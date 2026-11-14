package com.saferenttechnologies.android.data.push

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.saferenttechnologies.android.core.log.AppLogger
import com.saferenttechnologies.android.domain.usecase.FcmTokenSync
import com.saferenttechnologies.android.domain.usecase.SyncFcmTokenUseCase
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** PUT devices/fcm-token with network constraint + backoff, so a new token survives being offline. */
class FcmTokenWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Dependencies {
        fun syncFcmToken(): SyncFcmTokenUseCase
        fun logger(): AppLogger
    }

    override suspend fun doWork(): Result {
        val deps = EntryPointAccessors.fromApplication(applicationContext, Dependencies::class.java)
        val result = deps.syncFcmToken().invoke(inputData.getString(KEY_TOKEN))
        deps.logger().d(TAG, "FCM token sync attempt ${runAttemptCount + 1} → $result")
        return when (result) {
            FcmTokenSync.SENT, FcmTokenSync.UNCHANGED, FcmTokenSync.SIGNED_OUT, FcmTokenSync.REJECTED -> Result.success()
            FcmTokenSync.FAILED -> if (runAttemptCount + 1 < MAX_ATTEMPTS) Result.retry() else Result.failure()
        }
    }

    companion object {
        const val UNIQUE_NAME = "fcm-token-sync"
        const val KEY_TOKEN = "token"
        private const val TAG = "SafeRent-Push"
        private const val MAX_ATTEMPTS = 8
    }
}

@Singleton
class FcmTokenScheduler @Inject constructor(@ApplicationContext private val context: Context) {
    fun schedule(token: String) {
        val request = OneTimeWorkRequestBuilder<FcmTokenWorker>()
            .setInputData(workDataOf(FcmTokenWorker.KEY_TOKEN to token))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        // REPLACE: only the newest token matters.
        WorkManager.getInstance(context)
            .enqueueUniqueWork(FcmTokenWorker.UNIQUE_NAME, ExistingWorkPolicy.REPLACE, request)
    }
}
