package com.saferenttechnologies.android.data.remote

import com.saferenttechnologies.android.data.remote.dto.ApiEnvelope
import kotlinx.serialization.json.JsonElement
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

/** Look people up by SafeRent ID or phone (landlord side). */
interface PeopleApi {
    /** Invite form: is this person already on SafeRent? → masked preview. 404 = not on SafeRent. */
    @GET("api/v1/users/lookup")
    suspend fun lookup(@Query("phone") phone: String?, @Query("safeRentId") safeRentId: String?): Response<ApiEnvelope<JsonElement>>

    /** "Review tenant": earlier landlords' reviews + leases + rent record of a tenant. */
    @GET("api/v1/tenants/report")
    suspend fun tenantReport(@Query("phone") phone: String?, @Query("safeRentId") safeRentId: String?): Response<ApiEnvelope<JsonElement>>
}
