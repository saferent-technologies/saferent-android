package com.saferenttechnologies.android.data.announcements

import com.saferenttechnologies.android.domain.model.AnnouncementAudience
import com.saferenttechnologies.android.domain.model.AnnouncementRules
import com.saferenttechnologies.android.domain.model.DeepLinkRouter
import com.saferenttechnologies.android.domain.model.Lease
import com.saferenttechnologies.android.domain.model.NavRoute
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnnouncementsMapperTest {
    private fun json(s: String) = Json.parseToJsonElement(s)

    @Test fun `announcement with stats and image`() {
        val a = AnnouncementsMapper.announcementOf(
            json(
                """{"announcement":{"id":"a1","title":"Water off","body":"Tank cleaning Sunday","image":{"url":"/api/v1/files/announcements/i1"},
                   "audience":"properties","audienceLabel":"Green Heights","createdAt":"2026-11-15T10:00:00Z","stats":{"recipients":5,"viewed":3}}}""",
            ),
        )!!
        assertEquals(AnnouncementAudience.PROPERTIES, a.audience)
        assertEquals("/api/v1/files/announcements/i1", a.imageUrl)
        assertEquals(5, a.recipientCount)
        assertEquals(3, a.viewCount)
    }

    @Test fun `tenant side viewed flag and sender`() {
        val p = AnnouncementsMapper.page(json("""[{"id":"a1","landlord":{"fullName":"Suresh"},"viewedAt":"2026-11-15T10:00:00Z"},{"id":"a2"}]"""), json("""{"page":1,"totalPages":2}"""), 1)
        assertTrue(p.hasMore)
        assertEquals("Suresh", p.items.first().fromName)
        assertTrue(p.items.first().viewed)
        assertFalse(p.items[1].viewed)
    }

    @Test fun `recipients - seen first`() {
        val v = AnnouncementsMapper.viewers(
            json("""{"recipients":[{"leaseId":"l1","tenant":{"fullName":"Ravi"},"viewedAt":null},{"leaseId":"l2","tenant":{"fullName":"Anu"},"property":{"title":"Green"},"place":"Flat 1","viewedAt":"2026-11-15T10:00:00Z"}]}"""),
        )
        assertEquals(listOf("Anu", "Ravi"), v.map { it.name })
        assertEquals("Green · Flat 1", v.first().place)
        assertFalse(v[1].seen)
    }

    @Test fun `rules, pickers and routes`() {
        assertFalse(AnnouncementRules.canSend("Hi", "Tank cleaning Sunday", AnnouncementAudience.ALL, emptySet(), emptySet()))
        // "Send to" not picked → can't send.
        assertFalse(AnnouncementRules.canSend("Water off", "Tank cleaning Sunday", null, emptySet(), emptySet()))
        assertTrue(AnnouncementRules.canSend("Water off", "Tank cleaning Sunday", AnnouncementAudience.ALL, emptySet(), emptySet()))
        assertFalse(AnnouncementRules.canSend("Water off", "Tank cleaning Sunday", AnnouncementAudience.TENANTS, emptySet(), emptySet()))
        assertTrue(AnnouncementRules.canSend("Water off", "Tank cleaning Sunday", AnnouncementAudience.PROPERTIES, setOf("p1"), emptySet()))
        val leases = listOf(
            Lease("l1", "ACTIVE", place = "Flat 1", tenantName = "Ravi", propertyId = "p1", propertyTitle = "Green"),
            Lease("l2", "ACTIVE", place = "Flat 2", tenantName = "Anu", propertyId = "p1", propertyTitle = "Green"),
        )
        assertEquals("2", AnnouncementRules.properties(leases).single().subtitle)
        assertEquals(listOf("l2"), AnnouncementRules.search(AnnouncementRules.tenants(leases), "anu").map { it.id })
        assertEquals(NavRoute.Announcements("a1"), DeepLinkRouter.route("/announcements/a1"))
        assertEquals(NavRoute.Announcements(null), DeepLinkRouter.route("/announcements"))
    }
}
