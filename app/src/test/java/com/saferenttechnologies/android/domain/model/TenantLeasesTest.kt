package com.saferenttechnologies.android.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TenantLeasesTest {
    @Test fun `active on top, past newest first`() {
        val all = listOf(
            LeaseSummary("old", status = "ENDED", endedOn = "2025-03-01"),
            LeaseSummary("now", status = "ACTIVE"),
            LeaseSummary("mid", status = "TERMINATED", endedOn = "2026-01-10"),
        )
        val (active, past) = TenantLeases.split(all)
        assertEquals("now", active?.id)
        assertEquals(listOf("mid", "old"), past.map { it.id })
    }

    @Test fun `no active lease`() {
        val (active, past) = TenantLeases.split(listOf(LeaseSummary("a", status = "ENDED")))
        assertNull(active)
        assertEquals(1, past.size)
    }
}
