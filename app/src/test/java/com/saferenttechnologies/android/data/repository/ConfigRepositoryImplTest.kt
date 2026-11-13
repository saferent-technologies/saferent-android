package com.saferenttechnologies.android.data.repository

import com.saferenttechnologies.android.core.log.NoOpLogger
import com.saferenttechnologies.android.data.local.CachedConfig
import com.saferenttechnologies.android.data.local.ConfigDataStore
import com.saferenttechnologies.android.data.remote.SafeApiCall
import com.saferenttechnologies.android.data.remote.SafeRentJson
import com.saferenttechnologies.android.domain.model.AppConfig
import com.saferenttechnologies.android.domain.model.AppError
import com.saferenttechnologies.android.domain.model.AuthSettings
import com.saferenttechnologies.android.domain.model.ConfigSyncResult
import com.saferenttechnologies.android.domain.model.Features
import com.saferenttechnologies.android.testutil.ApiResponses
import com.saferenttechnologies.android.testutil.FakeConfigApi
import com.saferenttechnologies.android.testutil.InMemoryPreferencesDataStore
import com.saferenttechnologies.android.testutil.TestConfig.REAL_DATA_JSON
import com.saferenttechnologies.android.testutil.TestConfig.REAL_ETAG
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException

@OptIn(ExperimentalCoroutinesApi::class)
class ConfigRepositoryImplTest {

    private lateinit var store: ConfigDataStore
    private lateinit var api: FakeConfigApi

    @Before
    fun setUp() {
        store = ConfigDataStore(InMemoryPreferencesDataStore())
        api = FakeConfigApi()
    }

    private fun TestScope.repository() = ConfigRepositoryImpl(
        api = api,
        safeApiCall = SafeApiCall(SafeRentJson.instance, NoOpLogger),
        store = store,
        json = SafeRentJson.instance,
        ioDispatcher = UnconfinedTestDispatcher(testScheduler),
        logger = NoOpLogger,
    )

    private suspend fun seedCache(): CachedConfig {
        store.save("""{"configVersion":2}""", "\"app-config-2\"", 2, 111L)
        return store.read()
    }

    // ------------------------------------------------------------- once per process

    @Test
    fun `first syncOnce saves json and etag, second syncOnce makes no request`() = runTest {
        val repo = repository()

        assertEquals(ConfigSyncResult.Updated(3), repo.syncOnce())
        assertEquals(listOf<String?>(null), api.requests) // empty cache → no If-None-Match

        val cached = store.read()
        assertEquals(REAL_ETAG, cached.etag)
        assertEquals(3, cached.version)
        assertNotNull(cached.savedAt)
        assertTrue(cached.json!!.contains("\"configVersion\":3"))
        assertFalse("only data is saved, not the envelope", cached.json!!.contains("\"success\""))

        assertEquals(ConfigSyncResult.AlreadySynced, repo.syncOnce())
        assertEquals(1, api.requestCount)
    }

    @Test
    fun `concurrent syncOnce calls make exactly one request`() = runTest {
        api.responder = {
            delay(100)
            ApiResponses.ok(REAL_DATA_JSON, REAL_ETAG)
        }
        val repo = repository()

        val results = List(10) { async { repo.syncOnce() } }.awaitAll()

        assertEquals(1, api.requestCount)
        assertEquals(1, results.count { it is ConfigSyncResult.Updated })
        assertEquals(9, results.count { it == ConfigSyncResult.AlreadySynced })
    }

    @Test
    fun `failed syncOnce still counts as this process's only sync`() = runTest {
        api.responder = { throw IOException("offline") }
        val repo = repository()

        assertTrue(repo.syncOnce() is ConfigSyncResult.Failed)
        assertEquals(ConfigSyncResult.AlreadySynced, repo.syncOnce())
        assertEquals(1, api.requestCount)
    }

    @Test
    fun `forceRefresh ignores the guard but still sends the saved etag`() = runTest {
        val repo = repository()
        repo.syncOnce()
        api.responder = { ApiResponses.notModified() }

        assertEquals(ConfigSyncResult.NotModified, repo.forceRefresh())
        assertEquals(listOf(null, REAL_ETAG), api.requests)
    }

    // ------------------------------------------------------------- 304 / errors

    @Test
    fun `304 returns NotModified, sends If-None-Match and keeps cache unchanged`() = runTest {
        val before = seedCache()
        api.responder = { ApiResponses.notModified() }

        assertEquals(ConfigSyncResult.NotModified, repository().syncOnce())
        assertEquals(listOf<String?>("\"app-config-2\""), api.requests)
        assertEquals(before, store.read())
    }

    @Test
    fun `server error returns Failed with envelope details and keeps cache`() = runTest {
        val repo = repository()
        repo.syncOnce()
        val before = store.read()
        api.responder = {
            ApiResponses.error(
                503,
                """{"success":false,"message":"Down for maintenance","errorCode":"SERVICE_UNAVAILABLE","reason":"deploy","details":null}""",
            )
        }

        val result = repo.forceRefresh()

        val error = (result as ConfigSyncResult.Failed).error as AppError.Server
        assertEquals(503, error.httpCode)
        assertEquals("SERVICE_UNAVAILABLE", error.errorCode)
        assertEquals("deploy", error.reason)
        assertEquals("Down for maintenance", error.message)
        assertEquals(before, store.read())
        assertEquals(3, repo.getConfig().configVersion)
    }

    @Test
    fun `error without a readable body still maps to Server error`() = runTest {
        api.responder = { ApiResponses.error(500, "<html>oops</html>") }

        val error = (repository().syncOnce() as ConfigSyncResult.Failed).error
        assertTrue(error is AppError.Server)
        assertEquals(500, (error as AppError.Server).httpCode)
    }

    @Test
    fun `offline returns Network failure, never throws and keeps cache`() = runTest {
        val before = seedCache()
        api.responder = { throw IOException("offline") }

        assertEquals(ConfigSyncResult.Failed(AppError.Network()), repository().syncOnce())
        assertEquals(before, store.read())
    }

    @Test
    fun `timeout returns Timeout failure`() = runTest {
        api.responder = { throw SocketTimeoutException("slow") }

        assertEquals(ConfigSyncResult.Failed(AppError.Timeout()), repository().syncOnce())
    }

    // ------------------------------------------------------------- cache reads

    @Test
    fun `empty cache returns defaults without any request`() = runTest {
        val repo = repository()

        assertEquals(AppConfig(), repo.getConfig())
        assertEquals(Features(), repo.getFeatures())
        assertEquals(0, repo.getConfigVersion())
        assertNull(repo.getLastSavedAt())
        assertTrue(repo.isFeatureEnabled("anything"))
        assertEquals(0, api.requestCount)
    }

    @Test
    fun `unreadable cached json returns defaults and keeps the raw string`() = runTest {
        store.save("not json {", "\"e\"", 1, 1L)

        assertEquals(AppConfig(), repository().getConfig())
        assertEquals("not json {", store.read().json)
    }

    @Test
    fun `getters return the saved values`() = runTest {
        val repo = repository()
        repo.syncOnce()

        assertEquals(3, repo.getConfigVersion())
        assertNotNull(repo.getLastSavedAt())
        assertEquals("SafeRent", repo.getApp().name)
        assertEquals("1.0.0", repo.getApp().minimumSupportedVersion)
        assertFalse(repo.getMaintenance().enabled)
        assertFalse(repo.isFeatureEnabled("chat"))
        assertTrue(repo.isFeatureEnabled("rentPayments"))
        assertEquals(60, repo.getAuth().otpResendSeconds)
        assertEquals(4, repo.getProperties().enabledTypes.size)
        assertEquals("PRIVATE", repo.getDocuments().defaultVisibility)
        assertTrue(repo.getNotifications().rentOverdue)
        assertEquals(100000.0, repo.getPayments().maxAmount!!, 0.0)
        assertEquals(3, repo.getReferrals().programs.size)
        assertEquals("support@saferenttechnologies.com", repo.getSupport().email)
        assertEquals(1500L, repo.getSplash().minDurationMs)
        assertEquals(3, repo.getOnboarding().screens.size)
        assertEquals("#F97316", repo.getUi().primaryColor)
        assertEquals("", repo.getLegal().termsUrl)
    }

    @Test
    fun `unknown and missing fields do not crash`() = runTest {
        api.responder = {
            ApiResponses.ok(
                """
                {
                  "configVersion": 7,
                  "brandNewSection": { "x": 1 },
                  "app": { "latestVersion": "2.0.0", "forceUpdate": null, "newField": "y" },
                  "features": { "chat": true, "videoTours": false },
                  "payments": { "minAmount": null }
                }
                """.trimIndent(),
                "\"app-config-7\"",
            )
        }
        val repo = repository()

        assertEquals(ConfigSyncResult.Updated(7), repo.syncOnce())
        val app = repo.getApp()
        assertEquals("2.0.0", app.latestVersion)
        assertFalse(app.forceUpdate) // null coerced to default
        assertEquals("SafeRent", app.name) // missing → default
        assertTrue(repo.isFeatureEnabled("chat"))
        assertFalse(repo.isFeatureEnabled("videoTours")) // new server flag respected
        assertTrue(repo.isFeatureEnabled("neverHeardOf")) // unknown → true
        assertEquals(AuthSettings(), repo.getAuth())
        assertNull(repo.getPayments().minAmount)
    }

    @Test
    fun `200 without ETag clears the old etag`() = runTest {
        seedCache()
        api.responder = { ApiResponses.ok(REAL_DATA_JSON, etag = null) }

        repository().syncOnce()
        assertNull(store.read().etag)
        assertEquals(3, store.read().version)
    }

    @Test
    fun `observeConfig emits cached value first then the synced config`() = runTest {
        val repo = repository()
        val emissions = mutableListOf<AppConfig>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            repo.observeConfig().toList(emissions)
        }

        repo.syncOnce()
        advanceUntilIdle()

        assertEquals(2, emissions.size)
        assertEquals(AppConfig(), emissions[0])
        assertEquals(3, emissions[1].configVersion)
    }
}
