package com.saferenttechnologies.android.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OccupancyAfterCloseTest {
    private fun unit(id: String, code: String, status: String) = PropertyUnit(id = id, code = code, values = mapOf("availabilityStatus" to status))

    @Test fun `closed lease unlocks the flat in the change request`() {
        val draft = Property(id = "p", publicationStatus = "PUBLISHED", units = listOf(unit("c1", "F302", "OCCUPIED")))
        val live = Property(id = "p", publicationStatus = "PUBLISHED", units = listOf(unit("u1", "F302", "AVAILABLE")))
        val out = Occupancy.fromLive(draft, live)
        assertFalse(out.units.single().isOccupied)
        assertEquals("c1", out.units.single().id)
        assertFalse(out.hasTenants)
    }

    @Test fun `new lease locks it`() {
        val draft = Property(id = "p", units = listOf(unit("c1", "F302", "AVAILABLE")))
        val live = Property(id = "p", units = listOf(unit("u1", "F302", "OCCUPIED")))
        assertTrue(Occupancy.fromLive(draft, live).units.single().isOccupied)
    }

    @Test fun `bed freed after close unlocks the room`() {
        val draft = Property(id = "p", rooms = listOf(PropertyRoom("r1", "101", beds = listOf(Bed("b1", "A", "OCCUPIED")))))
        val live = Property(id = "p", rooms = listOf(PropertyRoom("x1", "101", beds = listOf(Bed("y1", "A", "AVAILABLE")))))
        assertFalse(Occupancy.fromLive(draft, live).rooms.single().isFullyOccupied)
    }

    @Test fun `flat missing on live keeps the draft status`() {
        val draft = Property(id = "p", units = listOf(unit("c1", "F302", "OCCUPIED")))
        assertTrue(Occupancy.fromLive(draft, Property(id = "p")).units.single().isOccupied)
    }
}
