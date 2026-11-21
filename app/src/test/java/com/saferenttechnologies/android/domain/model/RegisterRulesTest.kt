package com.saferenttechnologies.android.domain.model

import com.saferenttechnologies.android.data.inventory.RegisterMapper
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RegisterRulesTest {
    private val today = SimpleDate(2026, 11, 21)

    @Test fun `semi and fully furnished use the register`() {
        assertTrue(RegisterRules.furnished("SEMI_FURNISHED"))
        assertTrue(RegisterRules.furnished("fully_furnished"))
        assertFalse(RegisterRules.furnished("UNFURNISHED"))
        assertFalse(RegisterRules.furnished(null))
    }

    @Test fun `item checks`() {
        val ok = RegisterItemForm(unitId = "u1", name = "Refrigerator", category = "APPLIANCES", quantity = "1", purchaseDate = "2024-05-01")
        assertTrue(RegisterRules.validate(ok, today).isEmpty())
        assertEquals(RegisterRules.Problem.FUTURE_DATE, RegisterRules.validate(ok.copy(purchaseDate = "2026-11-22"), today)["purchaseDate"])
        assertEquals(RegisterRules.Problem.QUANTITY, RegisterRules.validate(ok.copy(quantity = "501"), today)["quantity"])
        assertEquals(RegisterRules.Problem.NAME, RegisterRules.validate(ok.copy(name = "A"), today)["name"])
    }

    @Test fun `repair checks`() {
        assertEquals(RegisterRules.Problem.DATE_REQUIRED, RegisterRules.validateRepair(RepairForm(description = "Gas refill"), today)["repairDate"])
        assertEquals(RegisterRules.Problem.COST, RegisterRules.validateRepair(RepairForm("2026-11-20", "Gas refill", cost = "10.123"), today)["cost"])
        assertTrue(RegisterRules.validateRepair(RepairForm("2026-11-20", "Gas refill", cost = "2500"), today).isEmpty())
    }

    @Test fun `patch sends only changes and the place as a pair`() {
        val item = RegisterItem("i1", unitId = "u1", name = "Sofa", category = "LIVING_ROOM", quantity = 1)
        val form = RegisterItemForm.from(item)
        assertTrue(RegisterRules.fields(form, item).isEmpty())
        assertEquals(mapOf("quantity" to 2), RegisterRules.fields(form.copy(quantity = "2"), item))
        val moved = RegisterRules.fields(form.copy(unitId = null, roomId = "r1"), item)
        assertEquals(setOf("unitId", "roomId"), moved.keys)
        assertNull(moved["unitId"])
    }

    @Test fun `inspection picks the place plus building and links rows`() {
        val items = listOf(
            RegisterItem("a", unitId = "u1", name = "Fridge", category = "APPLIANCES", condition = Condition.FAIR, workingStatus = WorkingStatus.WORKING),
            RegisterItem("b", unitId = "u2", name = "Bed", category = "BEDROOM"),
            RegisterItem("c", name = "Lift fan", category = "OTHER"),
        )
        val picked = RegisterRules.forInspection(items, "u1", null)
        assertEquals(listOf("a", "c"), picked.map { it.id })
        val (furniture, appliances) = RegisterRules.inspectionRows(picked)
        assertEquals("c", furniture.single().inventoryItemId)
        assertEquals("a", appliances.single().inventoryItemId)
        assertEquals(WorkingStatus.WORKING, appliances.single().workingStatus)
        // Already rated rows keep their rating; typed rows stay.
        val existing = listOf(InventoryItem("Fridge", "1", Condition.POOR, "a", WorkingStatus.NOT_WORKING), InventoryItem("Mat", "1"))
        val merged = RegisterRules.merge(existing, appliances)
        assertEquals(Condition.POOR, merged.first().condition)
        assertEquals(WorkingStatus.NOT_WORKING, merged.first().workingStatus)
        assertEquals("Mat", merged.last().item)
    }

    @Test fun `tenant reduced item parses without private fields`() {
        val json = Json.parseToJsonElement(
            """{"items":[{"id":"x","unitId":"u1","roomId":null,"name":"Refrigerator","category":"APPLIANCES","quantity":1,"brand":"LG","model":"GL-B201","condition":"GOOD","workingStatus":"WORKING","photos":[{"id":"p","url":"/api/v1/files/inventory-photos/p"}]}]}""",
        )
        val i = RegisterMapper.items(json).single()
        assertEquals("LG GL-B201", i.brandModel)
        assertEquals("", i.serialNumber)
        assertTrue(i.repairs.isEmpty())
    }
}
