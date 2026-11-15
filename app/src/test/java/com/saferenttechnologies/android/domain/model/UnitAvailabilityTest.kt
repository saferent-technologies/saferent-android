package com.saferenttechnologies.android.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UnitAvailabilityTest {
    private val today = SimpleDate(2026, 11, 16)

    @Test fun `new flat - date defaults to today, status omitted`() {
        val v = UnitAvailability.prefill(emptyMap(), today)
        assertEquals("2026-11-16", v[UnitAvailability.FROM])
        assertEquals(mapOf("availableFrom" to "2026-11-16"), UnitAvailability.body(v, isNew = true, today = today))
    }

    @Test fun `edit - switch off sends AVAILABLE, past date becomes today`() {
        val v = UnitAvailability.prefill(mapOf("availabilityStatus" to "AVAILABLE", "availableFrom" to "2026-10-01"), today)
        assertEquals("2026-11-16", v[UnitAvailability.FROM])
        assertFalse(UnitAvailability.notReady(v))
        assertEquals("AVAILABLE", UnitAvailability.body(v, isNew = false, today = today)["availabilityStatus"])
    }

    @Test fun `edit - not-ready status turns the switch on and is sent`() {
        val v = UnitAvailability.prefill(mapOf("availabilityStatus" to "UNDER_MAINTENANCE", "availableFrom" to "2026-12-01"), today)
        assertTrue(UnitAvailability.notReady(v))
        assertEquals("2026-12-01", v[UnitAvailability.FROM])
        assertEquals(mapOf("availableFrom" to "2026-12-01", "availabilityStatus" to "UNDER_MAINTENANCE"), UnitAvailability.body(v, isNew = false, today = today))
    }

    @Test fun `occupied never sends availability`() {
        val v = UnitAvailability.prefill(mapOf("availabilityStatus" to "OCCUPIED"), today)
        assertTrue(UnitAvailability.body(v, isNew = false, today = today).isEmpty())
        assertTrue(UnitAvailability.validate(v, today).isEmpty())
    }

    @Test fun `validation - past date and switch without reason`() {
        val e = UnitAvailability.validate(mapOf("availableFrom" to "2026-11-15", "_notReady" to "true"), today)
        assertEquals(UnitAvailability.ERR_DATE, e["availableFrom"])
        assertEquals(UnitAvailability.ERR_REASON, e["availabilityStatus"])
    }

    @Test fun `form hides the two fields`() {
        val f = listOf(FieldDef("unitNumber", "unitNumber", FieldType.TEXT), FieldDef("availabilityStatus", "a", FieldType.SELECT), FieldDef("availableFrom", "b", FieldType.DATE))
        assertEquals(listOf("unitNumber"), UnitAvailability.formFields(f).map { it.key })
    }

    @Test fun `chip tone`() {
        assertEquals(UnitAvailability.Tone.NOW, UnitAvailability.tone("AVAILABLE", true, "", today))
        assertEquals(UnitAvailability.Tone.LATER, UnitAvailability.tone("AVAILABLE", null, "2026-11-30", today))
        assertEquals(UnitAvailability.Tone.NOW, UnitAvailability.tone("AVAILABLE", null, "2026-11-16", today))
        assertEquals(UnitAvailability.Tone.OCCUPIED, UnitAvailability.tone("OCCUPIED", false, "", today))
        assertEquals(UnitAvailability.Tone.OTHER, UnitAvailability.tone("RESERVED", false, "", today))
    }
}
