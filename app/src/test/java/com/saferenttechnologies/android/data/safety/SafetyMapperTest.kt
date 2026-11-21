package com.saferenttechnologies.android.data.safety

import com.saferenttechnologies.android.data.remote.SafeRentJson
import com.saferenttechnologies.android.domain.model.EmergencyContact
import com.saferenttechnologies.android.domain.model.Safety
import com.saferenttechnologies.android.domain.model.SafetyPlace
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SafetyMapperTest {
    private fun json(s: String) = SafeRentJson.instance.parseToJsonElement(s)

    @Test fun readsOwnerData() {
        val d = SafetyMapper.data(
            json(
                """{"safety":{"fireExtinguisher":true,"fireExtinguisherCount":2,"fireExtinguisherExpiry":"2027-03-01","smokeDetectors":null,
                "emergencyContacts":[{"label":"Office","phone":"+911234567890"}],"nearestHospital":{"name":"City","distanceKm":1.2}},
                "extinguisherState":"VALID","score":70,"certificates":[{"docType":"FIRE_NOC","uploaded":true,"status":"APPROVED"}]}""",
            ),
        )
        assertEquals(true, d.safety.fireExtinguisher)
        assertNull(d.safety.smokeDetectors)
        assertEquals(1.2, d.safety.nearestHospital.distanceKm!!, 0.0)
        assertEquals(70, d.score)
        assertEquals(listOf("STRUCTURAL_SAFETY_CERTIFICATE", "FIRE_NOC", "OCCUPANCY_CERTIFICATE"), d.certificates.map { it.docType })
        assertEquals("APPROVED", d.certificates[1].status)
    }

    @Test fun bodyIsWholeObject() {
        val b = SafetyMapper.body(Safety(fireExtinguisher = false, fireExtinguisherCount = 3, emergencyContacts = listOf(EmergencyContact("Office", "98765 43210")), nearestPoliceStation = SafetyPlace()))
        assertTrue(b.containsKey("smokeDetectors"))
        assertEquals(JsonNull, b["fireExtinguisherCount"])
        assertEquals(JsonNull, b["nearestPoliceStation"])
        assertEquals("+919876543210", ((b["emergencyContacts"] as kotlinx.serialization.json.JsonArray)[0] as JsonObject)["phone"]!!.jsonPrimitive.content)
    }

    @Test fun listingHidesEmpty() {
        assertNull(SafetyMapper.listing(json("""{"fireExtinguisher":null}""") as JsonObject))
        val l = SafetyMapper.listing(json("""{"fireExtinguisher":true,"fireExtinguisherState":"EXPIRED","security":"24X7","certificates":[{"docType":"FIRE_NOC","verified":true},{"docType":"OCCUPANCY_CERTIFICATE","verified":false}]}""") as JsonObject)!!
        assertEquals("EXPIRED", l.fireExtinguisherState)
        assertEquals(listOf("FIRE_NOC"), l.verifiedCertificates)
    }

    @Test fun emergencyFallsBackToHelplines() {
        val e = SafetyMapper.emergency(json("""{"property":{"title":"Green Residency"},"landlord":{"fullName":"Ravi","phoneNumber":"+91"},"helplines":[],"emergencyStaircase":true}"""))
        assertEquals("Green Residency", e.propertyTitle)
        assertEquals("112", e.helplines.first().phone)
        assertEquals(true, e.emergencyStaircase)
        assertNull(e.hospital)
    }
}
