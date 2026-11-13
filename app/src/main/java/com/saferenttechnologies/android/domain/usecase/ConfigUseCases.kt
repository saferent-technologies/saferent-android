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
import kotlinx.coroutines.flow.Flow
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
