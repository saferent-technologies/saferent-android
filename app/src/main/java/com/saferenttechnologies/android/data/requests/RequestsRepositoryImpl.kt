package com.saferenttechnologies.android.data.requests

import com.saferenttechnologies.android.core.log.AppLogger
import com.saferenttechnologies.android.data.mapper.arrayAt
import com.saferenttechnologies.android.data.mapper.asText
import com.saferenttechnologies.android.data.mapper.text
import com.saferenttechnologies.android.data.remote.ApiResult
import com.saferenttechnologies.android.data.remote.RequestsApi
import com.saferenttechnologies.android.data.remote.SafeApiCall
import com.saferenttechnologies.android.data.remote.dto.ApiEnvelope
import com.saferenttechnologies.android.domain.model.AppError
import com.saferenttechnologies.android.domain.model.AppResult
import com.saferenttechnologies.android.domain.model.RequestCan
import com.saferenttechnologies.android.domain.model.RequestCategory
import com.saferenttechnologies.android.domain.model.RequestEvent
import com.saferenttechnologies.android.domain.model.RequestOptions
import com.saferenttechnologies.android.domain.model.RequestPage
import com.saferenttechnologies.android.domain.model.RequestPhoto
import com.saferenttechnologies.android.domain.model.RequestSummary
import com.saferenttechnologies.android.domain.model.RequestType
import com.saferenttechnologies.android.domain.model.ServiceRequest
import com.saferenttechnologies.android.domain.repository.RequestResult
import com.saferenttechnologies.android.domain.repository.RequestsRepository
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Response
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RequestsRepositoryImpl @Inject constructor(
    private val api: RequestsApi,
    private val safeApiCall: SafeApiCall,
    private val logger: AppLogger,
) : RequestsRepository {

    @Volatile private var cachedOptions: RequestOptions? = null

    override suspend fun options(): AppResult<RequestOptions> {
        cachedOptions?.let { return AppResult.Success(it) }
        return call("GET requests/options", { api.options() }) { data, _ -> RequestsMapper.options(data) }
            .also { if (it is AppResult.Success) cachedOptions = it.data }
    }

    override suspend fun create(type: String, category: String, description: String, priority: String, leaseId: String?, photos: List<ByteArray>): AppResult<RequestResult> {
        logger.d(TAG, "raise request (type=$type, photos=${photos.size})")
        return action("POST requests") {
            api.create(type.text(), category.text(), description.trim().text(), priority.text(), leaseId?.text(), photos.parts())
        }
    }

    override suspend fun list(status: String?, landlord: Boolean, page: Int): AppResult<RequestPage> =
        call("GET requests", { api.list(status, if (landlord) "landlord" else "tenant", page, PAGE_SIZE) }) { data, meta -> RequestsMapper.page(data, meta, page) }

    override suspend fun summary(): AppResult<RequestSummary> =
        call("GET requests/summary", { api.summary() }) { data, _ -> RequestsMapper.summary(data) }

    override suspend fun detail(id: String): AppResult<ServiceRequest> =
        call("GET request", { api.detail(id) }) { data, _ -> RequestsMapper.requestOf(data) }

    override suspend fun remind(id: String) = action("POST request/remind") { api.remind(id, buildJsonObject { }) }

    override suspend fun resolve(id: String, remark: String) =
        action("POST request/resolve") { api.resolve(id, buildJsonObject { if (remark.isNotBlank()) put("remark", remark.trim()) }) }

    override suspend fun cancel(id: String, remark: String) =
        action("POST request/cancel") { api.cancel(id, buildJsonObject { if (remark.isNotBlank()) put("remark", remark.trim()) }) }

    override suspend fun reopen(id: String, remark: String, photos: List<ByteArray>) =
        action("POST request/reopen") { api.reopen(id, remark.trim().text(), photos.parts()) }

    override suspend fun addPhotos(id: String, photos: List<ByteArray>) =
        action("POST request/photos") { api.addPhotos(id, photos.parts()) }

    override suspend fun update(id: String, status: String?, remark: String, photos: List<ByteArray>): AppResult<RequestResult> {
        logger.d(TAG, "landlord update (status=$status, remark=${remark.isNotBlank()}, photos=${photos.size})")
        return action("POST request/update") { api.update(id, status?.text(), remark.trim().takeIf { it.isNotEmpty() }?.text(), photos.parts()) }
    }

    // ---------------- plumbing ----------------

    private fun String.text(): RequestBody = toRequestBody("text/plain".toMediaType())

    private fun List<ByteArray>.parts(): List<MultipartBody.Part> = mapIndexed { i, bytes ->
        MultipartBody.Part.createFormData("files", "photo-$i.jpg", bytes.toRequestBody("image/jpeg".toMediaType()))
    }

    private suspend fun <R> call(
        name: String,
        request: suspend () -> Response<ApiEnvelope<JsonElement>>,
        map: (data: JsonElement, meta: JsonElement?) -> R?,
    ): AppResult<R> = when (val r = safeApiCall(name, request)) {
        is ApiResult.Success -> map(r.data ?: JsonNull, r.meta)?.let { AppResult.Success(it) } ?: AppResult.Failure(AppError.Parse())
        is ApiResult.NotModified -> AppResult.Failure(AppError.Parse())
        is ApiResult.Failure -> AppResult.Failure(r.error)
    }

    private suspend fun action(name: String, request: suspend () -> Response<ApiEnvelope<JsonElement>>): AppResult<RequestResult> =
        when (val r = safeApiCall(name, request)) {
            is ApiResult.Success -> AppResult.Success(RequestResult(r.message, RequestsMapper.requestOf(r.data ?: JsonNull)))
            is ApiResult.NotModified -> AppResult.Success(RequestResult("", null))
            is ApiResult.Failure -> AppResult.Failure(r.error)
        }

    private companion object {
        const val TAG = "SR-Requests"
        const val PAGE_SIZE = 20
    }
}

/** JSON → request models (pure, unit-tested). Lenient: missing keys → defaults. */
object RequestsMapper {
    fun options(data: JsonElement): RequestOptions? {
        val o = data as? JsonObject ?: return null
        return RequestOptions(
            types = o.arrayAt("types").mapNotNull { t ->
                val to = t as? JsonObject ?: return@mapNotNull null
                val key = to.text("key").ifBlank { return@mapNotNull null }
                RequestType(
                    key = key,
                    label = to.text("label").ifBlank { key },
                    categories = to.arrayAt("categories").mapNotNull { c ->
                        val co = c as? JsonObject ?: return@mapNotNull null
                        co.text("key").ifBlank { null }?.let { RequestCategory(it, co.text("label").ifBlank { it }) }
                    },
                )
            },
            priorities = o.arrayAt("priorities").mapNotNull { it.asText() }.ifEmpty { listOf("LOW", "MEDIUM", "HIGH", "URGENT") },
            landlordStatuses = o.arrayAt("landlordStatuses").mapNotNull { s ->
                val so = s as? JsonObject ?: return@mapNotNull null
                so.text("key").ifBlank { null }?.let { it to so.text("label").ifBlank { it } }
            },
        )
    }

    fun page(data: JsonElement, meta: JsonElement?, requested: Int): RequestPage {
        val m = meta as? JsonObject
        fun int(k: String) = (m?.get(k) as? JsonPrimitive)?.content?.toDoubleOrNull()?.toInt()
        val page = int("page") ?: requested
        return RequestPage(
            items = data.arrayAt("items", "requests").mapNotNull { (it as? JsonObject)?.let(::request) },
            page = page,
            totalPages = int("totalPages") ?: page,
            needsAction = int("needsAction") ?: 0,
        )
    }

    fun summary(data: JsonElement): RequestSummary? {
        val o = data as? JsonObject ?: return null
        fun int(k: String) = (o[k] as? JsonPrimitive)?.content?.toDoubleOrNull()?.toInt() ?: 0
        return RequestSummary(int("needsAction"), int("landlordOpen"), int("myOpen"))
    }

    /** data.request or the request itself. */
    fun requestOf(data: JsonElement): ServiceRequest? {
        val o = data as? JsonObject ?: return null
        return ((o["request"] as? JsonObject) ?: o.takeIf { it.containsKey("id") })?.let(::request)
    }

    fun request(o: JsonObject): ServiceRequest {
        val prop = o["property"] as? JsonObject
        val can = o["can"] as? JsonObject
        fun JsonObject?.flag(k: String) = (this?.get(k) as? JsonPrimitive)?.let { it.booleanOrNull ?: it.content.equals("true", true) } ?: false
        return ServiceRequest(
            id = o.text("id"),
            type = o.text("type").uppercase(),
            typeLabel = o.text("typeLabel"),
            category = o.text("category").uppercase(),
            categoryLabel = o.text("categoryLabel"),
            title = o.text("title"),
            description = o.text("description"),
            priority = o.text("priority").ifBlank { "MEDIUM" }.uppercase(),
            status = o.text("status").ifBlank { "OPEN" }.uppercase(),
            statusLabel = o.text("statusLabel"),
            lastRemark = o.text("lastRemark"),
            propertyTitle = prop?.text("title").orEmpty(),
            propertyPlace = listOf(prop?.text("locality").orEmpty(), prop?.text("city").orEmpty()).filter { it.isNotBlank() }.joinToString(", "),
            place = o.text("place"),
            leaseId = o.text("leaseId"),
            raisedByName = (o["raisedBy"] as? JsonObject)?.text("fullName").orEmpty(),
            photos = o.arrayAt("photos").mapNotNull { p ->
                val po = p as? JsonObject ?: return@mapNotNull null
                val id = po.text("id").ifBlank { return@mapNotNull null }
                RequestPhoto(id, po.text("url").ifBlank { "/api/v1/files/maintenance-photos/$id" }, po.text("updateId").ifBlank { null }, po.flag("byTenant"), po.text("createdAt"))
            },
            timeline = o.arrayAt("timeline").mapNotNull { e ->
                val eo = e as? JsonObject ?: return@mapNotNull null
                val by = eo["by"] as? JsonObject
                RequestEvent(
                    id = eo.text("id"),
                    kind = eo.text("kind").uppercase(),
                    byRole = by?.text("role").orEmpty().uppercase(),
                    byName = by?.text("fullName").orEmpty(),
                    toStatusLabel = eo.text("toStatusLabel"),
                    remark = eo.text("remark"),
                    photoIds = eo.arrayAt("photoIds").mapNotNull { it.asText() },
                    at = eo.text("at"),
                )
            },
            createdAt = o.text("createdAt"),
            updatedAt = o.text("updatedAt"),
            lastReminderAt = o.text("lastReminderAt").ifBlank { null },
            needsAction = o.flag("needsAction"),
            can = RequestCan(can.flag("update"), can.flag("remind"), can.flag("resolve"), can.flag("reopen"), can.flag("cancel"), can.flag("addPhotos")),
        )
    }
}
