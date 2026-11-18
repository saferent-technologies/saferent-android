package com.saferenttechnologies.android.data.homealerts

import com.saferenttechnologies.android.domain.model.GeoAddress
import com.saferenttechnologies.android.domain.model.HomeAlert
import com.saferenttechnologies.android.domain.model.HomeAlertRules
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeAlertMapperTest {
    @Test fun `alert from server`() {
        val a = HomeAlertMapper.alert(
            Json.parseToJsonElement("""{"alert":{"enabled":true,"latitude":17.44,"longitude":78.38,"areaLabel":"Madhapur, Hyderabad","radiusKm":10,"maxRent":20000,"propertyTypes":["APARTMENT","PG"]}}"""),
        )
        assertTrue(a.enabled)
        assertEquals(10, a.radiusKm)
        assertEquals(20000, a.maxRent)
        assertEquals(setOf("APARTMENT", "PG"), a.propertyTypes)
        assertTrue(a.hasArea)
    }

    @Test fun `empty or missing - off with defaults`() {
        val a = HomeAlertMapper.alert(JsonNull)
        assertFalse(a.enabled)
        assertEquals(HomeAlertRules.DEFAULT_RADIUS, a.radiusKm)
    }

    @Test fun `body - any budget is null, types sorted`() {
        val b = HomeAlertMapper.body(HomeAlert(enabled = true, latitude = 1.0, longitude = 2.0, propertyTypes = setOf("PG", "APARTMENT")))
        assertEquals(JsonNull, b["maxRent"])
        assertEquals(listOf("APARTMENT", "PG"), b["propertyTypes"]!!.jsonArray.map { it.jsonPrimitive.content })
    }

    @Test fun `rules`() {
        assertFalse(HomeAlertRules.canSave(HomeAlert(enabled = true)))
        assertTrue(HomeAlertRules.canSave(HomeAlert(enabled = false)))
        assertTrue(HomeAlertRules.canSave(HomeAlert(enabled = true, latitude = 1.0, longitude = 2.0)))
        assertEquals("Madhapur, Hyderabad", HomeAlertRules.areaLabel(GeoAddress(locality = "Madhapur", city = "Hyderabad")))
        assertEquals("Full line", HomeAlertRules.areaLabel(GeoAddress(formattedAddress = "Full line")))
    }

    @Test fun `place picked from our list is enough`() {
        val a = HomeAlert(enabled = true, locality = "Madhapur", city = "Hyderabad")
        assertTrue(HomeAlertRules.canSave(a))
        val b = HomeAlertMapper.body(a)
        assertEquals("Madhapur", b["locality"]!!.jsonPrimitive.content)
        assertTrue(HomeAlertRules.searchable("locality"))
        assertFalse(HomeAlertRules.searchable("STATE"))
    }
}
