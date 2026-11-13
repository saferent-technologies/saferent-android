package com.saferenttechnologies.android.data.repository

import com.saferenttechnologies.android.core.di.IoDispatcher
import com.saferenttechnologies.android.core.log.AppLogger
import com.saferenttechnologies.android.data.local.ConfigDataStore
import com.saferenttechnologies.android.data.mapper.toAppConfig
import com.saferenttechnologies.android.data.remote.ApiResult
import com.saferenttechnologies.android.data.remote.ConfigApi
import com.saferenttechnologies.android.data.remote.SafeApiCall
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

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
    private val safeApiCall: SafeApiCall,
    private val store: ConfigDataStore,
    private val json: Json,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val logger: AppLogger,
) : ConfigRepository {

    private val syncMutex = Mutex()

    @Volatile
    private var alreadySynced = false

    private val lastSyncResult = MutableStateFlow<ConfigSyncResult?>(null)

    override fun observeSyncResult(): Flow<ConfigSyncResult?> = lastSyncResult.asStateFlow()

    // ---------------------------------------------------------------- network

    override suspend fun syncOnce(): ConfigSyncResult = syncMutex.withLock {
        if (alreadySynced) {
            logger.d(TAG, "syncOnce → AlreadySynced (no request)")
            return@withLock ConfigSyncResult.AlreadySynced
        }
        // Flag is set only when the attempt completes (success OR failure) — a cancelled
        // attempt leaves it false so a later call can still sync.
        fetchAndStore().also {
            alreadySynced = true
            lastSyncResult.value = it
            logResult("syncOnce", it)
        }
    }

    override suspend fun forceRefresh(): ConfigSyncResult = syncMutex.withLock {
        fetchAndStore().also {
            alreadySynced = true
            lastSyncResult.value = it
            logResult("forceRefresh", it)
        }
    }

    /** Debug only: which call happened and everything that is saved in DataStore now. */
    private suspend fun logResult(call: String, result: ConfigSyncResult) {
        if (!logger.isEnabled) return
        when (result) {
            is ConfigSyncResult.Failed -> logger.w(TAG, "$call → Failed: ${result.error} (cache kept)")
            else -> logger.d(TAG, "$call → $result")
        }
        logSavedConfig()
    }

    private suspend fun logSavedConfig() {
        val cached = store.read()
        val raw = cached.json
        if (raw.isNullOrBlank()) {
            logger.d(TAG, "Saved config: <empty cache → defaults>")
            return
        }
        val pretty = try {
            prettyJson.encodeToString(JsonObject.serializer(), json.parseToJsonElement(raw).jsonObject)
        } catch (e: IllegalArgumentException) {
            raw
        }
        logger.d(
            TAG,
            "Saved config | version=${cached.version} etag=${cached.etag} savedAt=${cached.savedAt}\n$pretty",
        )
    }

    private suspend fun fetchAndStore(): ConfigSyncResult {
        val savedEtag = store.read().etag
        logger.d(TAG, "→ GET api/v1/app/config (If-None-Match=${savedEtag ?: "<none>"})")

        val result = safeApiCall("GET app/config") { api.config(savedEtag) }
        return when (result) {
            is ApiResult.NotModified -> ConfigSyncResult.NotModified
            is ApiResult.Failure -> ConfigSyncResult.Failed(result.error)
            is ApiResult.Success -> save(result)
        }
    }

    private suspend fun save(result: ApiResult.Success<JsonObject>): ConfigSyncResult {
        val data = result.data
        if (data == null) {
            logger.w(TAG, "200 OK but \"data\" is null — cache kept")
            return ConfigSyncResult.Failed(AppError.Parse())
        }
        val version = parseOrNull(data)?.configVersion ?: 0
        return try {
            store.save(
                json = data.toString(),
                etag = result.headers[HEADER_ETAG]?.takeIf { it.isNotBlank() },
                version = version,
                savedAt = System.currentTimeMillis(),
            )
            ConfigSyncResult.Updated(version)
        } catch (e: IOException) {
            logger.w(TAG, "Couldn't save config to DataStore — cache kept", e)
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
        const val TAG = "SafeRent-Config"

        @OptIn(ExperimentalSerializationApi::class)
        val prettyJson = Json {
            prettyPrint = true
            prettyPrintIndent = "  "
        }

        const val HEADER_ETAG = "ETag"
    }
}
