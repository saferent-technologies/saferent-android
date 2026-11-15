package com.saferenttechnologies.android.domain.model

import com.saferenttechnologies.android.data.property.OptionalAnswers
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FormVisibilityTest {
    private val util = PropertyForms.utilities(null)
    private val policy = PropertyForms.policies(null)
    private fun shown(fields: List<FieldDef>, values: Map<String, String>) = FormVisibility.visible(fields, values).map { it.key }.toSet()

    @Test fun `electricity follow-ups`() {
        assertFalse("meterNumber" in shown(util, emptyMap()))
        assertTrue("meterNumber" in shown(util, mapOf("separateElectricityMeter" to "YES")))
        assertFalse("meterPaymentType" in shown(util, mapOf("separateElectricityMeter" to "NO")))
        assertTrue("electricityRatePerUnit" in shown(util, mapOf("electricityBillingType" to "FIXED")))
        assertFalse("electricityRatePerUnit" in shown(util, mapOf("electricityBillingType" to "INCLUDED_IN_RENT")))
    }

    @Test fun `water amount required for fixed and one-time, never sent when included`() {
        val fixed = mapOf("waterChargesType" to "ONE_TIME")
        assertEquals(FieldError.Required, FieldValidator.validate(util, fixed)["waterChargesAmount"])
        val included = mapOf("waterChargesType" to "INCLUDED_IN_RENT", "waterChargesAmount" to "300")
        assertEquals(mapOf("waterChargesType" to "INCLUDED_IN_RENT"), FormVisibility.clean(util, included))
        assertEquals(mapOf("waterIncludedInRent" to "YES"), PropertyForms.utilityExtras(included))
    }

    @Test fun `guests - overnight hidden when no guests, charge amount needs yes`() {
        assertFalse("overnightGuestsAllowed" in shown(policy, mapOf("guestsAllowed" to "NO")))
        assertTrue("overnightGuestsAllowed" in shown(policy, emptyMap()))
        // Max stay hangs under overnight, which hangs under guests → hidden with guests = NO.
        assertFalse("maximumGuestStayDays" in shown(policy, mapOf("guestsAllowed" to "NO", "overnightGuestsAllowed" to "YES")))
        assertEquals(FieldError.Required, FieldValidator.validate(policy, mapOf("guestChargesApplicable" to "YES"))["guestChargeAmount"])
    }

    @Test fun `agreement paid by only with charges, then required`() {
        val pricing = PropertyForms.pricing("RENT", null)
        assertFalse("agreementChargesPaidBy" in shown(pricing, emptyMap()))
        assertEquals(FieldError.Required, FieldValidator.validate(pricing, mapOf("monthlyRent" to "1", "agreementCharges" to "1500"))["agreementChargesPaidBy"])
        assertFalse(pricing.any { it.key == "rentDueDay" })
    }

    @Test fun `saved answers pre-fill`() {
        val u = OptionalAnswers.utilities(Json.parseToJsonElement("""{"utilities":{"id":"x","separateElectricityMeter":"YES","electricityRatePerUnit":"8.00","waterChargesAmount":"300.50","meterNumber":null}}"""))
        assertEquals(mapOf("separateElectricityMeter" to "YES", "electricityRatePerUnit" to "8", "waterChargesAmount" to "300.5"), u)
        val (p, rules) = OptionalAnswers.policies(Json.parseToJsonElement("""{"policy":{"guestsAllowed":"YES","additionalRules":[{"title":"No shoes inside","description":""}]}}"""))
        assertEquals("YES", p["guestsAllowed"])
        assertEquals(listOf("No shoes inside" to ""), rules)
        assertEquals(1, FormVisibility.unanswered(listOf(FieldDef("a", "a", FieldType.TEXT)), emptyMap()))
    }

    @Test fun `visitor timings after registration yes, cooking only for PG, no work from home`() {
        assertFalse("visitorTimingRestrictions" in shown(policy, emptyMap()))
        assertTrue("visitorTimingRestrictions" in shown(policy, mapOf("guestRegistrationRequired" to "YES")))
        assertFalse(PropertyForms.policies(null).any { it.key == "cookingAllowed" || it.key == "workFromHomeAllowed" })
        assertTrue(PropertyForms.policies(null, pg = true).any { it.key == "cookingAllowed" })
        assertTrue(PropertyForms.pricing("RENT", null).any { it.key == "depositUsableAsRent" && it.type == FieldType.BOOLEAN })
    }
}
