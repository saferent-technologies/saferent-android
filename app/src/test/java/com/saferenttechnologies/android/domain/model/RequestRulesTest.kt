package com.saferenttechnologies.android.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RequestRulesTest {
    @Test fun tenantMustPickWhoPaid() =
        assertEquals(RequestRules.ResolveProblem.NO_PAYER, RequestRules.resolveProblem(null, ""))

    @Test fun ownerMayLeaveWhoPaidEmpty() =
        assertNull(RequestRules.resolveProblem(null, "", payerRequired = false))

    @Test fun amountIsOptional() = assertNull(RequestRules.resolveProblem("TENANT", ""))

    @Test fun amountMustBePositive() =
        assertEquals(RequestRules.ResolveProblem.BAD_AMOUNT, RequestRules.resolveProblem("TENANT", "0"))

    @Test fun noCostMeansZero() {
        assertEquals(RequestRules.ResolveProblem.NO_COST_AMOUNT, RequestRules.resolveProblem("NO_COST", "500"))
        assertNull(RequestRules.resolveProblem("NO_COST", "0"))
        assertNull(RequestRules.resolveAmount("NO_COST", "0"))
    }

    @Test fun amountSent() = assertEquals(1200.0, RequestRules.resolveAmount("TENANT", "1,200")!!, 0.0)

    @Test fun costChecks() {
        assertTrue(RequestRules.cost("10.555")!!.isNaN())
        assertTrue(RequestRules.cost("10000001")!!.isNaN())
        assertNull(RequestRules.cost(""))
        assertEquals("1500", RequestRules.costText(1500.0))
    }

    @Test fun phones() {
        assertTrue(RequestRules.phoneOk("98765 43210"))
        assertTrue(RequestRules.phoneOk("+91 98765 43210"))
        assertFalse(RequestRules.phoneOk("12345"))
        assertEquals("+919876543210", RequestRules.phoneForApi("098765 43210"))
    }

    @Test fun workStartWithin60Days() {
        val today = SimpleDate(2026, 11, 21)
        assertTrue(RequestRules.workStartOk("2026-12-20", today))
        assertFalse(RequestRules.workStartOk("2027-02-01", today))
    }

    @Test fun twoVideosMax() {
        assertFalse(RequestRules.tooManyVideos(2))
        assertTrue(RequestRules.tooManyVideos(3))
    }
}
