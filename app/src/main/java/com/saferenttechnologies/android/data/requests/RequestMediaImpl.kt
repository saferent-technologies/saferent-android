package com.saferenttechnologies.android.data.requests

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import com.saferenttechnologies.android.core.log.AppLogger
import com.saferenttechnologies.android.domain.model.RequestRules
import com.saferenttechnologies.android.domain.repository.ProofImages
import com.saferenttechnologies.android.domain.repository.RequestMedia
import com.saferenttechnologies.android.domain.repository.UploadFile
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.coroutineScope
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Request attachments: photos → JPEG (≤ 1600 px), PDFs as they are (≤ 10 MB), videos → MP4.
 * A video taller than 720p (short side) or bigger than 40 MB is re-encoded to 720p H.264 on the
 * phone first (Media3 Transformer). Nothing about the files is logged except counts.
 */
@Singleton
class RequestMediaImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val images: ProofImages,
    private val logger: AppLogger,
) : RequestMedia {

    private fun type(uri: String): String {
        val u = Uri.parse(uri)
        val t = runCatching { context.contentResolver.getType(u) }.getOrNull()
        if (!t.isNullOrBlank()) return t.lowercase()
        val p = (u.path ?: uri).lowercase()
        return when {
            p.endsWith(".mp4") -> "video/mp4"
            p.endsWith(".pdf") -> "application/pdf"
            else -> "image/jpeg"
        }
    }

    override fun isVideo(uri: String) = type(uri).startsWith("video/")
    override fun isPdf(uri: String) = type(uri) == "application/pdf"

    override suspend fun prepare(uris: List<String>, onProgress: (Int) -> Unit): RequestMedia.Result {
        val videos = uris.count { isVideo(it) }
        if (RequestRules.tooManyVideos(videos)) return RequestMedia.Result.Failed(RequestMedia.Problem.TOO_MANY_VIDEOS)
        val out = mutableListOf<UploadFile>()
        var videoIndex = 0
        for (uri in uris) {
            val file = when {
                isVideo(uri) -> {
                    val base = videoIndex * 100 / videos.coerceAtLeast(1)
                    videoIndex++
                    when (val v = video(uri) { pct -> onProgress(base + pct / videos.coerceAtLeast(1)) }) {
                        is UploadFile -> v
                        is RequestMedia.Problem -> { cleanUp(out); return RequestMedia.Result.Failed(v) }
                        else -> null
                    }
                }
                isPdf(uri) -> withContext(Dispatchers.IO) {
                    if (size(uri) > RequestRules.FILE_MAX_BYTES) return@withContext null
                    runCatching { context.contentResolver.openInputStream(Uri.parse(uri))?.use { it.readBytes() } }.getOrNull()?.let { UploadFile.Pdf(it) }
                } ?: run {
                    cleanUp(out)
                    return RequestMedia.Result.Failed(if (size(uri) > RequestRules.FILE_MAX_BYTES) RequestMedia.Problem.FILE_TOO_BIG else RequestMedia.Problem.UNREADABLE)
                }
                else -> withContext(Dispatchers.IO) { images.readJpeg(uri) }?.let { UploadFile.Jpeg(it) }
            }
            if (file == null) { cleanUp(out); return RequestMedia.Result.Failed(RequestMedia.Problem.UNREADABLE) }
            out += file
        }
        logger.d(TAG, "prepared ${out.size} files ($videos videos)")
        return RequestMedia.Result.Ok(out)
    }

    override fun cleanUp(files: List<UploadFile>) {
        files.filterIsInstance<UploadFile.Mp4>().forEach { f -> File(f.path).takeIf { it.path.startsWith(context.cacheDir.path) }?.delete() }
    }

    private fun size(uri: String): Long {
        val u = Uri.parse(uri)
        if (u.scheme == null || u.scheme == "file") return File(u.path ?: uri).length()
        return runCatching {
            context.contentResolver.query(u, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c ->
                if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else null
            }
        }.getOrNull() ?: runCatching { context.contentResolver.openAssetFileDescriptor(u, "r")?.use { it.length } }.getOrNull() ?: -1L
    }

    /** Short side of the picture (after rotation) or null when it isn't a readable video. */
    private fun shortSide(uri: String): Int? = runCatching {
        val r = MediaMetadataRetriever()
        try {
            val u = Uri.parse(uri)
            if (u.scheme == null || u.scheme == "file") r.setDataSource(u.path ?: uri) else r.setDataSource(context, u)
            if (r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO) != "yes") return@runCatching null
            val w = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: return@runCatching null
            val h = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: return@runCatching null
            minOf(w, h)
        } finally {
            r.release()
        }
    }.getOrNull()

    /** UploadFile.Mp4, or a Problem. */
    private suspend fun video(uri: String, onProgress: (Int) -> Unit): Any {
        val short = withContext(Dispatchers.IO) { shortSide(uri) } ?: return RequestMedia.Problem.NOT_A_VIDEO
        val bytes = withContext(Dispatchers.IO) { size(uri) }
        val u = Uri.parse(uri)
        val local = u.scheme == null || u.scheme == "file"
        if (short <= SHORT_SIDE && bytes in 1..RequestRules.VIDEO_MAX_BYTES) {
            // Small enough: our own recording is sent as it is; a gallery video is copied to cache.
            if (local) return UploadFile.Mp4(u.path ?: uri)
            val copy = withContext(Dispatchers.IO) {
                runCatching {
                    val f = File(dir(), "${UUID.randomUUID()}.mp4")
                    context.contentResolver.openInputStream(u)?.use { input -> f.outputStream().use { input.copyTo(it) } } ?: error("no stream")
                    f
                }.getOrNull()
            } ?: return RequestMedia.Problem.UNREADABLE
            onProgress(100)
            return UploadFile.Mp4(copy.path)
        }
        val out = File(dir(), "${UUID.randomUUID()}.mp4")
        val ok = shrink(u, out, onProgress)
        if (!ok) { out.delete(); return RequestMedia.Problem.UNREADABLE }
        if (out.length() > RequestRules.VIDEO_MAX_BYTES) { out.delete(); return RequestMedia.Problem.VIDEO_TOO_BIG }
        return UploadFile.Mp4(out.path)
    }

    private fun dir() = File(context.cacheDir, "request-videos").apply { mkdirs() }

    /** 720p H.264 (Transformer runs on the main thread; progress is polled). */
    @OptIn(UnstableApi::class)
    private suspend fun shrink(input: Uri, out: File, onProgress: (Int) -> Unit): Boolean = withContext(Dispatchers.Main) {
        coroutineScope {
            var transformer: Transformer? = null
            val poll = launch {
                val holder = ProgressHolder()
                while (true) {
                    delay(300)
                    val t = transformer ?: continue
                    if (t.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) onProgress(holder.progress.coerceIn(0, 99))
                }
            }
            val ok = suspendCancellableCoroutine { cont ->
                val t = Transformer.Builder(context)
                    .setVideoMimeType(MimeTypes.VIDEO_H264)
                    .addListener(object : Transformer.Listener {
                        override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                            if (cont.isActive) cont.resume(true)
                        }

                        override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                            logger.d(TAG, "video shrink failed (code=${exportException.errorCode})")
                            if (cont.isActive) cont.resume(false)
                        }
                    })
                    .build()
                transformer = t
                val item = EditedMediaItem.Builder(MediaItem.fromUri(input))
                    .setEffects(Effects(emptyList(), listOf(Presentation.createForShortSide(SHORT_SIDE))))
                    .build()
                runCatching { t.start(item, out.absolutePath) }.onFailure { if (cont.isActive) cont.resume(false) }
                cont.invokeOnCancellation { runCatching { t.cancel() } }
            }
            poll.cancel()
            if (ok) onProgress(100)
            ok
        }
    }

    private companion object {
        const val TAG = "SR-RequestMedia"
        const val SHORT_SIDE = 720
    }
}
