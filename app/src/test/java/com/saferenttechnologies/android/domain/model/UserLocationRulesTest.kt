package com.saferenttechnologies.android.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UserLocationRulesTest {
    private val hour = 60L * 60 * 1000

    @Test fun `coarse point is about 1 km`() {
        assertEquals(17.45, UserLocationRules.coarse(17.44812), 0.0)
        assertEquals(78.38, UserLocationRules.coarse(78.38149), 0.0)
    }

    @Test fun `same name and same point is never sent again`() {
        val last = SentArea(17.45, 78.38, 0L, "Madhapur", "Hyderabad")
        assertTrue(UserLocationRules.shouldSend(null, 17.45, 78.38, "Madhapur", "Hyderabad"))
        assertTrue(UserLocationRules.samePoint(last, 17.45, 78.38))
        assertFalse(UserLocationRules.shouldSend(last, 17.45, 78.38, "Madhapur", "Hyderabad"))
        // Next rounded point, same area, < 1 km → still the same place.
        assertFalse(UserLocationRules.shouldSend(last, 17.45, 78.39, "madhapur ", "Hyderabad"))
        // Different area name → send.
        assertTrue(UserLocationRules.shouldSend(last, 17.45, 78.39, "Kondapur", "Hyderabad"))
        // Same name but far (~5.5 km) → send.
        assertTrue(UserLocationRules.shouldSend(last, 17.50, 78.38, "Madhapur", "Hyderabad"))
    }

    @Test fun `GPS at most every 6 hours`() {
        assertTrue(UserLocationRules.shouldCheck(null, 0L))
        assertFalse(UserLocationRules.shouldCheck(0L, 5 * hour))
        assertTrue(UserLocationRules.shouldCheck(0L, 6 * hour))
    }
}
