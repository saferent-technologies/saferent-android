package com.saferenttechnologies.android.data.browse

import com.saferenttechnologies.android.core.log.AppLogger
import com.saferenttechnologies.android.data.mapper.arrayAt
import com.saferenttechnologies.android.data.mapper.asText
import com.saferenttechnologies.android.data.mapper.int
import com.saferenttechnologies.android.data.mapper.text
import com.saferenttechnologies.android.data.remote.ApiResult
import com.saferenttechnologies.android.data.remote.BrowseApi
import com.saferenttechnologies.android.data.remote.SafeApiCall
import com.saferenttechnologies.android.data.remote.dto.ApiEnvelope
import com.saferenttechnologies.android.domain.model.AppError
import com.saferenttechnologies.android.domain.model.AppResult
import com.saferenttechnologies.android.domain.model.ApplyInput
import com.saferenttechnologies.android.domain.model.BedOption
import com.saferenttechnologies.android.domain.model.BrowseFilters
import com.saferenttechnologies.android.domain.model.CostBreakdown
import com.saferenttechnologies.android.domain.model.CostLine
import com.saferenttechnologies.android.domain.model.Disclosure
import com.saferenttechnologies.android.domain.model.Listing
import com.saferenttechnologies.android.domain.model.ListingDetail
import com.saferenttechnologies.android.domain.model.ListingPage
import com.saferenttechnologies.android.domain.model.ListingPhoto
import com.saferenttechnologies.android.domain.model.LocationSuggestion
import com.saferenttechnologies.android.domain.model.MyApplicationRef
import com.saferenttechnologies.android.domain.model.PlaceFilter
import com.saferenttechnologies.android.domain.model.PropertyStructure
import com.saferenttechnologies.android.domain.model.Review
import com.saferenttechnologies.android.domain.model.ReviewSummary
import com.saferenttechnologies.android.domain.model.ReviewsPreview
import com.saferenttechnologies.android.domain.model.RoomOption
import com.saferenttechnologies.android.domain.model.TenantApplication
import com.saferenttechnologies.android.domain.model.TenantLease
import com.saferenttechnologies.android.domain.model.UnitOption
import com.saferenttechnologies.android.domain.repository.BrowseRepository
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.put
import retrofit2.Response
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BrowseRepositoryImpl @Inject constructor(
    private val api: BrowseApi,
    private val safeApiCall: SafeApiCall,
    private val logger: AppLogger,
) : BrowseRepository {

    override suspend fun browse(filters: BrowseFilters, page: Int, pageSize: Int): AppResult<ListingPage> =
        call("GET browse", { api.browse(filters.toQuery(page, pageSize)) }) { data, meta -> BrowseMapper.page(data, meta, page) }

    override suspend fun locations(query: String): AppResult<List<LocationSuggestion>> =
        call("GET browse/locations", { api.locations(query, LOCATION_LIMIT) }) { data, _ ->
            data.arrayAt("items").mapNotNull { (it as? JsonObject)?.let(BrowseMapper::suggestion) }
        }

    override suspend fun listing(id: String): AppResult<ListingDetail> =
        call("GET browse/{id}", { api.listing(id) }) { data, _ -> (data as? JsonObject)?.let(BrowseMapper::detail) }

    override suspend fun reviews(propertyId: String): AppResult<ReviewsPreview> =
        call("GET property reviews", { api.reviews(propertyId, 3) }) { data, meta -> BrowseMapper.reviews(data, meta) }

    override suspend fun apply(propertyId: String, input: ApplyInput): AppResult<String> {
        val body = buildJsonObject {
            input.unitId?.let { put("unitId", it) }
            input.bedId?.let { put("bedId", it) }
            input.proposedMoveIn?.let { put("proposedMoveIn", it.iso()) }
            put("occupants", input.occupants)
            if (input.message.isNotBlank()) put("message", input.message.trim())
        }
        logger.d(TAG, "apply (unit=${input.unitId != null}, bed=${input.bedId != null}, occupants=${input.occupants})")
        return message("POST browse/apply") { api.apply(propertyId, body) }
    }

    override suspend fun enquire(propertyId: String, message: String): AppResult<String> =
        message("POST browse/enquire") { api.enquire(propertyId, buildJsonObject { put("message", message.trim()) }) }

    override suspend fun report(propertyId: String, reason: String, details: String): AppResult<String> =
        message("POST fraud-reports") {
            api.report(buildJsonObject { put("propertyId", propertyId); put("reason", reason); put("details", details.trim()) })
        }

    override suspend fun activeLease(): AppResult<TenantLease?> =
        call("GET leases (active)", { api.leases("ACTIVE", 1) }) { data, _ ->
            // Wrapped so "no lease" (empty list) is a successful null.
            Wrapper(data.arrayAt("items", "leases").firstNotNullOfOrNull { (it as? JsonObject)?.let(BrowseMapper::lease) })
        }.let { r -> if (r is AppResult.Success) AppResult.Success(r.data.value) else r as AppResult.Failure }

    override suspend fun pendingReviews(): AppResult<Int> =
        call("GET reviews/pending", { api.pendingReviews() }) { data, meta ->
            (meta as? JsonObject)?.int("total", -1)?.takeIf { it >= 0 } ?: data.arrayAt("items", "reviews").size
        }

    override suspend fun myApplications(status: String): AppResult<List<TenantApplication>> =
        call("GET applications", { api.applications(status) }) { data, _ ->
            data.arrayAt("items", "applications").mapNotNull { (it as? JsonObject)?.let(BrowseMapper::application) }
        }

    override suspend fun withdraw(applicationId: String): AppResult<String> =
        message("POST applications/withdraw") { api.withdraw(applicationId) }

    // ---------------- plumbing ----------------

    private class Wrapper<T>(val value: T)

    private suspend fun <R> call(
        name: String,
        request: suspend () -> Response<ApiEnvelope<JsonElement>>,
        map: (data: JsonElement, meta: JsonElement?) -> R?,
    ): AppResult<R> = when (val r = safeApiCall(name, request)) {
        is ApiResult.Success -> map(r.data ?: JsonNull, r.meta)?.let { AppResult.Success(it) } ?: AppResult.Failure(AppError.Parse())
        is ApiResult.NotModified -> AppResult.Failure(AppError.Parse())
        is ApiResult.Failure -> AppResult.Failure(r.error)
    }

    private suspend fun message(name: String, request: suspend () -> Response<ApiEnvelope<JsonElement>>): AppResult<String> =
        when (val r = safeApiCall(name, request)) {
            is ApiResult.Success -> AppResult.Success(r.message)
            is ApiResult.NotModified -> AppResult.Success("")
            is ApiResult.Failure -> AppResult.Failure(r.error)
        }

    private companion object {
        const val TAG = "SR-Explore"
        const val LOCATION_LIMIT = 8
    }
}

/** JSON → domain (pure, unit-tested). Lenient: missing keys fall back to defaults. */
object BrowseMapper {
    private fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject
    private fun JsonObject.double(key: String): Double? = (this[key] as? JsonPrimitive)?.let { it.doubleOrNull ?: it.content.toDoubleOrNull() }
    private fun JsonObject.bool(key: String): Boolean = (this[key] as? JsonPrimitive)?.let { it.booleanOrNull ?: it.content.equals("true", true) } ?: false
    private fun JsonObject.intOrNull(key: String): Int? = double(key)?.toInt()

    fun listing(o: JsonObject) = Listing(
        id = o.text("id"),
        code = o.text("code"),
        title = o.text("title").ifBlank { o.text("name") },
        category = o.text("category"),
        propertyType = o.text("propertyType"),
        propertyTypeLabel = o.text("propertyTypeLabel"),
        listingType = o.text("listingType").ifBlank { "RENT" },
        locality = o.text("locality"),
        city = o.text("city"),
        state = o.text("state"),
        rentFrom = o.text("rentFrom"),
        rentTo = o.text("rentTo"),
        availableCount = o.int("availableCount", 0),
        completenessScore = o.int("completenessScore", 0),
        verified = o.text("verificationStatus").equals("VERIFIED", true),
        ownerVerified = o.bool("ownerVerified"),
        coverImageId = o.text("coverImageId").ifBlank { null },
        distanceKm = o.double("distanceKm"),
    )

    fun page(data: JsonElement, meta: JsonElement?, requested: Int): ListingPage {
        val items = data.arrayAt("items", "listings").mapNotNull { (it as? JsonObject)?.let(::listing) }.filter { it.id.isNotBlank() }
        val m = meta as? JsonObject
        val page = m?.int("page", requested) ?: requested
        return ListingPage(items, page, m?.int("totalPages", page) ?: page, m?.int("total", items.size) ?: items.size)
    }

    fun suggestion(o: JsonObject): LocationSuggestion? {
        val name = o.text("name").ifBlank { return null }
        val f = o.obj("filters")
        val filter = PlaceFilter(
            label = o.text("label").ifBlank { name },
            city = f?.text("city")?.ifBlank { null },
            locality = f?.text("locality")?.ifBlank { null },
            state = f?.text("state")?.ifBlank { null },
        )
        val kind = o.text("kind").uppercase()
        val where = listOf(o.text("city"), o.text("state")).filter { it.isNotBlank() }.joinToString(", ")
        val sub = when (kind) {
            "LOCALITY" -> listOf("Area", where).filter { it.isNotBlank() }.joinToString(" · ")
            "CITY" -> listOf("City", where).filter { it.isNotBlank() }.joinToString(" · ")
            "STATE" -> "State"
            else -> where
        }
        fun num(k: String) = (o[k] as? kotlinx.serialization.json.JsonPrimitive)?.content?.toDoubleOrNull()
        return LocationSuggestion(kind, name, filter.label, sub, o.int("listingCount", 0), filter, num("latitude"), num("longitude"))
    }

    private fun breakdown(o: JsonObject?): CostBreakdown? = o?.let {
        CostBreakdown(
            monthlyTotal = it.text("monthlyTotal"),
            moveInTotal = it.text("moveInTotal"),
            lines = it.arrayAt("lines").mapNotNull { l ->
                val lo = l as? JsonObject ?: return@mapNotNull null
                CostLine(lo.text("label"), lo.text("amount").ifBlank { null }, lo.text("frequency"))
            },
        )
    }

    private fun disclosures(e: JsonElement?, labels: Map<String, String> = emptyMap()): List<Disclosure> = when (e) {
        is JsonObject -> e.mapNotNull { (k, v) ->
            val status = v.asText().orEmpty()
            if (status.isBlank()) null else Disclosure(k, labels[k] ?: humanizeKey(k), status)
        }
        else -> e?.arrayAt()?.mapNotNull { a ->
            val o = a as? JsonObject ?: return@mapNotNull null
            Disclosure(o.text("key"), o.text("label").ifBlank { humanizeKey(o.text("key")) }, o.text("status").ifBlank { "YES" })
        }.orEmpty()
    }

    /** "waterSupplyType" → "Water supply type"; "LIFT" → "Lift". */
    fun humanizeKey(key: String): String {
        val spaced = if (key.contains('_') || key.all { !it.isLowerCase() }) key.lowercase().replace('_', ' ')
        else key.replace(Regex("([a-z])([A-Z])"), "$1 $2").lowercase()
        return spaced.replaceFirstChar { it.uppercase() }
    }

    fun detail(data: JsonObject): ListingDetail? {
        val p = data.obj("property") ?: data
        val id = p.text("id").ifBlank { return null }
        val units = p.arrayAt("units").mapNotNull { u ->
            val o = u as? JsonObject ?: return@mapNotNull null
            val attrs = o.obj("attributes")
            val pricing = o.obj("pricing")
            UnitOption(
                id = o.text("id"),
                unitNumber = o.text("unitNumber"),
                floorNumber = o.intOrNull("floorNumber"),
                bedrooms = o.intOrNull("bedrooms") ?: attrs?.intOrNull("bedrooms"),
                bathrooms = o.intOrNull("bathrooms") ?: attrs?.intOrNull("bathrooms"),
                bhkType = attrs?.text("bhkType").orEmpty().ifBlank { o.text("bhkType") },
                furnishing = o.text("furnishing").ifBlank { attrs?.text("furnishing").orEmpty() },
                builtUpArea = o.text("builtUpArea").ifBlank { attrs?.text("builtUpArea").orEmpty() },
                availabilityStatus = o.text("availabilityStatus"),
                monthlyRent = pricing?.text("monthlyRent").orEmpty(),
                securityDeposit = pricing?.text("securityDeposit").orEmpty(),
                breakdown = breakdown(pricing?.obj("breakdown")),
                availableFrom = o.text("availableFrom"),
                availabilityLabel = o.text("availabilityLabel"),
                availableNow = o.text("availableNow").takeIf { it.isNotBlank() }?.equals("true", true),
                amenities = disclosures(o["amenities"]),
                imageIds = o.arrayAt("imageIds").mapNotNull { it.asText() },
            )
        }
        val beds = p.arrayAt("rooms").flatMap { r ->
            val room = r as? JsonObject ?: return@flatMap emptyList()
            room.arrayAt("beds").mapNotNull { b ->
                val bo = b as? JsonObject ?: return@mapNotNull null
                BedOption(
                    id = bo.text("id"),
                    roomNumber = room.text("roomNumber"),
                    bedNumber = bo.text("bedNumber"),
                    sharingType = room.text("sharingType"),
                    rent = bo.text("rent").ifBlank { room.text("rentPerBed") },
                    availabilityStatus = bo.text("availabilityStatus"),
                )
            }
        }
        val rooms = p.arrayAt("rooms").mapNotNull { r ->
            val o = r as? JsonObject ?: return@mapNotNull null
            RoomOption(
                id = o.text("id"),
                roomNumber = o.text("roomNumber"),
                sharingType = o.text("sharingType"),
                attachedBathroom = o.text("attachedBathroom"),
                acAvailable = o.text("acAvailable"),
                rentPerBed = o.text("rentPerBed").ifBlank { o.obj("pricing")?.text("monthlyRent").orEmpty() },
                securityDeposit = o.text("securityDeposit").ifBlank { o.obj("pricing")?.text("securityDeposit").orEmpty() },
                totalBeds = o.intOrNull("totalBeds") ?: o.intOrNull("bedCount") ?: o.arrayAt("beds").size,
                availableBeds = o.intOrNull("availableBeds") ?: 0,
                beds = o.arrayAt("beds").mapNotNull { b ->
                    val bo = b as? JsonObject ?: return@mapNotNull null
                    BedOption(bo.text("id"), o.text("roomNumber"), bo.text("bedNumber"), o.text("sharingType"), bo.text("rent").ifBlank { o.text("rentPerBed") }, bo.text("availabilityStatus"))
                },
                imageIds = o.arrayAt("imageIds").mapNotNull { it.asText() },
                amenities = disclosures(o["amenities"]),
                breakdown = breakdown(o.obj("pricing")?.obj("breakdown")),
            )
        }
        val legend = (p.obj("disclosureLegend"))?.mapValues { it.value.asText().orEmpty() }.orEmpty()
        val my = (data.obj("myApplication") ?: data.arrayAt("myApplications").firstOrNull() as? JsonObject)?.let {
            MyApplicationRef(it.text("id"), it.text("status"), it.text("unitId").ifBlank { null }, it.text("bedId").ifBlank { null })
        }
        val completeness = p.obj("completeness")
        return ListingDetail(
            id = id,
            code = p.text("code"),
            propertyType = p.text("propertyType"),
            title = p.text("title").ifBlank { p.text("name") },
            description = p.text("description"),
            propertyTypeLabel = p.text("propertyTypeLabel"),
            structure = PropertyStructure.of(p.text("structure")),
            listingType = p.text("listingType").ifBlank { "RENT" },
            locality = p.text("locality"),
            city = p.text("city"),
            state = p.text("state"),
            pincode = p.text("pincode"),
            latitude = p.double("latitude"),
            longitude = p.double("longitude"),
            building = p.obj("building")?.mapNotNull { (k, v) -> v.asText()?.takeIf { it.isNotBlank() }?.let { k to it } }?.toMap().orEmpty(),
            verified = p.text("verificationStatus").equals("VERIFIED", true),
            ownerVerified = p.bool("ownerVerified"),
            completenessScore = completeness?.int("score", 0) ?: p.int("completenessScore", 0),
            completenessNote = completeness?.text("note").orEmpty(),
            missing = completeness?.arrayAt("missing")?.mapNotNull { it.asText() }.orEmpty(),
            rentFrom = p.text("rentFrom"),
            rentTo = p.text("rentTo"),
            availableCount = p.int("availableCount", 0),
            units = units,
            beds = beds,
            rooms = rooms,
            amenities = disclosures(p["amenities"]),
            utilities = disclosures(p["utilities"]),
            policies = disclosures(p["policy"] ?: p["policies"]),
            photos = p.arrayAt("images").mapNotNull { i ->
                val o = i as? JsonObject ?: return@mapNotNull null
                val imgId = o.text("id").ifBlank { return@mapNotNull null }
                ListingPhoto(imgId, o.text("category"), o.text("url").ifBlank { "/api/v1/files/property-images/$imgId" }, o.text("unitId").ifBlank { null })
            },
            disclosureLegend = legend,
            myApplication = my,
        )
    }

    fun reviews(data: JsonElement, meta: JsonElement?): ReviewsPreview {
        val stats = (meta as? JsonObject)?.obj("stats")
        val reviews = data.arrayAt("items", "reviews").mapNotNull { r ->
            val o = r as? JsonObject ?: return@mapNotNull null
            Review(
                id = o.text("id"),
                rating = o.double("rating") ?: o.double("overallRating"),
                comment = o.text("comment").ifBlank { o.text("text") },
                authorName = o.obj("author")?.text("fullName").orEmpty().ifBlank { o.text("authorName") },
                createdAt = o.text("createdAt"),
            )
        }
        val categories = stats?.obj("categories")?.mapNotNull { (k, v) -> (v as? JsonPrimitive)?.doubleOrNull?.let { k to it } }?.toMap().orEmpty()
        return ReviewsPreview(
            ReviewSummary(stats?.double("average") ?: stats?.double("averageRating"), stats?.int("count", reviews.size) ?: reviews.size, categories),
            reviews,
        )
    }

    fun lease(o: JsonObject) = TenantLease(
        id = o.text("id"),
        code = o.text("code"),
        propertyId = o.text("propertyId").ifBlank { o.obj("property")?.text("id").orEmpty() },
        propertyTitle = o.obj("property")?.text("title").orEmpty(),
        place = o.text("place"),
        monthlyRent = o.text("monthlyRent"),
        rentDueDay = o.intOrNull("rentDueDay"),
        startDate = o.text("startDate"),
        endDate = o.text("endDate"),
        landlordName = o.obj("landlord")?.text("fullName").orEmpty(),
    )

    fun application(o: JsonObject) = TenantApplication(
        id = o.text("id"),
        status = o.text("status"),
        proposedMoveIn = o.text("proposedMoveIn"),
        occupants = o.intOrNull("occupants"),
        propertyId = o.obj("property")?.text("id").orEmpty(),
        propertyTitle = o.obj("property")?.text("title").orEmpty(),
        city = o.obj("property")?.text("city").orEmpty(),
        place = listOfNotNull(o.obj("unit")?.text("unitNumber"), o.obj("bed")?.text("bedNumber")).filter { it.isNotBlank() }.joinToString(" · "),
        createdAt = o.text("createdAt"),
    )
}
