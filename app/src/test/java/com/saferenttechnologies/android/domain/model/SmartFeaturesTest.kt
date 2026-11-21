package com.saferenttechnologies.android.domain.model

import com.saferenttechnologies.android.data.push.PushPayload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartFeaturesTest {
    @Test fun qrLinks() {
        assertEquals(QrLinks.Code("SR-PROP-000123", null), QrLinks.parse("https://app.saferenttechnologies.com/p/SR-PROP-000123"))
        assertEquals(QrLinks.Code("SR-PROP-000123", "SR-UNIT-000456"), QrLinks.parse("https://app.saferenttechnologies.com/p/SR-PROP-000123/u/SR-UNIT-000456"))
        assertNull(QrLinks.parse("https://evil.com/p/SR-PROP-000123"))
        assertNull(QrLinks.parse("http://app.saferenttechnologies.com/p/SR-PROP-000123"))
        assertNull(QrLinks.parse("https://app.saferenttechnologies.com/invite/abc"))
        assertNull(QrLinks.parse("WIFI:S:home;T:WPA;P:secret;;"))
        assertNull(QrLinks.parse("https://app.saferenttechnologies.com/p/SR-PROP-1/x/y"))
    }

    @Test fun deepLink() {
        assertEquals(NavRoute.Qr("SR-PROP-1", "SR-UNIT-2"), DeepLinkRouter.route("/p/SR-PROP-1/u/SR-UNIT-2"))
        assertEquals(NavRoute.Qr("SR-PROP-1", null), DeepLinkRouter.route("https://app.saferenttechnologies.com/p/SR-PROP-1"))
        assertTrue(DeepLinks.allowedFor(NavRoute.Qr("SR-PROP-1", null), isTenant = true))
    }

    @Test fun reminders() {
        assertEquals(listOf(5, 3, 1), ReminderRules.toggleBefore(listOf(3, 1), 5))
        assertEquals(listOf(3, 2, 1), ReminderRules.toggleBefore(listOf(5, 3, 1), 2))
        assertEquals(listOf(1), ReminderRules.toggleBefore(listOf(3, 1), 3))
        assertEquals(listOf(1, 7), ReminderRules.toggleOverdue(listOf(1, 3, 7), 3))
        assertEquals(listOf(1, 3, 7, 15), ReminderRules.toggleOverdue(listOf(1, 3, 7), 15))
        assertEquals(ReminderSettings(beforeDays = listOf(3), overdueDays = listOf(1, 7)), ReminderRules.clean(ReminderSettings(beforeDays = listOf(3, 3, 11), overdueDays = listOf(7, 1, 31))))
    }

    @Test fun comparison() {
        assertTrue(ComparisonRules.showChip("WORSE"))
        assertTrue(ComparisonRules.showChip("MISSING"))
        assertFalse(ComparisonRules.showChip("SAME"))
        assertFalse(ComparisonRules.showChip("NOT_COMPARABLE"))
        assertEquals("1000.5", ComparisonRules.number(1000.5))
        assertEquals("1450", ComparisonRules.number(1450.0))
        assertEquals(
            listOf("KITCHEN"),
            ComparisonRules.sharedCategories(
                listOf(InspectionPhoto("a", "KITCHEN", ""), InspectionPhoto("b", "BEDROOM", "")),
                listOf(InspectionPhoto("c", "kitchen", "")),
            ),
        )
    }

    @Test fun rentReminderPush() = assertEquals("/rent", PushPayload.complianceLink(mapOf("type" to "RENT_REMINDER", "rentPaymentId" to "r1")))
}
