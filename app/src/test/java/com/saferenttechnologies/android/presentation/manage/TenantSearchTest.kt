package com.saferenttechnologies.android.presentation.manage

import com.saferenttechnologies.android.domain.model.Lease
import org.junit.Assert.assertEquals
import org.junit.Test

class TenantSearchTest {
    private val list = listOf(
        Lease("1", "ACTIVE", place = "Flat 302", tenantName = "Ravi Kumar", tenantPhone = "+91 98765 43210", propertyTitle = "Green Heights"),
        Lease("2", "ACTIVE", place = "Room 2 · Bed B", tenantName = "Anil Reddy", propertyTitle = "Sai PG"),
    )

    @Test fun `matches name, home, flat and phone; every word must match`() {
        assertEquals(listOf("1"), TenantSearch.filter(list, "ravi").map { it.id })
        assertEquals(listOf("2"), TenantSearch.filter(list, "sai pg").map { it.id })
        assertEquals(listOf("1"), TenantSearch.filter(list, "302").map { it.id })
        assertEquals(listOf("1"), TenantSearch.filter(list, "98765").map { it.id })
        assertEquals(emptyList<String>(), TenantSearch.filter(list, "ravi sai").map { it.id })
        assertEquals(2, TenantSearch.filter(list, "  ").size)
    }
}
