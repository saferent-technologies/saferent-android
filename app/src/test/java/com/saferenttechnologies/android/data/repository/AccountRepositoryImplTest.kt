package com.saferenttechnologies.android.data.repository

import com.saferenttechnologies.android.core.log.NoOpLogger
import com.saferenttechnologies.android.data.remote.AccountApi
import com.saferenttechnologies.android.data.remote.SafeApiCall
import com.saferenttechnologies.android.data.remote.SafeRentJson
import com.saferenttechnologies.android.data.remote.dto.ApiEnvelope
import com.saferenttechnologies.android.data.remote.dto.FcmTokenBody
import com.saferenttechnologies.android.data.remote.dto.FcmTokenResultDto
import com.saferenttechnologies.android.data.remote.dto.LogoutBody
import com.saferenttechnologies.android.data.remote.dto.PhoneCompleteDto
import com.saferenttechnologies.android.data.remote.dto.UserProfileResultDto
import com.saferenttechnologies.android.domain.model.AppResult
import com.saferenttechnologies.android.testutil.InMemoryPreferencesDataStore
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

class AccountRepositoryImplTest {

    private class FakeAccountApi : AccountApi {
        /** "no-body" or the FcmTokenBody sent to phone/complete. */
        val completeBodies = mutableListOf<Any>()
        var logoutBody: LogoutBody? = null
        var fcmBody: FcmTokenBody? = null

        private fun ok() = Response.success(ApiEnvelope(success = true, data = PhoneCompleteDto(fcmTokenSaved = true)))

        override suspend fun completePhone(): Response<ApiEnvelope<PhoneCompleteDto>> {
            completeBodies += "no-body"
            return ok()
        }

        override suspend fun completePhone(body: FcmTokenBody): Response<ApiEnvelope<PhoneCompleteDto>> {
            completeBodies += body
            return ok()
        }

        override suspend fun me(): Response<ApiEnvelope<JsonElement>> = Response.success(ApiEnvelope(success = true))

        override suspend fun saveUserProfile(body: JsonObject): Response<ApiEnvelope<UserProfileResultDto>> =
            Response.success(ApiEnvelope(success = true, data = UserProfileResultDto()))

        override suspend fun registerFcmToken(body: FcmTokenBody): Response<ApiEnvelope<FcmTokenResultDto>> {
            fcmBody = body
            return Response.success(ApiEnvelope(success = true, data = FcmTokenResultDto(ok = true, fcmTokenSaved = true)))
        }

        override suspend fun logout(body: LogoutBody): Response<ApiEnvelope<JsonElement>> {
            logoutBody = body
            return Response.success(ApiEnvelope(success = true, data = JsonNull))
        }
    }

    private val api = FakeAccountApi()
    private val repository = AccountRepositoryImpl(
        api = api,
        safeApiCall = SafeApiCall(SafeRentJson.instance, NoOpLogger),
        json = SafeRentJson.instance,
        dataStore = InMemoryPreferencesDataStore(),
        appVersion = "1.0",
    )

    @Test
    fun `phone complete sends fcmToken, platform and appVersion when a token exists`() = runTest {
        val r = repository.completePhoneSignIn("tok-123") as AppResult.Success
        assertEquals(listOf<Any>(FcmTokenBody("tok-123", platform = "ANDROID", appVersion = "1.0")), api.completeBodies)
        assertTrue(r.data.fcmTokenSaved)
    }

    @Test
    fun `phone complete sends no body when there is no token`() = runTest {
        assertTrue(repository.completePhoneSignIn(null) is AppResult.Success)
        assertEquals(listOf<Any>("no-body"), api.completeBodies)
    }

    @Test
    fun `fcm token and logout bodies`() = runTest {
        assertEquals(AppResult.Success(true), repository.registerFcmToken("tok-9"))
        assertEquals(FcmTokenBody("tok-9", "ANDROID", "1.0"), api.fcmBody)

        repository.logout("tok-9")
        assertEquals("tok-9", api.logoutBody?.fcmToken)
        repository.logout(null)
        assertNull(api.logoutBody?.fcmToken)
    }
}
