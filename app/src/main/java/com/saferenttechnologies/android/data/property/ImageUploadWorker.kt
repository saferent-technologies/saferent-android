package com.saferenttechnologies.android.data.property

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.saferenttechnologies.android.core.log.AppLogger
import com.saferenttechnologies.android.data.remote.ApiResult
import com.saferenttechnologies.android.data.remote.PropertyApi
import com.saferenttechnologies.android.data.remote.SafeApiCall
import com.saferenttechnologies.android.domain.model.AppError
import com.saferenttechnologies.android.domain.repository.PhotoUpload
import com.saferenttechnologies.android.domain.repository.PhotoUploadQueue
import com.saferenttechnologies.android.domain.repository.PhotoUploadSummary
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Uploads ONE captured photo (POST properties/{id}/images, multipart field "files").
 * Network required; retries with backoff; the temp file is deleted only after it's uploaded.
 */
class ImageUploadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Dependencies {
        fun propertyApi(): PropertyApi
        fun safeApiCall(): SafeApiCall
        fun logger(): AppLogger
    }

    override suspend fun doWork(): Result {
        val deps = EntryPointAccessors.fromApplication(applicationContext, Dependencies::class.java)
        val propertyId = inputData.getString(KEY_PROPERTY).orEmpty()
        val file = File(inputData.getString(KEY_PATH).orEmpty())
        if (propertyId.isBlank() || !file.exists()) return Result.failure(outputOf("Photo file is missing."))

        val slot = inputData.getInt(KEY_SLOT, -1).takeIf { it > 0 }
        val result = deps.safeApiCall()("POST images") {
            deps.propertyApi().uploadImages(
                id = propertyId,
                files = listOf(PropertyRepositoryImpl.jpegPart("files", file)),
                category = PropertyRepositoryImpl.textPart(inputData.getString(KEY_CATEGORY).orEmpty()),
                slot = slot?.let { PropertyRepositoryImpl.textPart(it.toString()) },
                unitId = inputData.getString(KEY_UNIT)?.let(PropertyRepositoryImpl::textPart),
                roomId = inputData.getString(KEY_ROOM)?.let(PropertyRepositoryImpl::textPart),
            )
        }
        deps.logger().d(TAG, "Photo upload attempt ${runAttemptCount + 1} → ${result::class.java.simpleName}")
        return when (result) {
            is ApiResult.Success, is ApiResult.NotModified -> {
                file.delete()
                Result.success()
            }
            is ApiResult.Failure -> {
                val e = result.error
                val retryable = e is AppError.Network || e is AppError.Timeout ||
                    (e is AppError.Server && (e.httpCode >= 500 || e.httpCode == 408 || e.httpCode == 429))
                if (retryable && runAttemptCount + 1 < MAX_ATTEMPTS) Result.retry() else Result.failure(outputOf(e.message))
            }
        }
    }

    private fun outputOf(message: String): Data = workDataOf(KEY_MESSAGE to message)

    companion object {
        const val KEY_PROPERTY = "propertyId"
        const val KEY_PATH = "path"
        const val KEY_CATEGORY = "category"
        const val KEY_SLOT = "slot"
        const val KEY_UNIT = "unitId"
        const val KEY_ROOM = "roomId"
        const val KEY_MESSAGE = "message"
        private const val TAG = "SR-AddProperty"
        private const val MAX_ATTEMPTS = 5

        fun tag(propertyId: String) = "property-images-$propertyId"
        fun pathTag(path: String) = "path:$path"
        fun metaTag(key: String, value: String) = "$key:$value"
    }
}

@Singleton
class WorkManagerPhotoUploadQueue @Inject constructor(
    @ApplicationContext private val context: Context,
) : PhotoUploadQueue {

    private val workManager get() = WorkManager.getInstance(context)

    /** Work ids the user already retried (their old FAILED entries are hidden). */
    private val retried = mutableSetOf<String>()

    override fun enqueue(propertyId: String, filePath: String, category: String, slot: Int?, unitId: String?, roomId: String?) {
        val request = OneTimeWorkRequestBuilder<ImageUploadWorker>()
            .setInputData(
                workDataOf(
                    ImageUploadWorker.KEY_PROPERTY to propertyId,
                    ImageUploadWorker.KEY_PATH to filePath,
                    ImageUploadWorker.KEY_CATEGORY to category,
                    ImageUploadWorker.KEY_SLOT to (slot ?: -1),
                    ImageUploadWorker.KEY_UNIT to unitId,
                    ImageUploadWorker.KEY_ROOM to roomId,
                ),
            )
            // Tags carry what we need to show / retry a failed upload (WorkInfo has no input data).
            .addTag(ImageUploadWorker.tag(propertyId))
            .addTag(ImageUploadWorker.pathTag(filePath))
            .addTag(ImageUploadWorker.metaTag("category", category))
            .apply {
                slot?.let { addTag(ImageUploadWorker.metaTag("slot", it.toString())) }
                unitId?.let { addTag(ImageUploadWorker.metaTag("unit", it)) }
                roomId?.let { addTag(ImageUploadWorker.metaTag("room", it)) }
            }
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 20, TimeUnit.SECONDS)
            .build()
        workManager.enqueue(request)
    }

    override fun observe(propertyId: String): Flow<PhotoUploadSummary> =
        workManager.getWorkInfosByTagFlow(ImageUploadWorker.tag(propertyId)).map { infos ->
            PhotoUploadSummary(
                running = infos.count { !it.state.isFinished },
                failed = infos.filter { it.state == WorkInfo.State.FAILED && it.id.toString() !in retried }
                    .mapNotNull { it.toUpload() }
                    .filter { File(it.filePath).exists() },
                succeeded = infos.count { it.state == WorkInfo.State.SUCCEEDED },
            )
        }

    override fun retry(propertyId: String, upload: PhotoUpload) {
        retried += upload.workId
        enqueue(propertyId, upload.filePath, upload.category, upload.slot, upload.unitId, upload.roomId)
    }

    private fun WorkInfo.toUpload(): PhotoUpload? {
        fun meta(key: String) = tags.firstOrNull { it.startsWith("$key:") }?.substringAfter(':')
        val path = meta("path") ?: return null
        return PhotoUpload(
            workId = id.toString(),
            filePath = path,
            category = meta("category").orEmpty(),
            slot = meta("slot")?.toIntOrNull(),
            unitId = meta("unit"),
            roomId = meta("room"),
            message = outputData.getString(ImageUploadWorker.KEY_MESSAGE),
        )
    }
}
