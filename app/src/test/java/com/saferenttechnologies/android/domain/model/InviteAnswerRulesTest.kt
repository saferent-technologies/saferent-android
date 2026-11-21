package com.saferenttechnologies.android.domain.model

import com.saferenttechnologies.android.data.invitation.InvitationMapper
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InviteAnswerRulesTest {
    private val full = InviteRequirements(askDetails = true, companyName = "REQUIRED", references = 1, backgroundVerification = true, documents = listOf("ID_PROOF"))
    private val good = TenantAnswers(
        tenantType = "WORKING_PROFESSIONAL", employmentType = "SALARIED", companyName = "Acme Pvt Ltd", adults = "2", children = "1",
        hasPets = false, smokes = false, stayMonths = "11",
        references = listOf(TenantReference("Ravi Kumar", "Manager", "+91 98765 43210")), backgroundConsent = true,
    )

    @Test fun `everything asked must be filled`() {
        assertTrue(InviteAnswerRules.validate(full, good).isEmpty())
        val p = InviteAnswerRules.validate(full, InviteAnswerRules.start(full, null))
        assertEquals(InviteAnswerRules.Problem.REQUIRED, p["details.tenantType"])
        assertEquals(InviteAnswerRules.Problem.REQUIRED, p["details.hasPets"])
        assertEquals(InviteAnswerRules.Problem.LENGTH, p["details.references.0.name"])
        assertEquals(InviteAnswerRules.Problem.CONSENT, p["details.backgroundConsent"])
    }

    @Test fun `student needs no company name`() {
        val student = good.copy(tenantType = "STUDENT", employmentType = "STUDENT", companyName = "")
        assertFalse(InviteAnswerRules.validate(full, student).containsKey("details.companyName"))
        assertEquals(InviteAnswerRules.Problem.REQUIRED, InviteAnswerRules.validate(full, good.copy(companyName = ""))["details.companyName"])
    }

    @Test fun `body has only the asked parts`() {
        val onlyBasic = InviteRequirements(askDetails = true)
        val d = InviteAnswerRules.details(onlyBasic, good)
        assertFalse(d.containsKey("companyName"))
        assertFalse(d.containsKey("references"))
        assertFalse(d.containsKey("backgroundConsent"))
        val body = InvitationMapper.acceptBody(full, good)["details"] as JsonObject
        assertEquals("Acme Pvt Ltd", body["companyName"]!!.jsonPrimitive.content)
        assertEquals("+919876543210", ((body["references"] as JsonArray)[0] as JsonObject)["phone"]!!.jsonPrimitive.content)
        assertEquals("true", body["backgroundConsent"]!!.jsonPrimitive.content)
    }

    @Test fun `house rule warnings`() {
        val rules = HouseRules(petsAllowed = "NO", smokingAllowed = "NO", lockInMonths = 12)
        val w = InviteAnswerRules.warnings(full, good.copy(hasPets = true, smokes = true, stayMonths = "6"), rules)
        assertEquals(listOf(InviteAnswerRules.Warning.PETS, InviteAnswerRules.Warning.SMOKING, InviteAnswerRules.Warning.LOCK_IN), w)
    }

    @Test fun `prefill never carries references and makes exactly N cards`() {
        val a = InviteAnswerRules.start(full.copy(references = 2), good)
        assertEquals(2, a.references.size)
        assertTrue(a.references.all { it.name.isEmpty() })
        assertFalse(a.backgroundConsent)
    }

    @Test fun `requirements parse, off means nothing`() {
        assertNull(InvitationMapper.requirements(null))
        assertNull(InvitationMapper.requirements(Json.parseToJsonElement("""{"askDetails":false,"companyName":"OFF","references":0,"backgroundVerification":false,"documents":[]}""")))
        val r = InvitationMapper.requirements(Json.parseToJsonElement("""{"askDetails":true,"companyName":"REQUIRED","references":1,"backgroundVerification":true,"documents":["ID_PROOF","PAN"]}"""))!!
        assertEquals(listOf("ID_PROOF", "PAN"), r.documents)
    }
}
