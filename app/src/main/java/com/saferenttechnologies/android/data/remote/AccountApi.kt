package com.saferenttechnologies.android.data.remote

import com.saferenttechnologies.android.data.remote.dto.ApiEnvelope
import com.saferenttechnologies.android.data.remote.dto.FcmTokenBody
import com.saferenttechnologies.android.data.remote.dto.FcmTokenResultDto
import com.saferenttechnologies.android.data.remote.dto.LogoutBody
import com.saferenttechnologies.android.data.remote.dto.PhoneCompleteDto
import com.saferenttechnologies.android.data.remote.dto.UserProfileResultDto
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT

/** Firebase ID token is added by AuthInterceptor. */
interface AccountApi {
    /** No FCM token available → POST with no body. */
    @POST("api/v1/auth/phone/complete")
    suspend fun completePhone(): Response<ApiEnvelope<PhoneCompleteDto>>

    @POST("api/v1/auth/phone/complete")
    suspend fun completePhone(@Body body: FcmTokenBody): Response<ApiEnvelope<PhoneCompleteDto>>

    /** data = { user: {...}, termsAccepted } (a bare user object is accepted too). */
    @GET("api/v1/auth/me")
    suspend fun me(): Response<ApiEnvelope<JsonElement>>

    /** JsonObject body so blank email / occupation / referral go as explicit null. */
    @POST("api/v1/userProfile")
    suspend fun saveUserProfile(@Body body: JsonObject): Response<ApiEnvelope<UserProfileResultDto>>

    @PUT("api/v1/devices/fcm-token")
    suspend fun registerFcmToken(@Body body: FcmTokenBody): Response<ApiEnvelope<FcmTokenResultDto>>

    @POST("api/v1/auth/logout")
    suspend fun logout(@Body body: LogoutBody): Response<ApiEnvelope<JsonElement>>
}
