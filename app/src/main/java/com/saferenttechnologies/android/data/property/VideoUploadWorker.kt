package com.saferenttechnologies.android.data.property

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.saferenttechnologies.android.data.remote.ApiCache
import com.saferenttechnologies.android.data.remote.ApiResult
import com.saferenttechnologies.android.domain.model.AppError
import com.saferenttechnologies.android.domain.repository.VideoUploadQueue
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import java.io.File
import okio.buffer
import okio.source
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Uploads ONE recorded video tour (POST properties/{id}/videos, multipart "file"). Deletes the file when done. */
class VideoUploadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val deps = EntryPointAccessors.fromApplication(applicationContext, ImageUploadWorker.Dependencies::class.java)
        val propertyId = inputData.getString(KEY_PROPERTY).orEmpty()
        val file = File(inputData.getString(KEY_PATH).orEmpty())
        if (propertyId.isBlank() || !file.exists()) return Result.failure(workDataOf(KEY_MESSAGE to "Video file is missing. Record it again."))
        val result = deps.safeApiCall()("POST video") {
            deps.propertyApi().uploadVideo(
                id = propertyId,
                file = MultipartBody.Part.createFormData("file", file.name, ProgressBody(file, MP4) { pct -> setProgressAsync(workDataOf(KEY_PROGRESS to pct)) }),
                unitId = inputData.getString(KEY_UNIT)?.let(PropertyRepositoryImpl::textPart),
                roomId = inputData.getString(KEY_ROOM)?.let(PropertyRepositoryImpl::textPart),
                durationSec = PropertyRepositoryImpl.textPart(inputData.getInt(KEY_DURATION, 0).toString()),
            )
        }
        deps.logger().d(TAG, "Video upload attempt ${runAttemptCount + 1} → ${result::class.java.simpleName}")
        return when (result) {
            is ApiResult.Success, is ApiResult.NotModified -> {
                file.delete()
                ApiCache.clear()
                Result.success()
            }
            is ApiResult.Failure -> {
                val e = result.error
                val retryable = e is AppError.Network || e is AppError.Timeout ||
                    (e is AppError.Server && (e.httpCode >= 500 || e.httpCode == 408 || e.httpCode == 429))
                // Failed for good: keep the file so the landlord can tap Retry (no need to record again).
                val message = when {
                    e is AppError.Server && e.httpCode == 413 -> "Video must be under 40 MB."
                    e is AppError.Conflict || (e is AppError.Server && e.httpCode == 409) -> "This place already has a video. Delete it first, then upload again."
                    else -> e.message
                }
                if (retryable && runAttemptCount + 1 < MAX_ATTEMPTS) Result.retry() else Result.failure(workDataOf(KEY_MESSAGE to message))
            }
        }
    }

    companion object {
        const val KEY_PROPERTY = "propertyId"
        const val KEY_PATH = "path"
        const val KEY_UNIT = "unitId"
        const val KEY_ROOM = "roomId"
        const val KEY_DURATION = "durationSec"
        const val KEY_MESSAGE = "message"
        const val KEY_PROGRESS = "progress"
        private const val TAG = "SR-AddProperty"
        private const val MAX_ATTEMPTS = 5
        private val MP4 = "video/mp4".toMediaType()
        fun tag(propertyId: String) = "property-videos-$propertyId"
    }
}

@Singleton
class WorkManagerVideoUploadQueue @Inject constructor(@ApplicationContext private val context: Context) : VideoUploadQueue {
    private val workManager get() = WorkManager.getInstance(context)

    override fun enqueue(propertyId: String, filePath: String, durationSec: Int, unitId: String?, roomId: String?) {
        val request = OneTimeWorkRequestBuilder<VideoUploadWorker>()
            .setInputData(
                workDataOf(
                    VideoUploadWorker.KEY_PROPERTY to propertyId,
                    VideoUploadWorker.KEY_PATH to filePath,
                    VideoUploadWorker.KEY_UNIT to unitId,
                    VideoUploadWorker.KEY_ROOM to roomId,
                    VideoUploadWorker.KEY_DURATION to durationSec,
                ),
            )
            .addTag(VideoUploadWorker.tag(propertyId))
            // Tags carry what the wizard shows / retries (WorkInfo has no input data).
            .addTag("path:$filePath")
            .addTag("dur:$durationSec")
            .apply {
                unitId?.let { addTag("unit:$it") }
                roomId?.let { addTag("room:$it") }
            }
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        workManager.enqueue(request)
    }

    override fun running(propertyId: String): Flow<Int> =
        workManager.getWorkInfosByTagFlow(VideoUploadWorker.tag(propertyId)).map { infos -> infos.count { !it.state.isFinished } }

    private val retried = mutableSetOf<String>()

    override fun observe(propertyId: String): Flow<List<com.saferenttechnologies.android.domain.repository.VideoUpload>> =
        workManager.getWorkInfosByTagFlow(VideoUploadWorker.tag(propertyId)).map { infos ->
            infos.filter { it.id.toString() !in retried && it.state != androidx.work.WorkInfo.State.CANCELLED }.mapNotNull { info ->
                fun meta(key: String) = info.tags.firstOrNull { it.startsWith("$key:") }?.substringAfter(':')
                val path = meta("path") ?: return@mapNotNull null
                com.saferenttechnologies.android.domain.repository.VideoUpload(
                    workId = info.id.toString(),
                    filePath = path,
                    unitId = meta("unit"),
                    roomId = meta("room"),
                    durationSec = meta("dur")?.toIntOrNull() ?: 0,
                    state = when (info.state) {
                        androidx.work.WorkInfo.State.SUCCEEDED -> com.saferenttechnologies.android.domain.repository.VideoUploadState.UPLOADED
                        androidx.work.WorkInfo.State.FAILED -> com.saferenttechnologies.android.domain.repository.VideoUploadState.FAILED
                        else -> com.saferenttechnologies.android.domain.repository.VideoUploadState.UPLOADING
                    },
                    message = info.outputData.getString(VideoUploadWorker.KEY_MESSAGE),
                    progress = info.progress.getInt(VideoUploadWorker.KEY_PROGRESS, -1),
                )
            }
        }

    override fun retry(propertyId: String, upload: com.saferenttechnologies.android.domain.repository.VideoUpload) {
        retried += upload.workId
        enqueue(propertyId, upload.filePath, upload.durationSec, upload.unitId, upload.roomId)
    }
}

/** The video file, reporting 0–100 % as it is written to the network (only when the percent changes). */
private class ProgressBody(
    private val file: File,
    private val type: okhttp3.MediaType,
    private val onProgress: (Int) -> Unit,
) : okhttp3.RequestBody() {
    override fun contentType() = type
    override fun contentLength() = file.length()
    override fun writeTo(sink: okio.BufferedSink) {
        val total = contentLength().coerceAtLeast(1)
        var sent = 0L
        var last = -1
        file.source().buffer().use { source ->
            val buffer = okio.Buffer()
            while (true) {
                val read = source.read(buffer, 64 * 1024L)
                if (read == -1L) break
                sink.write(buffer, read)
                sent += read
                val pct = (sent * 100 / total).toInt()
                if (pct != last) { last = pct; onProgress(pct) }
            }
        }
    }
}
