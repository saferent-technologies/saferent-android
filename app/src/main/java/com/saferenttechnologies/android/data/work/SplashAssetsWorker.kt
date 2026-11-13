package com.saferenttechnologies.android.data.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.saferenttechnologies.android.core.log.AppLogger
import com.saferenttechnologies.android.domain.model.SplashAssetsResult
import com.saferenttechnologies.android.domain.repository.SplashAssetsScheduler
import com.saferenttechnologies.android.domain.usecase.UpdateSplashAssetsUseCase
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Downloads new splash assets in the background. WorkManager keeps it alive across
 * app background / process death and retries with backoff when offline or failing.
 */
class SplashAssetsWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Dependencies {
        fun updateSplashAssets(): UpdateSplashAssetsUseCase
        fun logger(): AppLogger
    }

    override suspend fun doWork(): Result {
        val deps = EntryPointAccessors.fromApplication(applicationContext, Dependencies::class.java)
        val result = deps.updateSplashAssets().invoke()
        deps.logger().d(TAG, "Worker attempt ${runAttemptCount + 1} → $result")
        return when (result) {
            is SplashAssetsResult.Updated, SplashAssetsResult.UpToDate -> Result.success()
            is SplashAssetsResult.Failed ->
                if (runAttemptCount + 1 < MAX_ATTEMPTS) Result.retry() else Result.failure()
        }
    }

    companion object {
        const val UNIQUE_NAME = "splash-assets-update"
        private const val TAG = "SafeRent-Splash"
        private const val MAX_ATTEMPTS = 5
    }
}

@Singleton
class WorkManagerSplashAssetsScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) : SplashAssetsScheduler {

    override fun scheduleUpdate() {
        val request = OneTimeWorkRequestBuilder<SplashAssetsWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        // KEEP: a run already queued will read the latest cached config anyway.
        WorkManager.getInstance(context)
            .enqueueUniqueWork(SplashAssetsWorker.UNIQUE_NAME, ExistingWorkPolicy.KEEP, request)
    }
}
