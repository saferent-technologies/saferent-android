package com.saferenttechnologies.android.data.requests

import com.saferenttechnologies.android.domain.model.DeepLinkRouter
import com.saferenttechnologies.android.domain.model.NavRoute
import com.saferenttechnologies.android.domain.model.RequestRules
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RequestsMapperTest {
    private fun json(s: String) = Json.parseToJsonElement(s)

    @Test fun `options from the server`() {
        val o = RequestsMapper.options(
            json(
                """{"types":[{"key":"MAINTENANCE","label":"Maintenance / repair","categories":[{"key":"PLUMBING","label":"Plumbing"}]},{"key":"OTHER"}],
                   "priorities":["LOW","MEDIUM","HIGH","URGENT"],"landlordStatuses":[{"key":"OPEN","label":"Pending"},{"key":"IN_PROGRESS","label":"In progress"}]}""",
            ),
        )!!
        assertEquals(listOf("MAINTENANCE", "OTHER"), o.types.map { it.key })
        assertEquals("Plumbing", o.types.first().categories.single().label)
        assertEquals("OTHER", o.types[1].label)
        assertEquals("OPEN" to "Pending", o.landlordStatuses.first())
    }

    @Test fun `request with photos, timeline and can flags`() {
        val r = RequestsMapper.requestOf(
            json(
                """{"request":{"id":"r1","type":"maintenance","category":"PLUMBING","categoryLabel":"Plumbing","title":"Plumbing","description":"Kitchen tap leaking",
                   "status":"IN_PROGRESS","statusLabel":"In progress","property":{"title":"Green Heights","locality":"Madhapur","city":"Hyderabad"},"place":"Flat 302",
                   "raisedBy":{"fullName":"Ravi"},"photos":[{"id":"p1","url":"/api/v1/files/maintenance-photos/p1","updateId":null,"byTenant":true},{"id":"p2","updateId":"u1"}],
                   "timeline":[{"id":"e1","kind":"STATUS","by":{"role":"LANDLORD","fullName":"Suresh"},"toStatusLabel":"In progress","remark":"Plumber tomorrow","photoIds":["p2"],"at":"2026-11-15T10:00:00Z"}],
                   "can":{"remind":true,"resolve":true,"cancel":false}}}""",
            ),
        )!!
        assertEquals("MAINTENANCE", r.type)
        assertEquals("Madhapur, Hyderabad", r.propertyPlace)
        assertTrue(r.isOpen)
        assertEquals(null, r.photos.first().updateId)
        assertEquals("/api/v1/files/maintenance-photos/p2", r.photos[1].url)
        assertEquals("LANDLORD", r.timeline.single().byRole)
        assertEquals(listOf("p2"), r.timeline.single().photoIds)
        assertTrue(r.can.remind && r.can.resolve)
        assertFalse(r.can.cancel || r.can.update)
    }

    @Test fun `page meta and summary`() {
        val p = RequestsMapper.page(json("""[{"id":"a"},{"id":"b"}]"""), json("""{"page":1,"totalPages":2,"needsAction":3}"""), 1)
        assertTrue(p.hasMore)
        assertEquals(3, p.needsAction)
        assertEquals(2, RequestsMapper.summary(json("""{"needsAction":2,"landlordOpen":5,"myOpen":1}"""))!!.needsAction)
    }

    @Test fun `rules and routes`() {
        assertFalse(RequestRules.canSend("MAINTENANCE", null, "Kitchen tap leaking"))
        assertFalse(RequestRules.canSend("MAINTENANCE", "PLUMBING", "short"))
        assertTrue(RequestRules.canSend("MAINTENANCE", "PLUMBING", "Kitchen tap leaking"))
        assertFalse(RequestRules.canUpdate("OPEN", "OPEN", ""))
        assertTrue(RequestRules.canUpdate("OPEN", "IN_PROGRESS", ""))
        assertTrue(RequestRules.canUpdate("OPEN", null, "Plumber tomorrow"))
        assertEquals(NavRoute.RequestDetail("r1"), DeepLinkRouter.route("/maintenance/r1"))
        assertEquals(NavRoute.Maintenance, DeepLinkRouter.route("/maintenance"))
    }
}
