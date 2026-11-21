package com.saferenttechnologies.android.data.remote

import com.saferenttechnologies.android.data.remote.dto.ApiEnvelope
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

/** Landlord owner verification: DigiLocker (Aadhaar) → live selfie → submit → staff review. */
interface KycApi {
    @GET("api/v1/kyc/me")
    suspend fun me(): Response<ApiEnvelope<JsonElement>>

    /** No body (and never X-Client-Platform: ADMIN_PANEL). */
    @POST("api/v1/kyc/digilocker/start")
    suspend fun startDigiLocker(): Response<ApiEnvelope<JsonElement>>

    /** { id, success?: "True" | "False", errMessage? } */
    @POST("api/v1/kyc/digilocker/complete")
    suspend fun completeDigiLocker(@Body body: JsonObject): Response<ApiEnvelope<JsonElement>>

    /**
     * Owner ID (instead of DigiLocker): type AADHAAR_XML (file = ZIP, secret = share code) |
     * EAADHAAR_PDF (file = PDF, secret = password) | PAN (number, front) | PASSPORT (number, front, back).
     */
    @Multipart
    @POST("api/v1/kyc/identity")
    suspend fun uploadIdentity(
        @Part("type") type: okhttp3.RequestBody,
        @Part("number") number: okhttp3.RequestBody?,
        @Part("secret") secret: okhttp3.RequestBody?,
        @Part file: MultipartBody.Part?,
        @Part front: MultipartBody.Part?,
        @Part back: MultipartBody.Part?,
    ): Response<ApiEnvelope<JsonElement>>

    /** Live selfie (JPEG, multipart "file"). Endpoint assumed: spec was cut off; confirm with backend. */
    @Multipart
    @POST("api/v1/kyc/selfie")
    suspend fun uploadSelfie(@Part file: MultipartBody.Part): Response<ApiEnvelope<JsonElement>>

    /** Send for staff review. Endpoint assumed: spec was cut off; confirm with backend. */
    @POST("api/v1/kyc/submit")
    suspend fun submit(@Body body: JsonObject): Response<ApiEnvelope<JsonElement>>

    /** How to verify guides (title, description, video / PDF / picture url). */
    @GET("api/v1/kyc/tutorials")
    suspend fun tutorials(): Response<ApiEnvelope<JsonElement>>

    /** The verification screen after sign in was finished or skipped → promptDone = true on kyc/me. */
    @POST("api/v1/kyc/prompt-done")
    suspend fun promptDone(@Body body: JsonObject): Response<ApiEnvelope<JsonElement>>
}
