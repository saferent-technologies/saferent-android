package com.saferenttechnologies.android.data.owner

import android.content.Context
import com.saferenttechnologies.android.data.mapper.asText
import com.saferenttechnologies.android.data.mapper.text
import com.saferenttechnologies.android.data.mapper.toPropertyError
import com.saferenttechnologies.android.data.remote.ApiCache
import com.saferenttechnologies.android.data.remote.ApiResult
import com.saferenttechnologies.android.data.remote.OwnerApi
import com.saferenttechnologies.android.data.remote.SafeApiCall
import com.saferenttechnologies.android.data.remote.SafeRentJson
import com.saferenttechnologies.android.data.remote.dto.ApiEnvelope
import com.saferenttechnologies.android.domain.model.AppError
import com.saferenttechnologies.android.domain.model.AppResult
import com.saferenttechnologies.android.domain.model.AuthorityType
import com.saferenttechnologies.android.domain.model.CoOwner
import com.saferenttechnologies.android.domain.model.OwnerDetails
import com.saferenttechnologies.android.domain.model.OwnerType
import com.saferenttechnologies.android.domain.model.Ownership
import com.saferenttechnologies.android.domain.model.OwnershipInfo
import com.saferenttechnologies.android.domain.model.OwnershipRules
import com.saferenttechnologies.android.domain.model.Representative
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Response
import java.io.File
import java.math.BigDecimal
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OwnerRepositoryImpl @Inject constructor(
    private val api: OwnerApi,
    private val safeApiCall: SafeApiCall,
    @ApplicationContext private val context: Context,
) : com.saferenttechnologies.android.domain.repository.OwnerRepository {

    override suspend fun ownerDetails(): AppResult<OwnerDetails> =
        call("GET owner details", { api.ownerDetails() }) { d, _ -> OwnerMapper.details(d) }

    override suspend fun saveOwnerDetails(changes: Map<String, String?>): AppResult<Pair<String, OwnerDetails?>> {
        val body = buildJsonObject { changes.forEach { (k, v) -> put(k, if (v == null) JsonNull else JsonPrimitive(v)) } }
        return call("PUT owner details", { api.saveOwnerDetails(body) }) { d, message -> message to OwnerMapper.details(d) }
    }

    override suspend fun ownership(propertyId: String): AppResult<OwnershipInfo> =
        call("GET ownership", { api.ownership(propertyId) }) { d, _ -> OwnerMapper.info(d) }

    override suspend fun saveOwnership(propertyId: String, ownership: Ownership): AppResult<Pair<String, Ownership?>> =
        call("PUT ownership", { api.saveOwnership(propertyId, OwnerMapper.body(ownership)) }) { d, message ->
            message to ((d as? JsonObject)?.get("ownership") as? JsonObject)?.let(OwnerMapper::ownership)
        }

    override suspend fun uploadPropertyDocument(propertyId: String, uri: String, docType: String, expiryDate: String?): AppResult<String> = withContext(Dispatchers.IO) {
        // content:// → a temp copy (the picker's file can't be sent directly); deleted afterwards.
        val local = if (uri.startsWith("/")) File(uri) else null
        val mime = (if (local != null) null else context.contentResolver.getType(android.net.Uri.parse(uri)))
            ?: when (uri.substringAfterLast('.', "").lowercase()) {
                "pdf" -> "application/pdf"
                "png" -> "image/png"
                else -> "image/jpeg"
            }
        if (mime !in ALLOWED) return@withContext AppResult.Failure(AppError.Unknown("Choose a JPG, PNG or PDF file."))
        val ext = when (mime) { "application/pdf" -> "pdf"; "image/png" -> "png"; else -> "jpg" }
        val file = local ?: runCatching {
            val tmp = File(context.cacheDir, "property_doc_upload.$ext")
            context.contentResolver.openInputStream(android.net.Uri.parse(uri))!!.use { input -> tmp.outputStream().use { input.copyTo(it) } }
            tmp
        }.getOrElse { return@withContext AppResult.Failure(AppError.Unknown("Couldn't open that file. Try another one.")) }
        try {
            val part = MultipartBody.Part.createFormData("file", "${docType.lowercase()}.$ext", file.asRequestBody(mime.toMediaType()))
            call("POST property document", {
                api.uploadDocument(
                    propertyId, part, docType.toRequestBody("text/plain".toMediaType()),
                    expiryDate?.takeIf { it.isNotBlank() }?.toRequestBody("text/plain".toMediaType()),
                )
            }) { _, message -> message }
        } finally {
            if (local == null || local.path.startsWith(context.cacheDir.path)) file.delete()
        }
    }

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

    private companion object {
        val ALLOWED = setOf("application/pdf", "image/png", "image/jpeg", "image/jpg")
    }
}

/** JSON ⇄ owner models (pure, unit-tested). */
object OwnerMapper {
    private fun JsonObject.obj(k: String) = this[k] as? JsonObject
    private fun JsonObject.bool(k: String, default: Boolean = false) =
        (this[k] as? JsonPrimitive)?.let { it.booleanOrNull ?: it.content.equals("true", true) } ?: default

    /** 50 / 50.0 / "33.33" → "50" / "33.33". */
    private fun JsonObject.percent(k: String): String =
        text(k).toBigDecimalOrNull()?.stripTrailingZeros()?.toPlainString().orEmpty()

    fun details(data: JsonElement): OwnerDetails? {
        val root = data as? JsonObject ?: return null
        val o = root.obj("ownerDetails") ?: root
        return OwnerDetails(
            fullName = o.text("fullName"),
            nameLocked = o.bool("nameLocked"),
            phoneNumber = o.text("phoneNumber"),
            email = o.text("email"),
            address = o.text("address"),
            city = o.text("city"),
            state = o.text("state"),
            pincode = o.text("pincode"),
            alternatePhone = o.text("alternatePhone"),
            preferredLanguage = o.text("preferredLanguage"),
            preferredContactMethod = o.text("preferredContactMethod").uppercase(),
            emergencyContactName = o.text("emergencyContactName"),
            emergencyContactPhone = o.text("emergencyContactPhone"),
            emergencyContactRelation = o.text("emergencyContactRelation"),
        )
    }

    fun info(data: JsonElement): OwnershipInfo? {
        val o = data as? JsonObject ?: return null
        return OwnershipInfo(
            ownership = o.obj("ownership")?.let(::ownership),
            powerOfAttorneyUploaded = o.bool("powerOfAttorneyUploaded"),
            canEdit = o.bool("canEdit", default = true),
        )
    }

    fun ownership(o: JsonObject): Ownership {
        val type = OwnerType.entries.firstOrNull { it.name == o.text("ownerType").uppercase() } ?: OwnerType.INDIVIDUAL
        return Ownership(
            ownerType = type,
            ownershipType = o.text("ownershipType").uppercase().ifBlank { if (type == OwnerType.JOINT) "JOINT" else "SOLE" },
            companyName = o.text("companyName"),
            ownerSharePercent = o.percent("ownerSharePercent"),
            coOwners = (o["coOwners"] as? JsonArray)?.mapNotNull { e ->
                val c = e as? JsonObject ?: return@mapNotNull null
                CoOwner(c.text("name"), c.text("relation"), c.text("phone"), c.text("email"), c.percent("sharePercent"))
            }.orEmpty(),
            representative = o.obj("representative")?.let { r ->
                Representative(
                    name = r.text("name"),
                    phone = r.text("phone"),
                    email = r.text("email"),
                    relation = r.text("relation"),
                    authorityType = AuthorityType.entries.firstOrNull { it.name == r.text("authorityType").uppercase() } ?: AuthorityType.OTHER,
                )
            },
            updatedAt = o.text("updatedAt"),
        )
    }

    /** The full PUT body (numbers as numbers; optional empties left out). */
    fun body(raw: Ownership): JsonObject {
        val o = OwnershipRules.normalized(raw)
        return buildJsonObject {
            put("ownerType", o.ownerType.name)
            put("ownershipType", o.ownershipType)
            if (o.ownerType == OwnerType.COMPANY) put("companyName", o.companyName)
            if (o.isJoint) OwnershipRules.percent(o.ownerSharePercent)?.let { put("ownerSharePercent", it.toNumber()) }
            put(
                "coOwners",
                buildJsonArray {
                    o.coOwners.forEach { c ->
                        add(
                            buildJsonObject {
                                put("name", c.name)
                                if (c.relation.isNotEmpty()) put("relation", c.relation)
                                if (c.phone.isNotEmpty()) put("phone", c.phone)
                                if (c.email.isNotEmpty()) put("email", c.email)
                                OwnershipRules.percent(c.sharePercent)?.let { put("sharePercent", it.toNumber()) }
                            },
                        )
                    }
                },
            )
            val r = o.representative
            if (r == null) {
                put("representative", JsonNull)
            } else {
                put(
                    "representative",
                    buildJsonObject {
                        put("name", r.name)
                        put("phone", r.phone)
                        if (r.email.isNotEmpty()) put("email", r.email)
                        put("relation", r.relation)
                        put("authorityType", r.authorityType.name)
                    },
                )
            }
        }
    }

    /** 50 → 50 (int), 33.33 → 33.33. */
    private fun BigDecimal.toNumber(): Number = stripTrailingZeros().let { if (it.scale() <= 0) it.toLong() else it.toDouble() }

    /**
     * 400 INVALID_INPUT → FieldIssues keyed by the FULL path ("representative.phone", "coOwners.1.sharePercent"),
     * other errors as everywhere else (412 → InvalidState …).
     */
    fun error(e: AppError): AppError {
        if (e is AppError.Server && e.httpCode == 400) {
            val details = e.details?.let { runCatching { SafeRentJson.instance.parseToJsonElement(it) }.getOrNull() } as? JsonObject
            val issues = (details?.get("issues") as? JsonArray)?.mapNotNull { i ->
                val o = i as? JsonObject ?: return@mapNotNull null
                val path = when (val p = o["path"]) {
                    is JsonArray -> p.mapNotNull { it.asText() }.joinToString(".")
                    else -> o.text("path")
                }.ifBlank { return@mapNotNull null }
                path to o.text("message").ifBlank { e.message }
            }?.toMap().orEmpty()
            return AppError.FieldIssues(e.message, issues)
        }
        return e.toPropertyError(SafeRentJson.instance)
    }
}
