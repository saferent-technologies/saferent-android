package com.saferenttechnologies.android.presentation.explore

import com.saferenttechnologies.android.domain.model.BrowseFilters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QuickChipsTest {
    private val types = listOf(TypeOption("APARTMENT", "Apartment", "RESIDENTIAL"), TypeOption("PG_HOSTEL", "PG / Hostel", "RESIDENTIAL"))

    @Test fun `chips toggle filters on and off`() {
        val on = QuickChips.toggle(QuickChip.UNDER_20K, BrowseFilters(), types)
        assertEquals(20_000, on.maxRent)
        assertTrue(QuickChips.isActive(QuickChip.UNDER_20K, on, types))
        assertNull(QuickChips.toggle(QuickChip.UNDER_20K, on, types).maxRent)
    }

    @Test fun `type chips use the server's type keys`() {
        assertEquals("PG_HOSTEL", QuickChips.toggle(QuickChip.PG, BrowseFilters(), types).propertyType)
        assertEquals("INDEPENDENT_HOUSE", QuickChips.toggle(QuickChip.HOUSE, BrowseFilters(), types).propertyType)
        assertFalse(QuickChips.isActive(QuickChip.APARTMENT, BrowseFilters(propertyType = "PG_HOSTEL"), types))
    }
}
