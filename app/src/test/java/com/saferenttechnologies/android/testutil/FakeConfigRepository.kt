package com.saferenttechnologies.android.testutil

import com.saferenttechnologies.android.domain.model.AppConfig
import com.saferenttechnologies.android.domain.model.ConfigSyncResult
import com.saferenttechnologies.android.domain.repository.ConfigRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** Repository fake for ViewModel tests. [config] stands in for DataStore. */
class FakeConfigRepository : ConfigRepository {
    val config = MutableStateFlow(AppConfig())

    var syncResult: ConfigSyncResult = ConfigSyncResult.Updated(1)

    /** Config "saved" by a sync (applied before the sync returns). */
    var configAfterSync: AppConfig? = null

    /** When set, sync suspends until it completes — lets tests observe isSyncing. */
    var gate: CompletableDeferred<Unit>? = null

    /** Latest sync result, like the real repository. */
    val syncResults = MutableStateFlow<ConfigSyncResult?>(null)

    /** Non-null = config already cached (any launch after the first). */
    var lastSavedAt: Long? = 1L

    var syncCalls = 0
        private set
    var forceCalls = 0
        private set

    override suspend fun syncOnce(): ConfigSyncResult {
        syncCalls++
        return finishSync()
    }

    override suspend fun forceRefresh(): ConfigSyncResult {
        forceCalls++
        return finishSync()
    }

    private suspend fun finishSync(): ConfigSyncResult {
        gate?.await()
        configAfterSync?.let { config.value = it }
        syncResults.value = syncResult
        return syncResult
    }

    override fun observeSyncResult(): Flow<ConfigSyncResult?> = syncResults
    override fun observeConfig(): Flow<AppConfig> = config
    override suspend fun getConfig() = config.value
    override suspend fun getConfigVersion() = config.value.configVersion
    override suspend fun getLastSavedAt(): Long? = lastSavedAt
    override suspend fun getApp() = config.value.app
    override suspend fun getMaintenance() = config.value.maintenance
    override suspend fun getFeatures() = config.value.features
    override suspend fun isFeatureEnabled(key: String) = config.value.features.isEnabled(key)
    override suspend fun getAuth() = config.value.auth
    override suspend fun getProperties() = config.value.properties
    override suspend fun getDocuments() = config.value.documents
    override suspend fun getNotifications() = config.value.notifications
    override suspend fun getPayments() = config.value.payments
    override suspend fun getReferrals() = config.value.referrals
    override suspend fun getSupport() = config.value.support
    override suspend fun getSplash() = config.value.splash
    override suspend fun getOnboarding() = config.value.onboarding
    override suspend fun getUi() = config.value.ui
    override suspend fun getLegal() = config.value.legal
}
