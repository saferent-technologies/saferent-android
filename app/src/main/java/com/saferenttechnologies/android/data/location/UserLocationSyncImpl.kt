package com.saferenttechnologies.android.data.location

import android.content.Context
import com.saferenttechnologies.android.core.log.AppLogger
import com.saferenttechnologies.android.data.remote.ApiResult
import com.saferenttechnologies.android.data.remote.SafeApiCall
import com.saferenttechnologies.android.data.remote.UserLocationApi
import com.saferenttechnologies.android.domain.model.SentArea
import com.saferenttechnologies.android.domain.model.UserLocationRules
import com.saferenttechnologies.android.domain.repository.LocationProvider
import com.saferenttechnologies.android.domain.repository.UserLocationSync
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

/** Never logs coordinates or area names — only whether it sent. */
@Singleton
class UserLocationSyncImpl @Inject constructor(
    @ApplicationContext context: Context,
    private val location: LocationProvider,
    private val api: UserLocationApi,
    private val safeApiCall: SafeApiCall,
    private val logger: AppLogger,
) : UserLocationSync {

    private val prefs = context.getSharedPreferences("user_area", Context.MODE_PRIVATE)

    override suspend fun sync(role: String?): Boolean {
        val now0 = System.currentTimeMillis()
        if (!UserLocationRules.shouldCheck(prefs.getLong(K_CHECK, -1L).takeIf { it >= 0 }, now0)) return false
        // currentFix() is null when location isn't allowed → nothing asked, nothing sent.
        val fix = location.currentFix() ?: return false
        prefs.edit().putLong(K_CHECK, now0).apply()
        val lat = UserLocationRules.coarse(fix.latitude)
        val lng = UserLocationRules.coarse(fix.longitude)
        val now = System.currentTimeMillis()
        val last = last()
        // Same point as last time → no Geocoder, no call (nothing new to store).
        if (UserLocationRules.samePoint(last, lat, lng)) return false
        val address = location.addressOf(fix.latitude, fix.longitude)
        if (!UserLocationRules.shouldSend(last, lat, lng, address?.locality.orEmpty(), address?.city.orEmpty())) return false
        val body = buildJsonObject {
            put("latitude", lat)
            put("longitude", lng)
            address?.locality?.takeIf { it.isNotBlank() }?.let { put("locality", it) }
            address?.city?.takeIf { it.isNotBlank() }?.let { put("city", it) }
            address?.state?.takeIf { it.isNotBlank() }?.let { put("state", it) }
            address?.pincode?.takeIf { it.isNotBlank() }?.let { put("pincode", it) }
            role?.let { put("role", it) }
            put("source", "DEVICE")
        }
        val ok = safeApiCall("PUT me/location") { api.put(body) } is ApiResult.Success
        if (ok) {
            prefs.edit().putFloat(K_LAT, lat.toFloat()).putFloat(K_LNG, lng.toFloat()).putLong(K_AT, now)
                .putString(K_LOCALITY, address?.locality.orEmpty()).putString(K_CITY, address?.city.orEmpty()).apply()
        }
        logger.d(TAG, "user area sync (sent=$ok)")
        return ok
    }

    override fun reset() {
        prefs.edit().clear().apply()
    }

    private fun last(): SentArea? =
        if (!prefs.contains(K_AT)) null else SentArea(
            prefs.getFloat(K_LAT, 0f).toDouble(),
            prefs.getFloat(K_LNG, 0f).toDouble(),
            prefs.getLong(K_AT, 0L),
            prefs.getString(K_LOCALITY, "").orEmpty(),
            prefs.getString(K_CITY, "").orEmpty(),
        )

    private companion object {
        const val TAG = "SR-UserArea"
        const val K_LAT = "lat"
        const val K_LNG = "lng"
        const val K_AT = "at"
        const val K_CHECK = "checked"
        const val K_LOCALITY = "locality"
        const val K_CITY = "city"
    }
}
