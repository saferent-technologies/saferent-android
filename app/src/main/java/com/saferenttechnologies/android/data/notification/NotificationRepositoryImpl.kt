package com.saferenttechnologies.android.data.notification

import com.saferenttechnologies.android.data.mapper.arrayAt
import com.saferenttechnologies.android.data.mapper.int
import com.saferenttechnologies.android.data.mapper.parseIsoMillis
import com.saferenttechnologies.android.data.mapper.text
import com.saferenttechnologies.android.data.remote.ApiResult
import com.saferenttechnologies.android.data.remote.NotificationApi
import com.saferenttechnologies.android.data.remote.SafeApiCall
import com.saferenttechnologies.android.domain.model.AppError
import com.saferenttechnologies.android.domain.model.AppNotification
import com.saferenttechnologies.android.domain.model.AppResult
import com.saferenttechnologies.android.domain.model.NotificationPage
import com.saferenttechnologies.android.domain.repository.NotificationRepository
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationRepositoryImpl @Inject constructor(
    private val api: NotificationApi,
    private val safeApiCall: SafeApiCall,
) : NotificationRepository {

    override suspend fun list(page: Int, unreadOnly: Boolean): AppResult<NotificationPage> {
        val query = buildMap {
            put("page", page.toString())
            put("pageSize", PAGE_SIZE.toString())
            if (unreadOnly) put("unread", "true")
        }
        return when (val r = safeApiCall("GET notifications", { api.list(query) })) {
            is ApiResult.Success -> AppResult.Success(toPage(r.data, r.meta, page))
            is ApiResult.NotModified -> AppResult.Failure(AppError.Parse())
            is ApiResult.Failure -> AppResult.Failure(r.error)
        }
    }

    override suspend fun markRead(id: String): AppResult<Unit> = read(buildJsonObject { put("id", id) })

    override suspend fun markAllRead(): AppResult<Unit> = read(JsonObject(emptyMap()))

    private suspend fun read(body: JsonObject): AppResult<Unit> =
        when (val r = safeApiCall("POST notifications/read", { api.markRead(body) })) {
            is ApiResult.Success, is ApiResult.NotModified -> AppResult.Success(Unit)
            is ApiResult.Failure -> AppResult.Failure(r.error)
        }

    companion object {
        const val PAGE_SIZE = 20

        /** Pure mapping (unit-tested): data array + meta → page. */
        fun toPage(data: JsonElement?, meta: JsonElement?, requested: Int): NotificationPage {
            val items = data?.arrayAt("items", "notifications").orEmpty().mapNotNull { e ->
                val o = e as? JsonObject ?: return@mapNotNull null
                val id = o.text("id").ifBlank { return@mapNotNull null }
                AppNotification(
                    id = id,
                    title = o.text("title"),
                    body = o.text("body"),
                    link = o.text("link"),
                    readAt = o.text("readAt").ifBlank { null },
                    createdAtMillis = parseIsoMillis(o.text("createdAt")),
                )
            }
            val m = meta as? JsonObject
            val page = m?.int("page", requested) ?: requested
            return NotificationPage(
                items = items,
                page = page,
                totalPages = m?.int("totalPages", page) ?: page,
                unread = m?.int("unread", items.count { it.isUnread }) ?: items.count { it.isUnread },
            )
        }
    }
}
