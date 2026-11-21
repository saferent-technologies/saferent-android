package com.saferenttechnologies.android.domain.model

import com.saferenttechnologies.android.data.push.PushPayload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ComplianceRulesTest {
    private val today = SimpleDate(2026, 11, 21)

    @Test fun financialYears() {
        assertTrue(ComplianceRules.fyOk("2025-26"))
        assertTrue(ComplianceRules.fyOk("1999-00"))
        assertFalse(ComplianceRules.fyOk("2025-27"))
        assertFalse(ComplianceRules.fyOk("2025"))
        assertEquals(listOf("2026-27", "2025-26"), ComplianceRules.financialYears(today, 2))
        assertEquals("2025-26", ComplianceRules.financialYears(SimpleDate(2026, 3, 31), 1).first())
    }

    @Test fun paidNeedsYear() {
        assertEquals(ComplianceRules.Problem.YEAR_REQUIRED, ComplianceRules.validate(PropertyCompliance(propertyTaxStatus = "PAID"), today)["propertyTaxPaidYear"])
        assertTrue(ComplianceRules.validate(PropertyCompliance(propertyTaxStatus = "PAID", propertyTaxPaidYear = "2025-26"), today).isEmpty())
    }

    @Test fun cleanDropsYearWhenNotPaid() {
        val c = ComplianceRules.clean(PropertyCompliance(propertyTaxStatus = "PENDING", propertyTaxPaidYear = "2025-26", propertyTaxAmount = "4200"))
        assertNull(c.propertyTaxPaidYear)
        assertEquals("", c.propertyTaxAmount)
    }

    @Test fun approval() {
        assertEquals(ComplianceRules.Problem.APPROVAL_NUMBER, ComplianceRules.validate(PropertyCompliance(buildingApprovalAuthority = "GHMC"), today)["buildingApprovalNumber"])
        assertEquals(ComplianceRules.Problem.FUTURE_DATE, ComplianceRules.validate(PropertyCompliance(buildingApprovalNumber = "BP/1", buildingApprovalDate = "2026-12-01"), today)["buildingApprovalDate"])
    }

    @Test fun leaseChecks() {
        val base = LeaseCompliance(canEditAgreement = true, canEditPolice = true)
        assertEquals(ComplianceRules.Problem.AGREEMENT_NUMBER, ComplianceRules.validateLease(base.copy(agreement = LeaseAgreement(type = "E_STAMP")), today)["agreementRegistrationNumber"])
        assertEquals(ComplianceRules.Problem.VERIFIED_DATE, ComplianceRules.validateLease(base.copy(police = PoliceVerification("VERIFIED")), today)["policeVerificationVerifiedOn"])
        assertEquals(
            ComplianceRules.Problem.VERIFIED_BEFORE,
            ComplianceRules.validateLease(base.copy(police = PoliceVerification("VERIFIED", submittedOn = "2026-11-10", verifiedOn = "2026-11-05")), today)["policeVerificationVerifiedOn"],
        )
        // Tenant can't edit the agreement → not checked.
        assertTrue(ComplianceRules.validateLease(LeaseCompliance(agreement = LeaseAgreement(type = "E_STAMP")), today).isEmpty())
    }

    @Test fun leaseChangesOnlyWhatChangedAndAllowed() {
        val old = LeaseCompliance(canEditAgreement = false, canEditPolice = true)
        val new = old.copy(agreement = LeaseAgreement(type = "REGISTERED"), police = PoliceVerification("SUBMITTED", reference = " TS-1 ", submittedOn = "2026-11-20"))
        assertEquals(
            mapOf("policeVerificationStatus" to "SUBMITTED", "policeVerificationRef" to "TS-1", "policeVerificationSubmittedOn" to "2026-11-20"),
            ComplianceRules.leaseChanges(old, new),
        )
        assertTrue(ComplianceRules.leaseChanges(old, old).isEmpty())
    }

    @Test fun declarationProblem() = assertTrue(ComplianceRules.isDeclarationProblem("Confirm the ownership declaration."))

    @Test fun pushAndLinks() {
        assertEquals("/properties/p1/compliance", PushPayload.complianceLink(mapOf("type" to "DOCUMENT_EXPIRY", "propertyId" to "p1", "documentId" to "d1")))
        assertEquals("/leases/l1", PushPayload.complianceLink(mapOf("type" to "LEASE_COMPLIANCE", "leaseId" to "l1")))
        assertEquals(NavRoute.Compliance("p1"), DeepLinkRouter.route("/properties/p1/compliance"))
        assertFalse(DeepLinks.allowedFor(NavRoute.Compliance("p1"), isTenant = true))
    }
}
