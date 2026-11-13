package com.saferenttechnologies.android.data.repository

import com.saferenttechnologies.android.core.di.IoDispatcher
import com.saferenttechnologies.android.data.local.ConfigDataStore
import com.saferenttechnologies.android.data.mapper.toAppConfig
import com.saferenttechnologies.android.data.remote.ConfigApi
import com.saferenttechnologies.android.data.remote.toAppError
import com.saferenttechnologies.android.data.remote.toServerError
import com.saferenttechnologies.android.domain.model.AppConfig
import com.saferenttechnologies.android.domain.model.AppError
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
import com.saferenttechnologies.android.domain.repository.ConfigRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

/**
 * Call policy:
 * 1. Every getter reads DataStore — never the network.
 * 2. [syncOnce] hits the network at most once per process (Mutex + [alreadySynced]);
 *    later / concurrent calls return [ConfigSyncResult.AlreadySynced] without a request.
 * 3. Always sends If-None-Match: 304 → keep cache, 200 → overwrite, error → keep cache.
 * 4. Never throws (except coroutine cancellation), never clears the cache.
 */
@Singleton
class ConfigRepositoryImpl @Inject constructor(
    private val api: ConfigApi,
    private val store: ConfigDataStore,
    private val json: Json,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ConfigRepository {

    private val syncMutex = Mutex()

    @Volatile
    private var alreadySynced = false

    // ---------------------------------------------------------------- network

    override suspend fun syncOnce(): ConfigSyncResult = syncMutex.withLock {
        if (alreadySynced) return@withLock ConfigSyncResult.AlreadySynced
        // Flag is set only when the attempt completes (success OR failure) — a cancelled
        // attempt leaves it false so a later call can still sync.
        fetchAndStore().also { alreadySynced = true }
    }

    override suspend fun forceRefresh(): ConfigSyncResult = syncMutex.withLock {
        fetchAndStore().also { alreadySynced = true }
    }

    private suspend fun fetchAndStore(): ConfigSyncResult {
        val savedEtag = store.read().etag

        val response = try {
            api.config(savedEtag)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return ConfigSyncResult.Failed(e.toAppError())
        }

        if (response.code() == HTTP_NOT_MODIFIED) return ConfigSyncResult.NotModified
        if (!response.isSuccessful) return ConfigSyncResult.Failed(response.toServerError(json))

        val envelope = response.body()
            ?: return ConfigSyncResult.Failed(AppError.Parse())
        if (!envelope.success) {
            return ConfigSyncResult.Failed(
                AppError.Server(
                    httpCode = response.code(),
                    message = envelope.message.ifBlank { AppError.Server(response.code()).message },
                ),
            )
        }
        val data = envelope.data ?: return ConfigSyncResult.Failed(AppError.Parse())

        val version = parseOrNull(data)?.configVersion ?: 0
        return try {
            store.save(
                json = data.toString(),
                etag = response.headers()[HEADER_ETAG]?.takeIf { it.isNotBlank() },
                version = version,
                savedAt = System.currentTimeMillis(),
            )
            ConfigSyncResult.Updated(version)
        } catch (e: IOException) {
            ConfigSyncResult.Failed(AppError.Storage())
        }
    }

    // ---------------------------------------------------------------- cache reads

    override fun observeConfig(): Flow<AppConfig> = store.cached
        .map { it.json }
        .distinctUntilChanged()
        .map { raw -> parseCached(raw) }
        .flowOn(ioDispatcher)

    override suspend fun getConfig(): AppConfig = observeConfig().first()
    override suspend fun getConfigVersion(): Int = store.read().version
    override suspend fun getLastSavedAt(): Long? = store.read().savedAt

    override suspend fun getApp(): AppInfo = getConfig().app
    override suspend fun getMaintenance(): Maintenance = getConfig().maintenance
    override suspend fun getFeatures(): Features = getConfig().features
    override suspend fun isFeatureEnabled(key: String): Boolean = getFeatures().isEnabled(key)
    override suspend fun getAuth(): AuthSettings = getConfig().auth
    override suspend fun getProperties(): PropertySettings = getConfig().properties
    override suspend fun getDocuments(): DocumentSettings = getConfig().documents
    override suspend fun getNotifications(): NotificationSettings = getConfig().notifications
    override suspend fun getPayments(): PaymentSettings = getConfig().payments
    override suspend fun getReferrals(): ReferralSettings = getConfig().referrals
    override suspend fun getSupport(): SupportSettings = getConfig().support
    override suspend fun getSplash(): SplashSettings = getConfig().splash
    override suspend fun getOnboarding(): OnboardingSettings = getConfig().onboarding
    override suspend fun getUi(): UiSettings = getConfig().ui
    override suspend fun getLegal(): LegalSettings = getConfig().legal

    // ---------------------------------------------------------------- parsing

    /** Empty or unreadable cache → defaults. The raw string is left untouched in DataStore. */
    private fun parseCached(raw: String?): AppConfig {
        if (raw.isNullOrBlank()) return AppConfig()
        return try {
            parseOrNull(json.parseToJsonElement(raw).jsonObject) ?: AppConfig()
        } catch (e: IllegalArgumentException) {
            AppConfig()
        }
    }

    private fun parseOrNull(data: JsonObject): AppConfig? = try {
        data.toAppConfig(json)
    } catch (e: IllegalArgumentException) {
        null
    }

    private companion object {
        const val HTTP_NOT_MODIFIED = 304
        const val HEADER_ETAG = "ETag"
    }
}
