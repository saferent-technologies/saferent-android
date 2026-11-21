package com.saferenttechnologies.android.domain.model

import com.saferenttechnologies.android.data.inspection.InspectionMapper
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InspectionRulesTest {
    @Test fun `completing needs a date`() {
        val f = InspectionForm(complete = true)
        assertEquals(InspectionRules.Problem.DATE_REQUIRED, InspectionRules.validate(f)["inspectionDate"])
        assertTrue(InspectionRules.validate(f.copy(complete = false)).isEmpty())
    }

    @Test fun `empty rows are dropped, bad rows flagged by path`() {
        val f = InspectionForm(
            complete = false,
            furniture = listOf(InventoryItem("Sofa", "1"), InventoryItem("  ", "1")),
            repairs = listOf(RepairItem(item = "X"), RepairItem()),
        )
        assertEquals(1, InspectionRules.cleaned(f).furniture.size)
        assertEquals(1, InspectionRules.cleaned(f).repairs.size)
        assertEquals(InspectionRules.Problem.REPAIR_ITEM, InspectionRules.validate(f)["repairRequirements.0.item"])
        val bad = f.copy(furniture = listOf(InventoryItem("Bed", "1001")), repairs = emptyList(), keysHandedOver = "101")
        assertEquals(InspectionRules.Problem.QUANTITY, InspectionRules.validate(bad)["furnitureInventory.0.quantity"])
        assertEquals(InspectionRules.Problem.KEYS, InspectionRules.validate(bad)["keysHandedOver"])
    }

    @Test fun `open repair suggests needs repairs`() {
        val f = InspectionForm(repairs = listOf(RepairItem(item = "Tap leak")))
        assertEquals("NEEDS_REPAIRS", InspectionRules.withSuggestedResult(f).result)
        assertEquals("PASSED", InspectionRules.withSuggestedResult(f.copy(result = "PASSED")).result)
    }

    @Test fun `edit and acknowledge rules`() {
        val done = Inspection("i", status = "COMPLETED")
        assertTrue(InspectionRules.canEdit(done, canManage = true))
        assertTrue(InspectionRules.canAcknowledge(done, canManage = true))
        val acked = done.copy(tenantAcknowledgedAt = "2026-11-21T05:10:00.000Z")
        assertFalse(InspectionRules.canEdit(acked, canManage = true))
        assertFalse(InspectionRules.canAcknowledge(Inspection("i", status = "SCHEDULED"), canManage = true))
    }

    @Test fun `report round trip through the API shape`() {
        val json = Json.parseToJsonElement(
            """{"id":"a","type":"MOVE_IN","status":"COMPLETED","propertyCondition":"GOOD","plumbingCondition":"POOR",
               "furnitureInventory":[{"item":"Sofa","quantity":1,"condition":"GOOD"}],"applianceInventory":null,
               "repairRequirements":[{"item":"Kitchen tap leak","priority":"HIGH","estimatedCost":500,"responsibility":"OWNER","status":"OPEN"}],
               "handoverMeterReading":"10234.50","keysHandedOver":2,"notes":"ok",
               "photos":[{"id":"p1","category":"KITCHEN","url":"/api/v1/files/property-images/p1"}]}""",
        ) as JsonObject
        val i = InspectionMapper.inspection(json)!!
        assertEquals(Condition.POOR, i.conditions["plumbingCondition"])
        assertEquals(1, i.openRepairs)
        assertEquals("10234.5", i.meterReading)
        assertEquals(emptyList<InventoryItem>(), i.appliances)
        val body = InspectionMapper.body(InspectionForm.from(i).copy(inspectionDate = "2026-11-21T05:10:00.000Z"))
        assertEquals("COMPLETED", body["status"]!!.jsonPrimitive.content)
        assertEquals(1, (body["furnitureInventory"] as JsonArray).size)
        assertEquals("500", ((body["repairRequirements"] as JsonArray)[0] as JsonObject)["estimatedCost"]!!.jsonPrimitive.content)
        assertEquals(JsonNull, body["existingDamages"])
        // Draft keeps the form exactly.
        val form = InspectionForm.from(i)
        assertEquals(form, InspectionMapper.form(InspectionMapper.draftJson(form)))
    }

    @Test fun `iso time round trip`() {
        val ms = IsoTime.parse("2026-11-21T04:30:00.000Z")!!
        assertEquals("2026-11-21T04:30:00.000Z", IsoTime.format(ms))
        assertEquals(ms, IsoTime.parse("2026-11-21T04:30:00Z"))
    }
}
