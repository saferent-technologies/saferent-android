package com.saferenttechnologies.android.data.safety

import com.saferenttechnologies.android.data.mapper.arrayAt
import com.saferenttechnologies.android.data.mapper.text
import com.saferenttechnologies.android.data.owner.OwnerMapper
import com.saferenttechnologies.android.data.remote.ApiCache
import com.saferenttechnologies.android.data.remote.ApiResult
import com.saferenttechnologies.android.data.remote.SafeApiCall
import com.saferenttechnologies.android.data.remote.SafeRentJson
import com.saferenttechnologies.android.data.remote.SafetyApi
import com.saferenttechnologies.android.data.remote.dto.ApiEnvelope
import com.saferenttechnologies.android.domain.model.AppError
import com.saferenttechnologies.android.domain.model.AppResult
import com.saferenttechnologies.android.domain.model.EmergencyContact
import com.saferenttechnologies.android.domain.model.EmergencyInfo
import com.saferenttechnologies.android.domain.model.Helpline
import com.saferenttechnologies.android.domain.model.ListingSafety
import com.saferenttechnologies.android.domain.model.Safety
import com.saferenttechnologies.android.domain.model.SafetyCertificate
import com.saferenttechnologies.android.domain.model.SafetyData
import com.saferenttechnologies.android.domain.model.SafetyPlace
import com.saferenttechnologies.android.domain.model.SafetyRules
import com.saferenttechnologies.android.domain.repository.SafetyRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import retrofit2.Response
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SafetyRepositoryImpl @Inject constructor(
    private val api: SafetyApi,
    private val safeApiCall: SafeApiCall,
    private val cache: EmergencyCache,
) : SafetyRepository {

    override suspend fun safety(propertyId: String): AppResult<SafetyData> =
        call("GET safety", { api.safety(propertyId) }) { d, _ -> SafetyMapper.data(d) }

    override suspend fun saveSafety(propertyId: String, safety: Safety): AppResult<Pair<String, SafetyData>> =
        call("PUT safety", { api.saveSafety(propertyId, SafetyMapper.body(safety)) }) { d, m -> m to SafetyMapper.data(d) }

    override suspend fun emergency(leaseId: String): AppResult<EmergencyInfo> {
        val r = call("GET emergency", { api.emergency(leaseId) }) { d, _ -> d.takeIf { it is JsonObject }?.let { it to SafetyMapper.emergency(it) } }
        return when (r) {
            is AppResult.Success -> {
                withContext(Dispatchers.IO) { cache.save(leaseId, r.data.first.toString()) }
                AppResult.Success(r.data.second)
            }
            is AppResult.Failure -> {
                // Lease ended / not found → the old numbers must not stay on the phone.
                if (SafetyMapper.leaseGone(r.error)) clearEmergency()
                r
            }
        }
    }

    override suspend fun cachedEmergency(leaseId: String): EmergencyInfo? = withContext(Dispatchers.IO) {
        cache.read(leaseId)?.let { raw -> runCatching { SafetyMapper.emergency(SafeRentJson.instance.parseToJsonElement(raw)) }.getOrNull() }
    }

    override fun clearEmergency() = cache.clear()

    private suspend fun <R> call(
        name: String,
        request: suspend () -> Response<ApiEnvelope<JsonElement>>,
        map: (data: JsonElement, message: String) -> R?,
    ): AppResult<R> = when (val r = safeApiCall(name, request)) {
        is ApiResult.Success -> {
            if (!name.startsWith("GET")) ApiCache.clear()
            map(r.data ?: JsonNull, r.message)?.let { AppResult.Success(it) } ?: AppResult.Failure(AppError.Parse())
        }
        is ApiResult.NotModified -> AppResult.Failure(AppError.Parse())
        is ApiResult.Failure -> AppResult.Failure(OwnerMapper.error(r.error))
    }
}

/** JSON ⇄ safety models (pure, unit-tested). */
object SafetyMapper {
    private fun JsonObject.obj(k: String) = this[k] as? JsonObject
    private fun JsonObject.boolOrNull(k: String) = (this[k] as? JsonPrimitive)?.takeUnless { it is JsonNull }?.let { it.booleanOrNull ?: it.content.lowercase().toBooleanStrictOrNull() }
    private fun JsonObject.intOrNull(k: String) = (this[k] as? JsonPrimitive)?.takeUnless { it is JsonNull }?.let { it.intOrNull ?: it.doubleOrNull?.toInt() }
    private fun JsonObject.doubleOrNull(k: String) = (this[k] as? JsonPrimitive)?.takeUnless { it is JsonNull }?.doubleOrNull
    private fun JsonObject.textOrNull(k: String) = text(k).trim().ifBlank { null }

    /** 412 LEASE_ENDED / 404 lease not found. */
    fun leaseGone(e: AppError): Boolean = SafetyRules.leaseGone(e)

    fun place(o: JsonObject?): SafetyPlace? = o?.let {
        SafetyPlace(it.text("name"), it.text("phone"), it.text("address"), it.doubleOrNull("distanceKm"), it.doubleOrNull("latitude"), it.doubleOrNull("longitude"))
    }

    private fun contacts(o: JsonObject, key: String) = o.arrayAt(key).mapNotNull { e ->
        (e as? JsonObject)?.let { EmergencyContact(it.text("label"), it.text("phone")) }?.takeIf { it.phone.isNotBlank() }
    }

    fun safety(o: JsonObject?): Safety {
        if (o == null) return Safety()
        return Safety(
            fireExtinguisher = o.boolOrNull("fireExtinguisher"),
            fireExtinguisherCount = o.intOrNull("fireExtinguisherCount"),
            fireExtinguisherExpiry = o.textOrNull("fireExtinguisherExpiry")?.take(10),
            smokeDetectors = o.boolOrNull("smokeDetectors"),
            smokeDetectorLocations = o.text("smokeDetectorLocations"),
            emergencyExits = o.intOrNull("emergencyExits"),
            emergencyStaircase = o.boolOrNull("emergencyStaircase"),
            firstAidKit = o.boolOrNull("firstAidKit"),
            firstAidLocation = o.text("firstAidLocation"),
            securityName = o.text("securityName"),
            securityPhone = o.text("securityPhone"),
            security24x7 = o.boolOrNull("security24x7"),
            emergencyContacts = contacts(o, "emergencyContacts"),
            nearestHospital = place(o.obj("nearestHospital")) ?: SafetyPlace(),
            nearestPoliceStation = place(o.obj("nearestPoliceStation")) ?: SafetyPlace(),
            instructions = o.text("instructions"),
        )
    }

    fun data(d: JsonElement): SafetyData {
        val o = d as? JsonObject ?: return SafetyData()
        val fromServer = o.arrayAt("certificates").mapNotNull { e ->
            (e as? JsonObject)?.let { SafetyCertificate(it.text("docType").uppercase(), it.boolOrNull("uploaded") == true, it.textOrNull("status")?.uppercase()) }
        }.associateBy { it.docType }
        return SafetyData(
            safety = safety(o.obj("safety")),
            extinguisherState = o.textOrNull("extinguisherState")?.uppercase(),
            score = (o.intOrNull("score") ?: 0).coerceIn(0, 100),
            // Always the 3 rows, in this order.
            certificates = SafetyRules.CERTIFICATES.map { fromServer[it] ?: SafetyCertificate(it) },
            updatedAt = o.textOrNull("updatedAt"),
        )
    }

    private fun placeBody(p: SafetyPlace): JsonElement = if (p.isEmpty) JsonNull else buildJsonObject {
        put("name", p.name.trim().ifBlank { null })
        put("phone", SafetyRules.phoneForApi(p.phone))
        put("address", p.address.trim().ifBlank { null })
        put("distanceKm", p.distanceKm)
        put("latitude", p.latitude)
        put("longitude", p.longitude)
    }

    /** PUT body: the whole object (null = not answered). */
    fun body(raw: Safety): JsonObject {
        val s = SafetyRules.clean(raw)
        return buildJsonObject {
            put("fireExtinguisher", s.fireExtinguisher)
            put("fireExtinguisherCount", s.fireExtinguisherCount)
            put("fireExtinguisherExpiry", s.fireExtinguisherExpiry)
            put("smokeDetectors", s.smokeDetectors)
            put("smokeDetectorLocations", s.smokeDetectorLocations.ifBlank { null })
            put("emergencyExits", s.emergencyExits)
            put("emergencyStaircase", s.emergencyStaircase)
            put("firstAidKit", s.firstAidKit)
            put("firstAidLocation", s.firstAidLocation.ifBlank { null })
            put("securityName", s.securityName.ifBlank { null })
            put("securityPhone", SafetyRules.phoneForApi(s.securityPhone))
            put("security24x7", s.security24x7)
            put(
                "emergencyContacts",
                if (s.emergencyContacts.isEmpty()) JsonNull else buildJsonArray {
                    s.emergencyContacts.forEach { c -> add(buildJsonObject { put("label", c.label); put("phone", SafetyRules.phoneForApi(c.phone)) }) }
                },
            )
            put("nearestHospital", placeBody(s.nearestHospital))
            put("nearestPoliceStation", placeBody(s.nearestPoliceStation))
            put("instructions", s.instructions.ifBlank { null })
        }
    }

    /** Browse data.property.safety (features only). */
    fun listing(o: JsonObject?): ListingSafety? {
        if (o == null) return null
        return ListingSafety(
            fireExtinguisher = o.boolOrNull("fireExtinguisher"),
            fireExtinguisherState = o.textOrNull("fireExtinguisherState")?.uppercase(),
            smokeDetectors = o.boolOrNull("smokeDetectors"),
            emergencyExits = o.intOrNull("emergencyExits"),
            emergencyStaircase = o.boolOrNull("emergencyStaircase"),
            firstAidKit = o.boolOrNull("firstAidKit"),
            security = o.textOrNull("security")?.uppercase(),
            verifiedCertificates = o.arrayAt("certificates").mapNotNull { e ->
                (e as? JsonObject)?.takeIf { it.boolOrNull("verified") == true }?.text("docType")?.uppercase()?.ifBlank { null }
            },
        ).takeUnless { it.isEmpty }
    }

    fun emergency(d: JsonElement): EmergencyInfo {
        val o = d as? JsonObject ?: return EmergencyInfo()
        val property = o.obj("property")
        val landlord = o.obj("landlord")
        val security = o.obj("security")
        val ext = o.obj("fireExtinguisher")
        val helplines = o.arrayAt("helplines").mapNotNull { e -> (e as? JsonObject)?.let { Helpline(it.text("label"), it.text("phone")) }?.takeIf { it.phone.isNotBlank() } }
        return EmergencyInfo(
            propertyTitle = property?.text("title").orEmpty(),
            address = property?.text("address").orEmpty(),
            landlordName = landlord?.text("fullName").orEmpty(),
            landlordPhone = landlord?.text("phoneNumber").orEmpty(),
            securityName = security?.text("name").orEmpty(),
            securityPhone = security?.text("phone").orEmpty(),
            security24x7 = security?.boolOrNull("available24x7") == true,
            contacts = contacts(o, "emergencyContacts"),
            helplines = helplines.ifEmpty { SafetyRules.DEFAULT_HELPLINES },
            hospital = place(o.obj("nearestHospital"))?.takeUnless { it.isEmpty },
            police = place(o.obj("nearestPoliceStation"))?.takeUnless { it.isEmpty },
            firstAidLocation = o.obj("firstAid")?.text("location")?.ifBlank { null },
            extinguisherCount = ext?.intOrNull("count"),
            extinguisherExpiry = ext?.textOrNull("expiry")?.take(10),
            extinguisherState = ext?.textOrNull("state")?.uppercase(),
            smokeDetectorLocations = o.obj("smokeDetectors")?.text("locations")?.ifBlank { null },
            emergencyExits = o.intOrNull("emergencyExits"),
            emergencyStaircase = o.boolOrNull("emergencyStaircase"),
            instructions = o.text("instructions"),
        )
    }
}
