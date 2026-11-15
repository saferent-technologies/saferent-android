package com.saferenttechnologies.android.data.lease

import com.saferenttechnologies.android.domain.model.LeaseExitRules
import com.saferenttechnologies.android.domain.model.ReviewDraft
import com.saferenttechnologies.android.domain.model.SimpleDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LeaseExitTest {
    private fun obj(s: String): JsonObject = Json.parseToJsonElement(s).jsonObject

    @Test fun `exit state with an open request from the other side`() {
        val s = LeaseExitMapper.state(
            obj(
                """{"leaseStatus":"ACTIVE","side":"LANDLORD","noticePeriodDays":30,"endDate":"2027-06-30T00:00:00.000Z","canRequest":false,
                   "open":{"id":"x1","requestedByRole":"TENANT","requestedBy":"Ravi Kumar","reason":"Job transfer to Bengaluru",
                   "moveOutDate":"2026-12-15T00:00:00.000Z","status":"PENDING","canRespond":true,"canCancel":false},
                   "history":[{"id":"h1","status":"DECLINED","responseNote":"Too soon"}]}""",
            ),
        )
        assertFalse(s.isTenant)
        assertEquals(30, s.noticePeriodDays)
        assertTrue(s.needsMyResponse)
        assertEquals("Ravi Kumar", s.open?.requestedBy)
        assertEquals("DECLINED", s.history.single().status)
        assertEquals("Too soon", s.history.single().responseNote)
    }

    @Test fun `my own pending request is not "needs my response"`() {
        val s = LeaseExitMapper.state(obj("""{"side":"TENANT","open":{"id":"x","status":"PENDING","canCancel":true},"history":[]}"""))
        assertTrue(s.isTenant)
        assertFalse(s.needsMyResponse)
        assertTrue(s.open!!.canCancel)
    }

    @Test fun `closed lease uses the completed date`() {
        val s = LeaseExitMapper.state(obj("""{"leaseStatus":"ENDED","side":"TENANT","open":null,"history":[{"id":"h","status":"COMPLETED","completedAt":"2026-12-15T00:00:00.000Z"}]}"""))
        assertTrue(s.isClosed)
        assertNull(s.open)
        assertEquals("2026-12-15T00:00:00.000Z", s.closedOn)
    }

    @Test fun `review json only sends rated categories`() {
        val j = LeaseExitMapper.reviewJson(ReviewDraft(mapOf("overall" to 4, "cleanliness" to 0), "  Nice  ", setOf("Quiet"))).toString()
        assertEquals("""{"ratings":{"overall":4},"text":"Nice","tags":["Quiet"]}""", j)
    }

    @Test fun `review form and accept result`() {
        val info = LeaseExitMapper.reviewInfo(Json.parseToJsonElement("""{"eligible":true,"myReview":null,"form":{"categories":[{"key":"overall","label":"Overall","required":true},{"key":"owner","label":"Landlord"}],"tags":["Quiet"]}}"""))!!
        assertTrue(info.eligible)
        assertFalse(info.hasMyReview)
        assertEquals(listOf("overall", "owner"), info.form.allCategories.map { it.key })
        assertTrue(LeaseExitMapper.closed(Json.parseToJsonElement("""{"ok":true,"closed":true}""")))
    }

    @Test fun `rules`() {
        val today = SimpleDate(2026, 11, 15)
        assertEquals(SimpleDate(2026, 12, 15), LeaseExitRules.defaultMoveOut(today, 30))
        assertEquals(SimpleDate(2026, 12, 15), LeaseExitRules.defaultMoveOut(today, null))
        assertTrue(LeaseExitRules.isShortNotice(SimpleDate(2026, 12, 1), today, 30))
        assertFalse(LeaseExitRules.isShortNotice(SimpleDate(2026, 12, 20), today, 30))
        assertFalse(LeaseExitRules.isShortNotice(SimpleDate(2026, 11, 16), today, null))
        assertFalse(LeaseExitRules.reasonOk("too short"))
        assertTrue(LeaseExitRules.reasonOk("Job transfer"))
        assertTrue(LeaseExitRules.declineOk("No way"))
        assertEquals(8, (1..10).fold(ReviewDraft()) { d, i -> d.toggleTag("t$i") }.tags.size)
    }
}

class PendingReviewTest {
    @Test fun `pending review mapper`() {
        val p = LeaseExitMapper.pending(obj("""{"lease":{"id":"le1","place":"Flat 302","securityDeposit":"36000"},"property":{"id":"p1","title":"Green Heights"},"landlord":{"fullName":"Suresh"},"endedAt":"2026-11-01T00:00:00Z"}"""))!!
        assertEquals("le1", p.leaseId)
        assertEquals("p1", p.propertyId)
        assertEquals("Suresh", p.otherName)
        assertEquals("36000", p.depositAmount)
        assertNull(LeaseExitMapper.pending(obj("""{"property":{"id":"p1"}}""")))
    }

    private fun obj(s: String): JsonObject = Json.parseToJsonElement(s).jsonObject
}
