package com.saferenttechnologies.android.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OwnerRulesTest {
    @Test fun `phone rule allows spaces dashes brackets`() {
        assertTrue(OwnerRules.phoneOk("+91 98765-43210"))
        assertTrue(OwnerRules.phoneOk("(987) 654 3210"))
        assertFalse(OwnerRules.phoneOk("12345"))
        assertFalse(OwnerRules.phoneOk("0987654321"))
    }

    @Test fun `alternate number must differ from the mobile number`() {
        val d = OwnerDetails(phoneNumber = "+919876543210", alternatePhone = "98765 43210")
        assertEquals(OwnerRules.Problem.SAME_AS_MOBILE, OwnerRules.validate(d)["alternatePhone"])
    }

    @Test fun `only changed fields are sent, cleared ones as null`() {
        val before = OwnerDetails(city = "Hyderabad", preferredLanguage = "en")
        val after = before.copy(city = "", preferredLanguage = "te", preferredContactMethod = "WHATSAPP")
        val c = OwnerRules.changes(before, after)
        assertEquals(mapOf("city" to null, "preferredLanguage" to "te", "preferredContactMethod" to "WHATSAPP"), c)
    }

    @Test fun `incomplete until language, contact method and emergency phone are set`() {
        assertTrue(OwnerDetails().incomplete)
        assertFalse(OwnerDetails(preferredLanguage = "te", preferredContactMethod = "WHATSAPP", emergencyContactPhone = "9876543210").incomplete)
    }
}

class OwnershipRulesTest {
    private val joint = OwnershipRules.withOwnerType(Ownership(), OwnerType.JOINT)

    @Test fun `joint shares must add up to 100`() {
        val bad = joint.copy(ownerSharePercent = "60", coOwners = listOf(CoOwner(name = "Lakshmi", sharePercent = "50")))
        assertFalse(OwnershipRules.sharesOk(bad))
        assertEquals(OwnershipRules.Problem.SHARES_TOTAL, OwnershipRules.validate(bad)["coOwners"])
        val ok = bad.copy(ownerSharePercent = "50")
        assertTrue(OwnershipRules.sharesOk(ok))
        assertTrue(OwnershipRules.validate(ok).isEmpty())
    }

    @Test fun `two decimals at most`() {
        assertNull(OwnershipRules.percent("33.333"))
        assertEquals("33.33", OwnershipRules.percent("33.33")?.toPlainString())
    }

    @Test fun `individual is sole with no co-owners`() {
        val o = OwnershipRules.normalized(joint.copy(ownerType = OwnerType.INDIVIDUAL))
        assertEquals("SOLE", o.ownershipType)
        assertTrue(o.coOwners.isEmpty())
    }

    @Test fun `company needs a name and a representative`() {
        val c = Ownership(ownerType = OwnerType.COMPANY)
        val p = OwnershipRules.validate(c)
        assertEquals(OwnershipRules.Problem.COMPANY_NAME, p["companyName"])
        assertEquals(OwnershipRules.Problem.REQUIRED, p["representative"])
        val withRep = OwnershipRules.withOwnerType(Ownership(companyName = "Rao Estates Pvt Ltd"), OwnerType.COMPANY)
        assertTrue(withRep.representative != null)
    }

    @Test fun `power of attorney without the document needs an upload`() {
        val o = Ownership(representative = Representative(authorityType = AuthorityType.POWER_OF_ATTORNEY))
        assertTrue(OwnershipRules.needsPoa(o, uploaded = false))
        assertFalse(OwnershipRules.needsPoa(o, uploaded = true))
    }
}
