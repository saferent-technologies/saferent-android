package com.saferenttechnologies.android.domain.model

import com.saferenttechnologies.android.data.push.PushPayload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VisitRulesTest {
    private val on = VisitSettings(enabled = true, weekly = listOf(WeeklyWindow(6, "10:00", "13:00")))

    @Test fun validSettingsHaveNoProblems() = assertTrue(VisitRules.validate(on).isEmpty())

    @Test fun enabledNeedsAWindow() =
        assertEquals(VisitRules.Problem.NO_WINDOWS, VisitRules.validate(on.copy(weekly = emptyList()))["weekly"])

    @Test fun disabledSkipsWindowChecks() =
        assertTrue(VisitRules.validate(on.copy(enabled = false, weekly = emptyList())).isEmpty())

    @Test fun windowMustBe30Minutes() =
        assertEquals(VisitRules.Problem.TOO_SHORT, VisitRules.validate(on.copy(weekly = listOf(WeeklyWindow(1, "10:00", "10:15"))))["weekly.0"])

    @Test fun overlapOnSameDayFlagged() {
        val s = on.copy(weekly = listOf(WeeklyWindow(1, "10:00", "12:00"), WeeklyWindow(1, "11:00", "13:00"), WeeklyWindow(2, "11:00", "13:00")))
        val p = VisitRules.validate(s)
        assertEquals(VisitRules.Problem.OVERLAP, p["weekly.1"])
        assertNull(p["weekly.2"])
    }

    @Test fun longInstructionsFlagged() =
        assertEquals(VisitRules.Problem.INSTRUCTIONS, VisitRules.validate(on.copy(instructions = "x".repeat(301)))["instructions"])

    @Test fun reasonLength() {
        assertFalse(VisitRules.reasonOk("no"))
        assertTrue(VisitRules.reasonOk("Busy that day"))
        assertFalse(VisitRules.reasonOk("x".repeat(301)))
    }

    @Test fun hostButtons() {
        val now = IsoTime.parse("2026-11-21T06:00:00Z")!!
        val future = Visit("a", status = "REQUESTED", scheduledAt = "2026-11-22T04:30:00Z")
        val past = Visit("b", status = "SCHEDULED", scheduledAt = "2026-11-20T04:30:00Z")
        assertTrue(VisitRules.canConfirm(future, now))
        assertFalse(VisitRules.canComplete(future.copy(status = "SCHEDULED"), now))
        assertTrue(VisitRules.canComplete(past, now))
        assertFalse(VisitRules.canConfirm(past.copy(status = "REQUESTED"), now))
    }

    @Test fun istTimeFormatting() = assertEquals("10:00 AM", VisitRules.time("2026-11-21T04:30:00Z"))

    @Test fun sections() {
        val items = listOf(Visit("1", status = "REQUESTED"), Visit("2", status = "SCHEDULED"), Visit("3", status = "CANCELLED"))
        assertEquals(listOf("1"), VisitRules.needsConfirmation(items).map { it.id })
        assertEquals(listOf("2"), VisitRules.upcoming(items).map { it.id })
        assertEquals(listOf("3"), VisitRules.past(items).map { it.id })
    }

    @Test fun pushLinks() {
        assertEquals("/visits/v1", PushPayload.visitLink(mapOf("type" to "VISIT", "visitId" to "v1")))
        assertEquals("/visits/v1?feedback=1", PushPayload.visitLink(mapOf("type" to "VISIT_FEEDBACK", "visitId" to "v1")))
        assertNull(PushPayload.visitLink(mapOf("type" to "RENT")))
    }

    @Test fun deepLinks() {
        assertEquals(NavRoute.Visit("v1", feedback = true), DeepLinkRouter.route("/visits/v1?feedback=1"))
        assertEquals(NavRoute.Visit("v2"), DeepLinkRouter.route("https://dev.saferenttechnologies.com/my/visits/v2"))
    }
}
