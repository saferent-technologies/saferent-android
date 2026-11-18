package com.saferenttechnologies.android.data.support

import com.saferenttechnologies.android.domain.model.SupportRules
import com.saferenttechnologies.android.domain.model.Ticket
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SupportMapperTest {
    private fun json(s: String) = Json.parseToJsonElement(s)

    @Test fun `list item`() {
        val p = SupportMapper.page(
            json("""[{"id":"t1","category":"payment","subject":"Rent paid but shows overdue","status":"in_progress","updatedAt":"2026-11-18T10:00:00Z","raisedBy":{"id":"u1","fullName":"Ravi","role":"TENANT"},"assignedTo":{"id":"s1","fullName":"Anil"},"_count":{"messages":3}}]"""),
            json("""{"page":1,"totalPages":2}"""),
            1,
        )
        val t = p.items.single()
        assertEquals("PAYMENT", t.category)
        assertEquals("IN_PROGRESS", t.status)
        assertEquals(3, t.messageCount)
        assertEquals("Anil", t.assignedTo?.fullName)
        assertTrue(p.hasMore)
    }

    @Test fun `detail with messages, support vs mine, internal hidden`() {
        val t = SupportMapper.ticketOf(
            json(
                """{"ticket":{"id":"t1","subject":"S","description":"D","status":"CLOSED","messages":[
                   {"id":"m1","authorId":"u1","body":"hi","author":{"id":"u1","fullName":"Ravi","role":"TENANT"}},
                   {"id":"m2","authorId":"s1","body":"we're on it","author":{"id":"s1","fullName":"Anil","role":"SUPPORT"}},
                   {"id":"m3","authorId":"s1","body":"note","internal":true}]},"canManage":false}""",
            ),
        )!!
        assertTrue(t.closed)
        assertEquals(listOf("m1", "m2"), t.messages.map { it.id })
        assertFalse(t.messages[0].fromSupport)
        assertTrue(t.messages[1].fromSupport)
    }

    @Test fun `rules`() {
        assertEquals(listOf("PAYMENT", "TENANT_COMPLAINT", "ACCOUNT", "PROPERTY", "OTHER"), SupportRules.categories(landlord = true))
        assertTrue("LANDLORD_COMPLAINT" in SupportRules.categories(landlord = false))
        assertFalse(SupportRules.canSend("PAYMENT", "Hey", "Long enough text"))
        assertTrue(SupportRules.canSend("PAYMENT", "Rent paid", "Long enough text"))
        assertFalse(SupportRules.canSend(null, "Rent paid", "Long enough text"))
        val merged = SupportRules.merge(listOf(Ticket("a", updatedAt = "2026-11-01T00:00:00Z")), listOf(Ticket("b", updatedAt = "2026-11-02T00:00:00Z"), Ticket("a", updatedAt = "2026-11-01T00:00:00Z")))
        assertEquals(listOf("b", "a"), merged.map { it.id })
    }
}
