package com.saferenttechnologies.android.domain.model

import com.saferenttechnologies.android.data.push.PushPayload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SafetyRulesTest {
    private val today = SimpleDate(2026, 11, 21)

    @Test fun extinguisherNeedsExpiry() {
        assertEquals(SafetyRules.Problem.EXPIRY_REQUIRED, SafetyRules.validate(Safety(fireExtinguisher = true))["fireExtinguisherExpiry"])
        assertNull(SafetyRules.validate(Safety(fireExtinguisher = true, fireExtinguisherExpiry = "2027-03-01"))["fireExtinguisherExpiry"])
        assertTrue(SafetyRules.validate(Safety(fireExtinguisher = false)).isEmpty())
    }

    @Test fun countRange() =
        assertEquals(SafetyRules.Problem.COUNT, SafetyRules.validate(Safety(fireExtinguisher = true, fireExtinguisherExpiry = "2027-01-01", fireExtinguisherCount = 0))["fireExtinguisherCount"])

    @Test fun securityPhoneNeedsName() {
        val p = SafetyRules.validate(Safety(securityPhone = "98765 43210"))
        assertEquals(SafetyRules.Problem.SECURITY_NAME, p["securityName"])
        assertNull(p["securityPhone"])
    }

    @Test fun contactRows() {
        val p = SafetyRules.validate(Safety(emergencyContacts = listOf(EmergencyContact("Office", "12"), EmergencyContact("", ""), EmergencyContact("", "040 2345 6789"))))
        assertEquals(SafetyRules.Problem.PHONE, p["emergencyContacts.0.phone"])
        assertNull(p["emergencyContacts.1.label"])
        assertEquals(SafetyRules.Problem.CONTACT_LABEL, p["emergencyContacts.2.label"])
    }

    @Test fun cleanDropsDependentAnswers() {
        val c = SafetyRules.clean(
            Safety(
                fireExtinguisher = false, fireExtinguisherCount = 2, fireExtinguisherExpiry = "2027-01-01",
                smokeDetectors = null, smokeDetectorLocations = "Hall", emergencyContacts = listOf(EmergencyContact(), EmergencyContact(" Office ", "9876543210")),
            ),
        )
        assertNull(c.fireExtinguisherCount)
        assertNull(c.fireExtinguisherExpiry)
        assertEquals("", c.smokeDetectorLocations)
        assertEquals(listOf(EmergencyContact("Office", "9876543210")), c.emergencyContacts)
    }

    @Test fun phones() {
        assertEquals("+919876543210", SafetyRules.phoneForApi("98765 43210"))
        assertEquals("+919876543210", SafetyRules.phoneForApi("+91 98765 43210"))
        assertEquals("04023456789", SafetyRules.phoneForApi("040 2345 6789"))
        assertNull(SafetyRules.phoneForApi(""))
        assertFalse(SafetyRules.phoneOk("123"))
    }

    @Test fun extinguisherState() {
        assertEquals("EXPIRING", SafetyRules.extinguisherState("2026-12-11", today))
        assertEquals("VALID", SafetyRules.extinguisherState("2027-03-01", today))
        assertEquals("EXPIRED", SafetyRules.extinguisherState("2026-11-20", today))
        assertNull(SafetyRules.extinguisherState(null, today))
    }

    @Test fun directions() {
        assertEquals("https://www.google.com/maps/dir/?api=1&destination=17.4,78.4", SafetyRules.directionsUrl(SafetyPlace(latitude = 17.4, longitude = 78.4)))
        assertTrue(SafetyRules.directionsUrl(SafetyPlace(name = "City Hospital"))!!.contains("City+Hospital"))
        assertNull(SafetyRules.directionsUrl(SafetyPlace()))
    }

    @Test fun leaseGone() {
        assertTrue(SafetyRules.leaseGone(AppError.InvalidState("Emergency details are for your current rental.", errorCode = "LEASE_ENDED")))
        assertTrue(SafetyRules.leaseGone(AppError.Server(404)))
        assertFalse(SafetyRules.leaseGone(AppError.Network()))
    }

    @Test fun pushAndLinks() {
        assertEquals("/properties/p1/safety", PushPayload.safetyLink(mapOf("type" to "SAFETY_EXPIRY", "propertyId" to "p1")))
        assertNull(PushPayload.safetyLink(mapOf("type" to "VISIT")))
        assertEquals(NavRoute.Safety("p1"), DeepLinkRouter.route("/properties/p1/safety"))
        assertEquals(NavRoute.PropertyDetail("p1", null), DeepLinkRouter.route("/properties/p1"))
        assertFalse(DeepLinks.allowedFor(NavRoute.Safety("p1"), isTenant = true))
    }
}
