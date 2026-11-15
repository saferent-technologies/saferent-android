package com.saferenttechnologies.android.data.announcements

import com.saferenttechnologies.android.core.log.AppLogger
import com.saferenttechnologies.android.data.mapper.arrayAt
import com.saferenttechnologies.android.data.mapper.text
import com.saferenttechnologies.android.data.remote.AnnouncementsApi
import com.saferenttechnologies.android.data.remote.ApiResult
import com.saferenttechnologies.android.data.remote.SafeApiCall
import com.saferenttechnologies.android.data.remote.dto.ApiEnvelope
import com.saferenttechnologies.android.domain.model.Announcement
import com.saferenttechnologies.android.domain.model.AnnouncementAudience
import com.saferenttechnologies.android.domain.model.AnnouncementPage
import com.saferenttechnologies.android.domain.model.AnnouncementViewer
import com.saferenttechnologies.android.domain.model.AppError
import com.saferenttechnologies.android.domain.model.AppResult
import com.saferenttechnologies.android.domain.repository.AnnouncementsRepository
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Response
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AnnouncementsRepositoryImpl @Inject constructor(
    private val api: AnnouncementsApi,
    private val safeApiCall: SafeApiCall,
    private val logger: AppLogger,
) : AnnouncementsRepository {

    override suspend fun send(
        title: String,
        body: String,
        audience: AnnouncementAudience,
        propertyIds: Set<String>,
        leaseIds: Set<String>,
        image: ByteArray?,
    ): AppResult<Announcement> {
        logger.d(TAG, "send announcement (audience=$audience, properties=${propertyIds.size}, tenants=${leaseIds.size}, image=${image != null})")
        val props = if (audience == AnnouncementAudience.PROPERTIES) propertyIds.map { MultipartBody.Part.createFormData("propertyIds", it) } else emptyList()
        val leases = if (audience == AnnouncementAudience.TENANTS) leaseIds.map { MultipartBody.Part.createFormData("leaseIds", it) } else emptyList()
        val img = image?.let { MultipartBody.Part.createFormData("image", "announcement.jpg", it.toRequestBody("image/jpeg".toMediaType())) }
        return call("POST announcements", { api.create(title.trim().text(), body.trim().text(), audience.name.text(), props, leases, img) }) { data, _ ->
            AnnouncementsMapper.announcementOf(data)
        }
    }

    override suspend fun list(landlord: Boolean, page: Int): AppResult<AnnouncementPage> =
        call("GET announcements", { api.list(if (landlord) "landlord" else "tenant", page, PAGE_SIZE) }) { data, meta -> AnnouncementsMapper.page(data, meta, page) }

    override suspend fun detail(id: String): AppResult<Announcement> =
        call("GET announcement", { api.detail(id) }) { data, _ -> AnnouncementsMapper.announcementOf(data) }

    override suspend fun recipients(id: String): AppResult<List<AnnouncementViewer>> =
        call("GET announcement/recipients", { api.recipients(id) }) { data, _ -> AnnouncementsMapper.viewers(data) }

    override suspend fun markViewed(id: String): AppResult<Unit> =
        when (val r = safeApiCall("POST announcement/view") { api.view(id, buildJsonObject { }) }) {
            is ApiResult.Failure -> AppResult.Failure(r.error)
            else -> AppResult.Success(Unit)
        }

    private fun String.text(): RequestBody = toRequestBody("text/plain".toMediaType())

    private suspend fun <R> call(
        name: String,
        request: suspend () -> Response<ApiEnvelope<JsonElement>>,
        map: (data: JsonElement, meta: JsonElement?) -> R?,
    ): AppResult<R> = when (val r = safeApiCall(name, request)) {
        is ApiResult.Success -> map(r.data ?: JsonNull, r.meta)?.let { AppResult.Success(it) } ?: AppResult.Failure(AppError.Parse())
        is ApiResult.NotModified -> AppResult.Failure(AppError.Parse())
        is ApiResult.Failure -> AppResult.Failure(r.error)
    }

    private companion object {
        const val TAG = "SR-Announcements"
        const val PAGE_SIZE = 20
    }
}

/** JSON → announcement models (pure, unit-tested). Lenient: missing keys → defaults. */
object AnnouncementsMapper {
    private fun JsonObject.int(k: String) = (this[k] as? JsonPrimitive)?.content?.toDoubleOrNull()?.toInt()
    private fun JsonObject.flag(k: String) = (this[k] as? JsonPrimitive)?.let { it.booleanOrNull ?: it.content.equals("true", true) } ?: false

    fun announcementOf(data: JsonElement): Announcement? {
        val o = data as? JsonObject ?: return null
        val a = (o["announcement"] as? JsonObject) ?: o
        return announcement(a).takeIf { it.id.isNotBlank() }
    }

    fun announcement(o: JsonObject): Announcement {
        val image = o["image"] as? JsonObject
        val stats = o["stats"] as? JsonObject
        val from = (o["landlord"] as? JsonObject) ?: (o["from"] as? JsonObject)
        return Announcement(
            id = o.text("id"),
            title = o.text("title"),
            body = o.text("body").ifBlank { o.text("description") },
            imageUrl = o.text("imageUrl").ifBlank { image?.text("url").orEmpty() },
            audience = runCatching { AnnouncementAudience.valueOf(o.text("audience").uppercase()) }.getOrDefault(AnnouncementAudience.ALL),
            audienceLabel = o.text("audienceLabel"),
            createdAt = o.text("createdAt").ifBlank { o.text("sentAt") },
            recipientCount = stats?.int("recipients") ?: o.int("recipientCount") ?: 0,
            viewCount = stats?.int("viewed") ?: o.int("viewCount") ?: 0,
            fromName = from?.text("fullName").orEmpty().ifBlank { o.text("fromName") },
            viewed = o.flag("viewed") || o.text("viewedAt").isNotBlank(),
        )
    }

    fun page(data: JsonElement, meta: JsonElement?, requested: Int): AnnouncementPage {
        val items = data.arrayAt("announcements", "items").mapNotNull { (it as? JsonObject)?.let(::announcement) }.filter { it.id.isNotBlank() }
        val m = meta as? JsonObject
        return AnnouncementPage(items, m?.int("page") ?: requested, m?.int("totalPages") ?: requested)
    }

    fun viewers(data: JsonElement): List<AnnouncementViewer> {
        return data.arrayAt("recipients", "items").mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            val tenant = o["tenant"] as? JsonObject
            val prop = o["property"] as? JsonObject
            AnnouncementViewer(
                leaseId = o.text("leaseId"),
                name = tenant?.text("fullName").orEmpty().ifBlank { o.text("tenantName") },
                place = listOf(prop?.text("title").orEmpty(), o.text("place")).filter { it.isNotBlank() }.joinToString(" · "),
                viewedAt = o.text("viewedAt"),
            )
        }.sortedWith(compareBy<AnnouncementViewer> { !it.seen }.thenByDescending { it.viewedAt })
    }
}
