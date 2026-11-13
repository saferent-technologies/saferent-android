package com.saferenttechnologies.android.domain.usecase

import com.saferenttechnologies.android.domain.model.AppConfig
import com.saferenttechnologies.android.domain.model.AuthSettings
import com.saferenttechnologies.android.domain.model.ConfigSyncResult
import com.saferenttechnologies.android.domain.model.Features
import com.saferenttechnologies.android.domain.model.LegalSettings
import com.saferenttechnologies.android.domain.model.Maintenance
import com.saferenttechnologies.android.domain.model.OnboardingSettings
import com.saferenttechnologies.android.domain.model.SplashSettings
import com.saferenttechnologies.android.domain.model.SupportSettings
import com.saferenttechnologies.android.domain.model.UiSettings
import com.saferenttechnologies.android.domain.repository.ConfigRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

/** Network sync, at most once per app process (ETag aware). */
class SyncAppConfigUseCase @Inject constructor(private val repository: ConfigRepository) {
    suspend operator fun invoke(): ConfigSyncResult = repository.syncOnce()
}

/** Manual refresh that ignores the once-per-process guard. For explicit user action only. */
class ForceRefreshAppConfigUseCase @Inject constructor(private val repository: ConfigRepository) {
    suspend operator fun invoke(): ConfigSyncResult = repository.forceRefresh()
}

/** Cached config (or defaults) immediately, then every change. No network. */
class ObserveAppConfigUseCase @Inject constructor(private val repository: ConfigRepository) {
    operator fun invoke(): Flow<AppConfig> = repository.observeConfig()
}

class GetAppConfigUseCase @Inject constructor(private val repository: ConfigRepository) {
    suspend operator fun invoke(): AppConfig = repository.getConfig()
}

class GetSplashConfigUseCase @Inject constructor(private val repository: ConfigRepository) {
    suspend operator fun invoke(): SplashSettings = repository.getSplash()
}

class GetOnboardingConfigUseCase @Inject constructor(private val repository: ConfigRepository) {
    suspend operator fun invoke(): OnboardingSettings = repository.getOnboarding()
}

class GetFeaturesUseCase @Inject constructor(private val repository: ConfigRepository) {
    suspend operator fun invoke(): Features = repository.getFeatures()
}

/** Unknown key → true (the server enforces access). */
class IsFeatureEnabledUseCase @Inject constructor(private val repository: ConfigRepository) {
    suspend operator fun invoke(key: String): Boolean = repository.isFeatureEnabled(key)
}

class GetMaintenanceUseCase @Inject constructor(private val repository: ConfigRepository) {
    suspend operator fun invoke(): Maintenance = repository.getMaintenance()
}

class GetAuthConfigUseCase @Inject constructor(private val repository: ConfigRepository) {
    suspend operator fun invoke(): AuthSettings = repository.getAuth()
}

class GetUiConfigUseCase @Inject constructor(private val repository: ConfigRepository) {
    suspend operator fun invoke(): UiSettings = repository.getUi()
}

class GetSupportConfigUseCase @Inject constructor(private val repository: ConfigRepository) {
    suspend operator fun invoke(): SupportSettings = repository.getSupport()
}

class GetLegalConfigUseCase @Inject constructor(private val repository: ConfigRepository) {
    suspend operator fun invoke(): LegalSettings = repository.getLegal()
}

/**
 * First launch (no config saved yet): suspends until the config API succeeds, so the app
 * starts with the server's splash / onboarding / features instead of defaults.
 *
 * - Config already saved (any later launch) → returns true immediately, no waiting.
 * - Waits for the startup sync; if it fails (offline, server down) it retries every
 *   [RETRY_DELAY_MS] until success.
 * - Gives up after [MAX_WAIT_MS] and returns false → the app continues with defaults,
 *   so a phone with no internet is never stuck on the splash forever.
 */
class AwaitInitialConfigUseCase @Inject constructor(private val repository: ConfigRepository) {

    suspend operator fun invoke(): Boolean {
        if (repository.getLastSavedAt() != null) return true
        return withTimeoutOrNull(MAX_WAIT_MS) {
            var result = repository.observeSyncResult().filterNotNull().first()
            while (!result.isSuccess()) {
                delay(RETRY_DELAY_MS)
                result = repository.forceRefresh()
            }
            true
        } ?: false
    }

    private fun ConfigSyncResult.isSuccess(): Boolean =
        this is ConfigSyncResult.Updated || this == ConfigSyncResult.NotModified

    companion object {
        const val MAX_WAIT_MS = 20_000L
        const val RETRY_DELAY_MS = 3_000L
    }
}
