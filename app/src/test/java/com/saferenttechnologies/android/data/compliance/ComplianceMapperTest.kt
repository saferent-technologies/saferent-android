package com.saferenttechnologies.android.data.compliance

import com.saferenttechnologies.android.data.remote.SafeRentJson
import com.saferenttechnologies.android.domain.model.PropertyCompliance
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ComplianceMapperTest {
    private fun json(s: String) = SafeRentJson.instance.parseToJsonElement(s)

    @Test fun readsPropertyCompliance() {
        val d = ComplianceMapper.data(
            json(
                """{"declaration":{"text":"I declare…","version":"v1","current":false},
                "compliance":{"propertyTaxStatus":"PAID","propertyTaxPaidYear":"2025-26","propertyTaxAmount":4200},
                "documents":[{"docType":"FIRE_NOC","label":"Fire NOC","whereApplicable":true,"uploaded":true,"status":"PENDING","expiryDate":"2027-01-31","expiryState":"VALID"}],
                "expiring":[{"documentId":"d1","docType":"FIRE_NOC","name":"Fire noc","expiryDate":"2026-12-10","expiryState":"EXPIRING"}]}""",
            ),
        )
        assertFalse(d.declaration.current)
        assertEquals("4200", d.compliance.propertyTaxAmount)
        assertEquals("VALID", d.documents.single().expiryState)
        assertEquals("EXPIRING", d.expiring.single().expiryState)
    }

    @Test fun bodySendsWholeObject() {
        val b = ComplianceMapper.body(PropertyCompliance(propertyTaxStatus = "PAID", propertyTaxPaidYear = "2025-26", propertyTaxAmount = "4,200"))
        assertEquals(4200.0, b["propertyTaxAmount"]!!.jsonPrimitive.content.toDouble(), 0.0)
        assertEquals(JsonNull, b["assessmentNumber"])
        assertTrue(b.containsKey("buildingApprovalDate"))
    }

    @Test fun readsLease() {
        val l = ComplianceMapper.lease(
            json("""{"agreement":{"type":"E_STAMP","registrationNumber":"IN-1","stampDutyAmount":1100},"policeVerification":{"status":"SUBMITTED","reference":"TS-1"},"can":{"editAgreement":false,"editPoliceVerification":true}}"""),
            "l1",
        )
        assertEquals("l1", l.leaseId)
        assertEquals("1100", l.agreement.stampDutyAmount)
        assertEquals("SUBMITTED", l.police.status)
        assertFalse(l.canEditAgreement)
        assertTrue(l.canEditPolice)
    }

    @Test fun leaseBodyClears() {
        val b = ComplianceMapper.leaseBody(mapOf("policeVerificationRef" to null, "stampDutyAmount" to 1100.0, "agreementType" to "NOTARIZED"))
        assertEquals(JsonNull, b["policeVerificationRef"])
        assertEquals("NOTARIZED", b["agreementType"]!!.jsonPrimitive.content)
    }
}
