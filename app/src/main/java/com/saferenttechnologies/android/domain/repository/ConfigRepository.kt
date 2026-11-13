package com.saferenttechnologies.android.domain.repository

import com.saferenttechnologies.android.domain.model.AppConfig
import com.saferenttechnologies.android.domain.model.AppInfo
import com.saferenttechnologies.android.domain.model.AuthSettings
import com.saferenttechnologies.android.domain.model.ConfigSyncResult
import com.saferenttechnologies.android.domain.model.DocumentSettings
import com.saferenttechnologies.android.domain.model.Features
import com.saferenttechnologies.android.domain.model.LegalSettings
import com.saferenttechnologies.android.domain.model.Maintenance
import com.saferenttechnologies.android.domain.model.NotificationSettings
import com.saferenttechnologies.android.domain.model.OnboardingSettings
import com.saferenttechnologies.android.domain.model.PaymentSettings
import com.saferenttechnologies.android.domain.model.PropertySettings
import com.saferenttechnologies.android.domain.model.ReferralSettings
import com.saferenttechnologies.android.domain.model.SplashSettings
import com.saferenttechnologies.android.domain.model.SupportSettings
import com.saferenttechnologies.android.domain.model.UiSettings
import kotlinx.coroutines.flow.Flow

/**
 * App configuration.
 *
 * Network: only [syncOnce] (at most one request per app process) and [forceRefresh].
 * Every other function reads the local cache — never the network. Empty cache → defaults.
 */
interface ConfigRepository {
    /** Fetch with If-None-Match, at most once per process. Never throws, never clears the cache. */
    suspend fun syncOnce(): ConfigSyncResult

    /** Same as [syncOnce] but ignores the once-per-process guard (still sends the ETag). */
    suspend fun forceRefresh(): ConfigSyncResult

    /**
     * Result of the latest real network attempt (syncOnce / forceRefresh) in this process;
     * null until the first attempt finishes. AlreadySynced is never emitted.
     */
    fun observeSyncResult(): Flow<ConfigSyncResult?>

    /** Emits the cached config (or defaults) immediately, then again whenever it changes. */
    fun observeConfig(): Flow<AppConfig>

    suspend fun getConfig(): AppConfig
    suspend fun getConfigVersion(): Int
    suspend fun getLastSavedAt(): Long?

    suspend fun getApp(): AppInfo
    suspend fun getMaintenance(): Maintenance
    suspend fun getFeatures(): Features

    /** Unknown key → true (the server enforces access). */
    suspend fun isFeatureEnabled(key: String): Boolean
    suspend fun getAuth(): AuthSettings
    suspend fun getProperties(): PropertySettings
    suspend fun getDocuments(): DocumentSettings
    suspend fun getNotifications(): NotificationSettings
    suspend fun getPayments(): PaymentSettings
    suspend fun getReferrals(): ReferralSettings
    suspend fun getSupport(): SupportSettings
    suspend fun getSplash(): SplashSettings
    suspend fun getOnboarding(): OnboardingSettings
    suspend fun getUi(): UiSettings
    suspend fun getLegal(): LegalSettings
}
