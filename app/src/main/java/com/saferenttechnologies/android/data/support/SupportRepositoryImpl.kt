package com.saferenttechnologies.android.data.support

import com.saferenttechnologies.android.core.log.AppLogger
import com.saferenttechnologies.android.data.mapper.arrayAt
import com.saferenttechnologies.android.data.mapper.text
import com.saferenttechnologies.android.data.mapper.toPropertyError
import com.saferenttechnologies.android.data.remote.ApiResult
import com.saferenttechnologies.android.data.remote.SafeApiCall
import com.saferenttechnologies.android.data.remote.SafeRentJson
import com.saferenttechnologies.android.data.remote.SupportApi
import com.saferenttechnologies.android.data.remote.dto.ApiEnvelope
import com.saferenttechnologies.android.domain.model.AppError
import com.saferenttechnologies.android.domain.model.AppResult
import com.saferenttechnologies.android.domain.model.Ticket
import com.saferenttechnologies.android.domain.model.TicketMessage
import com.saferenttechnologies.android.domain.model.TicketPage
import com.saferenttechnologies.android.domain.model.TicketPerson
import com.saferenttechnologies.android.domain.repository.SupportRepository
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import retrofit2.Response
import javax.inject.Inject
import javax.inject.Singleton

/** Never logs subjects, descriptions or messages — only categories / counts. */
@Singleton
class SupportRepositoryImpl @Inject constructor(
    private val api: SupportApi,
    private val safeApiCall: SafeApiCall,
    private val logger: AppLogger,
) : SupportRepository {

    override suspend fun create(category: String, subject: String, description: String, priority: String, propertyId: String?): AppResult<Ticket> {
        logger.d(TAG, "new ticket (category=$category, property=${propertyId != null})")
        val body = buildJsonObject {
            put("category", category)
            put("subject", subject.trim())
            put("description", description.trim())
            put("priority", priority)
            propertyId?.let { put("propertyId", it) }
        }
        return call("POST support/tickets", { api.create(body) }) { data, _ -> SupportMapper.ticketOf(data) }
    }

    override suspend fun list(status: String, page: Int): AppResult<TicketPage> =
        call("GET support/tickets", { api.list(status, null, page, PAGE_SIZE) }) { data, meta -> SupportMapper.page(data, meta, page) }

    override suspend fun detail(id: String): AppResult<Ticket> =
        call("GET support/ticket", { api.detail(id) }) { data, _ -> SupportMapper.ticketOf(data) }

    override suspend fun reply(id: String, body: String): AppResult<Unit> =
        call("POST support/ticket/messages", { api.reply(id, buildJsonObject { put("body", body.trim()) }) }) { _, _ -> Unit }

    private suspend fun <R> call(
        name: String,
        request: suspend () -> Response<ApiEnvelope<JsonElement>>,
        map: (data: JsonElement, meta: JsonElement?) -> R?,
    ): AppResult<R> = when (val r = safeApiCall(name, request)) {
        is ApiResult.Success -> map(r.data ?: JsonNull, r.meta)?.let { AppResult.Success(it) } ?: AppResult.Failure(AppError.Parse())
        is ApiResult.NotModified -> AppResult.Failure(AppError.Parse())
        // 400 details.issues → per-field messages; 412 → InvalidState (closed ticket).
        is ApiResult.Failure -> AppResult.Failure(r.error.toPropertyError(SafeRentJson.instance))
    }

    private companion object {
        const val TAG = "SR-Support"
        const val PAGE_SIZE = 20
    }
}

/** JSON → tickets (pure, unit-tested). */
object SupportMapper {
    private fun JsonObject.int(k: String) = (this[k] as? JsonPrimitive)?.content?.toDoubleOrNull()?.toInt()

    private fun person(o: JsonObject?): TicketPerson? = o?.let { TicketPerson(it.text("id"), it.text("fullName"), it.text("role").uppercase()) }

    fun ticketOf(data: JsonElement): Ticket? {
        val o = data as? JsonObject ?: return null
        val t = (o["ticket"] as? JsonObject) ?: o
        return ticket(t).takeIf { it.id.isNotBlank() }
    }

    fun ticket(o: JsonObject): Ticket {
        val prop = o["property"] as? JsonObject
        val messages = o.arrayAt("messages").mapNotNull { e ->
            val m = e as? JsonObject ?: return@mapNotNull null
            // Internal support notes never show to users (server shouldn't send them anyway).
            if (m.text("internal").equals("true", true)) return@mapNotNull null
            TicketMessage(m.text("id"), m.text("authorId"), m.text("body"), m.text("createdAt"), person(m["author"] as? JsonObject))
        }
        return Ticket(
            id = o.text("id"),
            category = o.text("category").uppercase(),
            subject = o.text("subject"),
            description = o.text("description"),
            priority = o.text("priority").uppercase().ifBlank { "MEDIUM" },
            status = o.text("status").uppercase().ifBlank { "OPEN" },
            createdAt = o.text("createdAt"),
            updatedAt = o.text("updatedAt"),
            resolvedAt = o.text("resolvedAt").ifBlank { null },
            propertyId = o.text("propertyId").ifBlank { prop?.text("id").orEmpty() }.ifBlank { null },
            propertyTitle = prop?.text("title").orEmpty(),
            raisedBy = person(o["raisedBy"] as? JsonObject),
            assignedTo = person(o["assignedTo"] as? JsonObject),
            // Set by the server only when a staff member actually opens the ticket.
            connectedTo = person((o["connectedTo"] ?: o["connectedAgent"]) as? JsonObject)?.takeIf { it.fullName.isNotBlank() },
            connectedAt = o.text("connectedAt").ifBlank { null },
            messageCount = (o["_count"] as? JsonObject)?.int("messages") ?: messages.size,
            messages = messages,
        )
    }

    fun page(data: JsonElement, meta: JsonElement?, requested: Int): TicketPage {
        val items = data.arrayAt("tickets", "items").mapNotNull { (it as? JsonObject)?.let(::ticket) }.filter { it.id.isNotBlank() }
        val m = meta as? JsonObject
        return TicketPage(items, m?.int("page") ?: requested, m?.int("totalPages") ?: requested)
    }
}
