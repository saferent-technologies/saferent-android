package com.saferenttechnologies.android.domain.model

import com.saferenttechnologies.android.data.push.PushPayload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SocietyRulesTest {
    private val on = Society(isSociety = true)

    @Test fun maintenanceNeedsFrequency() {
        assertEquals(SocietyRules.Problem.FREQUENCY, SocietyRules.validate(on.copy(maintenanceAmount = "9000"))["maintenanceFrequency"])
        assertNull(SocietyRules.validate(on.copy(maintenanceAmount = "9000", maintenanceFrequency = "QUARTERLY"))["maintenanceFrequency"])
    }

    @Test fun tenantPaysNeedsPayTo() =
        assertEquals(SocietyRules.Problem.PAY_TO, SocietyRules.validate(on.copy(maintenancePaidBy = "TENANT"))["maintenancePayTo"])

    @Test fun contactsNeedNames() {
        val p = SocietyRules.validate(on.copy(contactPhone = "9876543210", gateContactPhone = "9876543210", contactEmail = "bad"))
        assertEquals(SocietyRules.Problem.NAME_REQUIRED, p["contactName"])
        assertEquals(SocietyRules.Problem.GATE_NAME, p["gateContactName"])
        assertEquals(SocietyRules.Problem.EMAIL, p["contactEmail"])
    }

    @Test fun notASocietyHasNoChecks() = assertTrue(SocietyRules.validate(Society(isSociety = false, maintenanceAmount = "9000")).isEmpty())

    @Test fun cleanDropsDependents() {
        val c = SocietyRules.clean(
            on.copy(
                maintenancePaidBy = "OWNER", maintenancePayTo = "SOCIETY", moveRequiresPermission = false, moveNoticeDays = 3, moveChargeAmount = "2000",
                rules = listOf(SocietyRule(" No parking on the ramp ", ""), SocietyRule()), gateAccessMethods = listOf("MYGATE", "MYGATE"), maintenanceMonthly = 3000.0,
            ),
        )
        assertNull(c.maintenancePayTo)
        assertNull(c.moveNoticeDays)
        assertEquals("", c.moveChargeAmount)
        assertEquals(listOf(SocietyRule("No parking on the ramp", "")), c.rules)
        assertEquals(listOf("MYGATE"), c.gateAccessMethods)
        assertNull(c.maintenanceMonthly)
        assertEquals(Society(isSociety = false), SocietyRules.clean(Society(isSociety = false, associationName = "X")))
    }

    @Test fun monthly() {
        assertEquals(3000.0, SocietyRules.monthly(9000.0, "QUARTERLY")!!, 0.0)
        assertEquals(750.0, SocietyRules.monthly(9000.0, "YEARLY")!!, 0.0)
        assertNull(SocietyRules.monthly(9000.0, null))
    }

    @Test fun landIsNotASociety() {
        assertFalse(SocietyRules.applies("LAND"))
        assertTrue(SocietyRules.applies("APARTMENT"))
    }

    @Test fun slots() {
        val old = ParkingSlot("s1", "B1-23", null, null, "CAR", true, "")
        assertEquals(mapOf("unitId" to "u1"), SocietyRules.slotChanges(old, old.copy(unitId = "u1", unitLabel = "Flat 101")))
        assertEquals(mapOf<String, Any?>("unitId" to null), SocietyRules.slotChanges(old.copy(unitId = "u1"), old))
        assertEquals(SocietyRules.Problem.SLOT_NUMBER, SocietyRules.validateSlot(ParkingSlot())["slotNumber"])
        val g = SocietyRules.grouped(listOf(old, ParkingSlot("s2", "A1", "u1", "Flat 101")))
        assertEquals("u1", g.first().first)
        assertNull(g.last().first)
    }

    @Test fun approval() {
        assertEquals(SocietyRules.Problem.NOTE_REQUIRED, SocietyRules.validateApproval(SocietyApproval("REJECTED", "2026-11-20", ""))["note"])
        assertEquals(SocietyRules.Problem.DATE_REQUIRED, SocietyRules.validateApproval(SocietyApproval("APPROVED", null, ""))["date"])
        assertTrue(SocietyRules.validateApproval(SocietyApproval("PENDING")).isEmpty())
    }

    @Test fun push() = assertEquals("/leases/l1", PushPayload.complianceLink(mapOf("type" to "SOCIETY_APPROVAL", "leaseId" to "l1")))
}
