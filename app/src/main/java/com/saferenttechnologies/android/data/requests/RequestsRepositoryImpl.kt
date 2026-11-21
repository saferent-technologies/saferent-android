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
import com.saferenttechnologies.android.domain.repository.ReceiptFiles
import com.saferenttechnologies.android.domain.repository.RequestResult
import com.saferenttechnologies.android.domain.repository.RequestUpdate
import com.saferenttechnologies.android.domain.repository.UploadFile
import com.saferenttechnologies.android.domain.model.RequestInvoice
import com.saferenttechnologies.android.domain.model.RequestTechnician
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.doubleOrNull
import okhttp3.RequestBody.Companion.asRequestBody
import okio.BufferedSink
import okio.ForwardingSink
import okio.Buffer
import okio.buffer
import java.io.File
import java.io.IOException
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
    private val receiptFiles: ReceiptFiles,
) : RequestsRepository {

    @Volatile private var cachedOptions: RequestOptions? = null

    override suspend fun options(): AppResult<RequestOptions> {
        cachedOptions?.let { return AppResult.Success(it) }
        return call("GET requests/options", { api.options() }) { data, _ -> RequestsMapper.options(data) }
            .also { if (it is AppResult.Success) cachedOptions = it.data }
    }

    override suspend fun create(type: String, category: String, description: String, priority: String, leaseId: String?, files: List<UploadFile>, onProgress: (Int) -> Unit): AppResult<RequestResult> {
        logger.d(TAG, "raise request (type=$type, files=${files.size})")
        return action("POST requests") {
            api.create(type.text(), category.text(), description.trim().text(), priority.text(), leaseId?.text(), files.parts(onProgress))
        }
    }

    override suspend fun list(status: String?, landlord: Boolean, page: Int): AppResult<RequestPage> =
        call("GET requests", { api.list(status, if (landlord) "landlord" else "tenant", page, PAGE_SIZE) }) { data, meta -> RequestsMapper.page(data, meta, page) }

    override suspend fun summary(): AppResult<RequestSummary> =
        call("GET requests/summary", { api.summary() }) { data, _ -> RequestsMapper.summary(data) }

    override suspend fun detail(id: String): AppResult<ServiceRequest> =
        call("GET request", { api.detail(id) }) { data, _ -> RequestsMapper.requestOf(data) }

    override suspend fun remind(id: String) = action("POST request/remind") { api.remind(id, buildJsonObject { }) }

    override suspend fun resolve(id: String, paidBy: String, actualCost: Double?, remark: String, receipts: List<UploadFile>, onProgress: (Int) -> Unit): AppResult<RequestResult> {
        // No amounts in logs.
        logger.d(TAG, "resolve (receipts=${receipts.size})")
        return if (receipts.isEmpty()) {
            action("POST request/resolve") { api.resolve(id, RequestsMapper.resolveBody(paidBy, actualCost, remark)) }
        } else {
            val fields = buildMap {
                put("paidBy", paidBy.text())
                actualCost?.let { put("actualCost", RequestsMapper.number(it).text()) }
                remark.trim().takeIf { it.isNotEmpty() }?.let { put("remark", it.text()) }
            }
            action("POST request/resolve") { api.resolveWithFiles(id, fields, receipts.parts(onProgress)) }
        }
    }

    override suspend fun cancel(id: String, remark: String) =
        action("POST request/cancel") { api.cancel(id, buildJsonObject { if (remark.isNotBlank()) put("remark", remark.trim()) }) }

    override suspend fun reopen(id: String, remark: String, files: List<UploadFile>, onProgress: (Int) -> Unit) =
        action("POST request/reopen") { api.reopen(id, remark.trim().text(), files.parts(onProgress)) }

    override suspend fun addPhotos(id: String, files: List<UploadFile>, onProgress: (Int) -> Unit) =
        action("POST request/photos") { api.addPhotos(id, files.parts(onProgress)) }

    override suspend fun update(id: String, update: RequestUpdate, files: List<UploadFile>, onProgress: (Int) -> Unit): AppResult<RequestResult> {
        logger.d(TAG, "landlord update (status=${update.status}, remark=${update.remark.isNotBlank()}, work=${update.sendWork}, files=${files.size})")
        return if (files.isEmpty()) {
            action("POST request/update") { api.update(id, RequestsMapper.updateBody(update)) }
        } else {
            // Multipart can't carry null: cleared fields go as "" (only when files are attached).
            val fields = RequestsMapper.updateBody(update).mapValues { (_, v) -> ((v as? JsonPrimitive)?.takeUnless { it is JsonNull }?.content ?: "").text() }
            action("POST request/update") { api.updateWithFiles(id, fields, files.parts(onProgress)) }
        }
    }

    override suspend fun addInvoice(id: String, files: List<UploadFile>, onProgress: (Int) -> Unit): AppResult<RequestResult> {
        logger.d(TAG, "add invoice (files=${files.size})")
        return action("POST request/invoice") { api.addInvoice(id, files.parts(onProgress)) }
    }

    override suspend fun invoicePdf(url: String): AppResult<String> = withContext(Dispatchers.IO) {
        try {
            val res = api.file(url)
            val body = res.body()
            if (res.isSuccessful && body != null) {
                val bytes = body.use { it.bytes() }
                AppResult.Success(receiptFiles.cache("invoice-${url.substringAfterLast('/').filter { it.isLetterOrDigit() }.take(40)}.pdf", bytes))
            } else {
                AppResult.Failure(AppError.Server(res.code(), message = "Couldn't open the invoice. Please try again."))
            }
        } catch (e: IOException) {
            AppResult.Failure(AppError.Network())
        }
    }

    // ---------------- plumbing ----------------

    private fun String.text(): RequestBody = toRequestBody("text/plain".toMediaType())

    /** "files" parts; progress 0..100 over all bytes sent. */
    private fun List<UploadFile>.parts(onProgress: (Int) -> Unit = {}): List<MultipartBody.Part> {
        val bodies = mapIndexed { i, f ->
            when (f) {
                is UploadFile.Jpeg -> "photo-$i.jpg" to f.bytes.toRequestBody("image/jpeg".toMediaType())
                is UploadFile.Pdf -> "receipt-$i.pdf" to f.bytes.toRequestBody("application/pdf".toMediaType())
                is UploadFile.Mp4 -> "video-$i.mp4" to File(f.path).asRequestBody("video/mp4".toMediaType())
            }
        }
        val tracker = UploadProgress(bodies.sumOf { it.second.contentLength().coerceAtLeast(0) }, onProgress)
        return bodies.map { (name, body) -> MultipartBody.Part.createFormData("files", name, CountingBody(body, tracker)) }
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
                RequestPhoto(
                    id, po.text("url").ifBlank { "/api/v1/files/maintenance-photos/$id" }, po.text("updateId").ifBlank { null }, po.flag("byTenant"), po.text("createdAt"),
                    kind = po.text("kind").ifBlank { "PHOTO" }.uppercase(), mimeType = po.text("mimeType").lowercase(),
                )
            },
            invoices = o.arrayAt("invoices").mapNotNull { p ->
                val po = p as? JsonObject ?: return@mapNotNull null
                val id = po.text("id").ifBlank { return@mapNotNull null }
                RequestInvoice(id, po.text("url").ifBlank { "/api/v1/files/maintenance-photos/$id" }, po.text("mimeType").lowercase(), po.flag("byTenant"), po.text("createdAt"))
            },
            technician = (o["technician"] as? JsonObject)?.let { t ->
                t.text("name").ifBlank { null }?.let { RequestTechnician(it, t.text("phone").ifBlank { null }, t.flag("onSafeRent")) }
            },
            estimatedCost = num(o["estimatedCost"]),
            actualCost = num(o["actualCost"]),
            paidBy = o.text("paidBy").uppercase().ifBlank { null },
            workStartedAt = o.text("workStartedAt").ifBlank { null },
            completedAt = o.text("completedAt").ifBlank { null },
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
            can = RequestCan(can.flag("update"), can.flag("remind"), can.flag("resolve"), can.flag("reopen"), can.flag("cancel"), can.flag("addPhotos"), can.flag("addInvoice")),
        )
    }

    private fun num(e: JsonElement?): Double? = (e as? JsonPrimitive)?.takeUnless { it is JsonNull }?.let { it.doubleOrNull ?: it.content.toDoubleOrNull() }

    /** 1500.0 → "1500", 1500.5 → "1500.5". */
    fun number(v: Double): String = if (v % 1.0 == 0.0) v.toLong().toString() else java.math.BigDecimal(v).setScale(2, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()

    private fun numberJson(v: Double): JsonPrimitive = if (v % 1.0 == 0.0) JsonPrimitive(v.toLong()) else JsonPrimitive(java.math.BigDecimal(v).setScale(2, java.math.RoundingMode.HALF_UP).toDouble())

    fun resolveBody(paidBy: String, actualCost: Double?, remark: String): JsonObject = buildJsonObject {
        put("paidBy", paidBy)
        actualCost?.let { put("actualCost", numberJson(it)) }
        remark.trim().takeIf { it.isNotEmpty() }?.let { put("remark", it) }
    }

    /** Only what's set; work details (when [RequestUpdate.sendWork]) with null for cleared ones. */
    fun updateBody(u: RequestUpdate): JsonObject = buildJsonObject {
        u.status?.let { put("status", it) }
        u.remark.trim().takeIf { it.isNotEmpty() }?.let { put("remark", it) }
        if (u.sendWork) {
            put("technicianName", u.technicianName?.trim()?.ifBlank { null }?.let(::JsonPrimitive) ?: JsonNull)
            put("technicianPhone", u.technicianPhone?.ifBlank { null }?.let(::JsonPrimitive) ?: JsonNull)
            put("estimatedCost", u.estimatedCost?.let(::numberJson) ?: JsonNull)
            put("workStartDate", u.workStartDate?.ifBlank { null }?.let(::JsonPrimitive) ?: JsonNull)
        } else if (u.workStartDate != null) {
            put("workStartDate", u.workStartDate)
        }
        if (u.status == "RESOLVED") {
            u.paidBy?.let { put("paidBy", it) }
            u.actualCost?.let { put("actualCost", numberJson(it)) }
        }
    }
}

/** Bytes sent across all parts → 0..100 (only on change). */
internal class UploadProgress(private val total: Long, private val onProgress: (Int) -> Unit) {
    private var sent = 0L
    private var last = -1

    @Synchronized fun add(n: Long) {
        if (total <= 0) return
        sent += n
        val pct = ((sent * 100) / total).toInt().coerceIn(0, 100)
        if (pct != last) { last = pct; onProgress(pct) }
    }
}

/** Counts bytes as OkHttp writes them (OkHttp may write twice with logging: the count is capped at 100). */
internal class CountingBody(private val delegate: RequestBody, private val tracker: UploadProgress) : RequestBody() {
    override fun contentType() = delegate.contentType()
    override fun contentLength() = delegate.contentLength()
    override fun writeTo(sink: BufferedSink) {
        val counting = object : ForwardingSink(sink) {
            override fun write(source: Buffer, byteCount: Long) {
                super.write(source, byteCount)
                tracker.add(byteCount)
            }
        }.buffer()
        delegate.writeTo(counting)
        counting.flush()
    }
}
