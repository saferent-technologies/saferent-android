package com.saferenttechnologies.android.presentation.requests

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.core.content.FileProvider
import coil3.compose.AsyncImage
import com.saferenttechnologies.android.R
import com.saferenttechnologies.android.core.ui.sdp
import com.saferenttechnologies.android.core.ui.ssp
import com.saferenttechnologies.android.domain.model.AppPermission
import com.saferenttechnologies.android.presentation.auth.AuthColors
import com.saferenttechnologies.android.presentation.permission.rememberPermissionRequester
import com.saferenttechnologies.android.presentation.property.OutlineAction
import com.saferenttechnologies.android.ui.theme.Inter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * Request media: camera photo (system camera), short video (our recorder, 720p, ≤ 60 s) and
 * gallery (photos and videos, system picker: no permission). Camera permission goes through our popup first.
 * [receipts]: camera photo, gallery photos and PDF (no video).
 */
class PhotoPicker internal constructor(val camera: () -> Unit, val video: () -> Unit, val gallery: () -> Unit, val pdf: () -> Unit)

@Composable
fun rememberPhotoPicker(remaining: Int, onRecordVideo: (() -> Unit)? = null, receipts: Boolean = false, onPicked: (List<String>) -> Unit): PhotoPicker {
    val context = LocalContext.current
    val cameraPermission = rememberPermissionRequester(AppPermission.CAMERA)
    var pendingShot by rememberSaveable { mutableStateOf<String?>(null) }
    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val uri = pendingShot
        pendingShot = null
        if (ok && uri != null) onPicked(listOf(uri))
    }
    val pickMany = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(maxOf(2, remaining))) { uris ->
        onPicked(uris.take(remaining).map(Uri::toString))
    }
    val pickOne = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let { onPicked(listOf(it.toString())) } }
    val pickPdf = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris -> onPicked(uris.take(remaining).map(Uri::toString)) }
    return remember(remaining, receipts, onRecordVideo) {
        PhotoPicker(
            camera = {
                if (remaining > 0) cameraPermission.request { granted ->
                    if (granted) {
                        val uri = context.newShotUri()
                        pendingShot = uri.toString()
                        takePicture.launch(uri)
                    }
                }
            },
            video = {
                if (remaining > 0 && onRecordVideo != null) cameraPermission.request { granted -> if (granted) onRecordVideo() }
            },
            gallery = {
                val request = PickVisualMediaRequest(if (receipts) ActivityResultContracts.PickVisualMedia.ImageOnly else ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                when {
                    remaining <= 0 -> Unit
                    remaining == 1 -> pickOne.launch(request)
                    else -> pickMany.launch(request)
                }
            },
            pdf = { if (remaining > 0) runCatching { pickPdf.launch(arrayOf("application/pdf")) } },
        )
    }
}

/** An empty file in cache/request-photos for the camera to write into (shared via FileProvider). */
private fun Context.newShotUri(): Uri {
    val dir = File(cacheDir, "request-photos").apply { mkdirs() }
    val file = File(dir, "shot-${UUID.randomUUID()}.jpg")
    return FileProvider.getUriForFile(this, "$packageName.files", file)
}

/** "Photo" + "Video" + "Gallery" (receipts: "Camera" + "Gallery" + "PDF"); hidden at the limit. */
@Composable
fun AddPhotoButtons(picker: PhotoPicker, enabled: Boolean, modifier: Modifier = Modifier, video: Boolean = true, receipts: Boolean = false) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.sdp)) {
        OutlineAction(stringResource(if (receipts) R.string.req_camera else R.string.req_photo), R.drawable.ic_camera, enabled = enabled, modifier = Modifier.weight(1f), compact = true, onClick = picker.camera)
        if (video && !receipts) {
            OutlineAction(stringResource(R.string.req_video), R.drawable.ic_play, enabled = enabled, modifier = Modifier.weight(1f), compact = true, onClick = picker.video)
        }
        OutlineAction(stringResource(R.string.req_gallery), R.drawable.ic_add, enabled = enabled, modifier = Modifier.weight(1f), compact = true, onClick = picker.gallery)
        if (receipts) {
            OutlineAction(stringResource(R.string.req_pdf), R.drawable.ic_document, enabled = enabled, modifier = Modifier.weight(1f), compact = true, onClick = picker.pdf)
        }
    }
}

/** Local file / content uri that is a video (by type or ".mp4"). */
fun isLocalVideo(context: Context, model: String): Boolean {
    if (model.startsWith("http")) return false
    if (model.endsWith(".mp4", ignoreCase = true)) return true
    return runCatching { context.contentResolver.getType(Uri.parse(model)) }.getOrNull()?.startsWith("video/") == true
}

fun isLocalPdf(context: Context, model: String): Boolean {
    if (model.startsWith("http")) return false
    if (model.endsWith(".pdf", ignoreCase = true)) return true
    return runCatching { context.contentResolver.getType(Uri.parse(model)) }.getOrNull() == "application/pdf"
}

/**
 * Thumbnails (local uris or server urls); videos show a play mark (local ones: a frame), PDFs a
 * document tile. × removes when [onRemove] is given; tap → [onOpen]. [videos] = server urls that are videos.
 */
@Composable
fun PhotoStrip(models: List<String>, videos: Set<String> = emptySet(), onRemove: ((Int) -> Unit)? = null, onOpen: ((Int) -> Unit)? = null) {
    if (models.isEmpty()) return
    val context = LocalContext.current
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.sdp)) {
        itemsIndexed(models) { i, m ->
            val video = m in videos || remember(m) { isLocalVideo(context, m) }
            val pdf = !video && remember(m) { isLocalPdf(context, m) }
            Box(
                Modifier
                    .size(76.sdp)
                    .clip(RoundedCornerShape(10.sdp))
                    .background(if (video) Color.Black else AuthColors.Surface)
                    .then(if (onOpen != null) Modifier.clickable(role = Role.Button) { onOpen(i) } else Modifier),
            ) {
                when {
                    pdf -> Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(painterResource(R.drawable.ic_document), null, tint = AuthColors.OrangeText, modifier = Modifier.size(26.sdp))
                        Text("PDF", color = AuthColors.Ink, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 11.ssp)
                    }
                    video -> {
                        if (!m.startsWith("http")) LocalVideoFrame(m)
                        Box(
                            Modifier.align(Alignment.Center).size(30.sdp).clip(CircleShape).background(Color.Black.copy(alpha = 0.55f)),
                            contentAlignment = Alignment.Center,
                        ) { Icon(painterResource(R.drawable.ic_play), stringResource(R.string.req_play_video), tint = Color.White, modifier = Modifier.size(16.sdp)) }
                    }
                    else -> AsyncImage(model = m, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                }
                if (onRemove != null) {
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.sdp)
                            .size(22.sdp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.6f))
                            .clickable(role = Role.Button) { onRemove(i) },
                        contentAlignment = Alignment.Center,
                    ) { Icon(painterResource(R.drawable.ic_close), stringResource(R.string.add_property_delete), tint = Color.White, modifier = Modifier.size(14.sdp)) }
                }
            }
        }
    }
}

/** First frame of a picked / recorded video (on device only). */
@Composable
private fun LocalVideoFrame(model: String) {
    val context = LocalContext.current
    val frame by produceState<Bitmap?>(null, model) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val r = MediaMetadataRetriever()
                try {
                    val u = Uri.parse(model)
                    if (u.scheme == null || u.scheme == "file") r.setDataSource(u.path ?: model) else r.setDataSource(context, u)
                    // getScaledFrameAtTime is API 27+ (minSdk 24): take the frame, then shrink it.
                    r.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)?.let { f ->
                        val scale = 240f / maxOf(f.width, f.height)
                        if (scale < 1f) Bitmap.createScaledBitmap(f, (f.width * scale).toInt().coerceAtLeast(1), (f.height * scale).toInt().coerceAtLeast(1), true) else f
                    }
                } finally {
                    r.release()
                }
            }.getOrNull()
        }
    }
    frame?.let { Image(it.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
}
