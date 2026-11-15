package com.saferenttechnologies.android.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InviteLocksTest {
    private val list = listOf(
        Invitation("i1", status = "PENDING", unitId = "u302"),
        Invitation("i2", status = "REVOKED", unitId = "u303"),
        Invitation("i3", status = "EXPIRED", unitId = "u304"),
        Invitation("i4", status = "pending", bedId = "b1"),
    )

    @Test fun `only pending invites lock a flat or bed`() {
        assertEquals(setOf("u302"), InviteLocks.units(list))
        assertEquals(setOf("b1"), InviteLocks.beds(list))
        assertTrue(InviteLocks.isLocked(list, "u302", null))
        assertFalse(InviteLocks.isLocked(list, "u303", null)) // revoked → can invite again
        assertTrue(InviteLocks.isLocked(list, null, "b1"))
        assertFalse(InviteLocks.isLocked(list, null, null)) // generic invite from the Invitations tab
    }
}
