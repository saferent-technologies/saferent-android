package com.saferenttechnologies.android.data.homealerts

import com.saferenttechnologies.android.core.log.AppLogger
import com.saferenttechnologies.android.data.mapper.arrayAt
import com.saferenttechnologies.android.data.mapper.asText
import com.saferenttechnologies.android.data.mapper.text
import com.saferenttechnologies.android.data.remote.ApiResult
import com.saferenttechnologies.android.data.remote.HomeAlertsApi
import com.saferenttechnologies.android.data.remote.SafeApiCall
import com.saferenttechnologies.android.domain.model.AppError
import com.saferenttechnologies.android.domain.model.AppResult
import com.saferenttechnologies.android.domain.model.HomeAlert
import com.saferenttechnologies.android.domain.model.HomeAlertRules
import com.saferenttechnologies.android.domain.repository.HomeAlertsRepository
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

/** Never logs the coordinates or the area — only on/off. */
@Singleton
class HomeAlertsRepositoryImpl @Inject constructor(
    private val api: HomeAlertsApi,
    private val safeApiCall: SafeApiCall,
    private val logger: AppLogger,
) : HomeAlertsRepository {

    override suspend fun get(): AppResult<HomeAlert> = when (val r = safeApiCall("GET me/home-alert") { api.get() }) {
        is ApiResult.Success -> AppResult.Success(HomeAlertMapper.alert(r.data ?: JsonNull))
        is ApiResult.NotModified -> AppResult.Failure(AppError.Parse())
        // Not set up yet → off.
        is ApiResult.Failure -> if ((r.error as? AppError.Server)?.httpCode == 404) AppResult.Success(HomeAlert()) else AppResult.Failure(r.error)
    }

    override suspend fun save(alert: HomeAlert): AppResult<HomeAlert> {
        logger.d(TAG, "save home alert (enabled=${alert.enabled})")
        return when (val r = safeApiCall("PUT me/home-alert") { api.save(HomeAlertMapper.body(alert)) }) {
            is ApiResult.Success -> AppResult.Success((r.data as? JsonObject)?.let { HomeAlertMapper.alert(it) } ?: alert)
            is ApiResult.NotModified -> AppResult.Success(alert)
            is ApiResult.Failure -> AppResult.Failure(r.error)
        }
    }

    private companion object { const val TAG = "SR-HomeAlert" }
}

/** JSON ↔ HomeAlert (pure, unit-tested). */
object HomeAlertMapper {
    fun alert(data: JsonElement): HomeAlert {
        val o = (data as? JsonObject)?.let { (it["alert"] as? JsonObject) ?: it } ?: return HomeAlert()
        fun num(k: String) = (o[k] as? JsonPrimitive)?.content?.toDoubleOrNull()
        return HomeAlert(
            enabled = (o["enabled"] as? JsonPrimitive)?.let { it.booleanOrNull ?: it.content.equals("true", true) } ?: false,
            latitude = num("latitude"),
            longitude = num("longitude"),
            areaLabel = o.text("areaLabel"),
            locality = o.text("locality"),
            city = o.text("city"),
            radiusKm = num("radiusKm")?.toInt() ?: HomeAlertRules.DEFAULT_RADIUS,
            maxRent = num("maxRent")?.toInt(),
            propertyTypes = o.arrayAt("propertyTypes").mapNotNull { it.asText() }.toSet(),
        )
    }

    fun body(a: HomeAlert): JsonObject = buildJsonObject {
        put("enabled", a.enabled)
        a.latitude?.let { put("latitude", it) }
        a.longitude?.let { put("longitude", it) }
        if (a.areaLabel.isNotBlank()) put("areaLabel", a.areaLabel)
        if (a.locality.isNotBlank()) put("locality", a.locality)
        if (a.city.isNotBlank()) put("city", a.city)
        put("radiusKm", a.radiusKm)
        val maxRent: JsonElement = a.maxRent?.let { JsonPrimitive(it) } ?: JsonNull
        put("maxRent", maxRent)
        put("propertyTypes", JsonArray(a.propertyTypes.sorted().map { JsonPrimitive(it) }))
    }
}
