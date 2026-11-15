package com.saferenttechnologies.android.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoRequirementsTest {

    private fun unit(id: String, number: String, vararg values: Pair<String, String>) =
        PropertyUnit(id = id, unitNumber = number, values = mapOf(*values))

    @Test
    fun `2BHK needs Bedroom 1 and 2, any bedroom photo with that slot counts for one unit`() {
        val p = Property(id = "p", units = listOf(unit("u1", "302", "bhkType" to "2BHK")))
        assertEquals(
            listOf("LIVING_ROOM" to 1, "BEDROOM" to 1, "BEDROOM" to 2, "KITCHEN" to 1),
            PhotoRequirements.missing(p).map { it.category to it.slot },
        )

        val done = p.copy(
            images = listOf(
                PropertyImage("i1", "BEDROOM", slot = 1), PropertyImage("i2", "BEDROOM", slot = 2),
                PropertyImage("i3", "LIVING_ROOM", slot = 1), PropertyImage("i4", "KITCHEN", slot = 1),
            ),
        )
        assertTrue(PhotoRequirements.missing(done).isEmpty())
    }

    @Test
    fun `bathrooms and balconies count too, 0 means none`() {
        val p = Property(id = "p", units = listOf(unit("u1", "302", "bedrooms" to "2", "bathrooms" to "1", "balconies" to "0")))
        assertEquals(
            listOf("BEDROOM" to 1, "BEDROOM" to 2, "BATHROOM" to 1),
            PhotoRequirements.missing(p).map { it.category to it.slot },
        )
        val done = p.copy(
            images = listOf(
                PropertyImage("i1", "BEDROOM", slot = 1),
                PropertyImage("i2", "BEDROOM", slot = 2),
                PropertyImage("i3", "BEDROOM", slot = 3), // a bedroom photo doesn't count as a bathroom
            ),
        )
        assertEquals(listOf("BATHROOM" to 1), PhotoRequirements.missing(done).map { it.category to it.slot })
        val all = done.copy(images = done.images + PropertyImage("i4", "BATHROOM", slot = 1))
        assertTrue(PhotoRequirements.missing(all).isEmpty())
    }

    @Test
    fun `apartment - front view first, then every flat, empty flat needs one inside photo`() {
        val p = Property(id = "p", units = listOf(unit("u1", "302", "bedrooms" to "1", "bathrooms" to "1"), unit("u2", "303")))
        assertEquals(
            listOf("FRONT_VIEW" to "", "BEDROOM" to "302", "BATHROOM" to "302", "LIVING_ROOM" to "303"),
            PhotoRequirements.required(p, frontView = true).map { it.category to it.unitNumber },
        )
        val shot = p.copy(images = listOf(PropertyImage("f", "FRONT_VIEW"), PropertyImage("l", "LIVING_ROOM", slot = 1, unitId = "u2")))
        assertEquals(listOf("BEDROOM", "BATHROOM"), PhotoRequirements.missing(shot, frontView = true).map { it.category })
    }

    @Test
    fun `kitchen and living room counts are asked too`() {
        val p = Property(id = "p", units = listOf(unit("u1", "302", "bedrooms" to "1", "kitchens" to "1", "livingRooms" to "1")))
        assertEquals(listOf("LIVING_ROOM", "BEDROOM", "KITCHEN"), PhotoRequirements.required(p).map { it.category })
    }

    @Test
    fun `photos the server returns without a slot still count (flat not asked again)`() {
        val p = Property(
            id = "p",
            units = listOf(unit("u1", "302", "bedrooms" to "2"), unit("u2", "303", "bedrooms" to "1")),
            images = listOf(
                PropertyImage("a", "BEDROOM", unitId = "u1"),
                PropertyImage("b", "BEDROOM", unitId = "u1"),
            ),
        )
        assertEquals(listOf("u2" to 1), PhotoRequirements.missing(p).map { it.unitId to it.slot })
    }

    @Test
    fun `flat 302 shot alone, then flat 303 added - 302 is not asked again`() {
        val p = Property(
            id = "p",
            units = listOf(unit("u1", "302", "bedrooms" to "1"), unit("u2", "303", "bedrooms" to "1")),
            images = listOf(PropertyImage("a", "BEDROOM", slot = 1)), // saved before 303 existed (no flat tag)
        )
        assertEquals(listOf("303"), PhotoRequirements.missing(p).map { it.unitNumber })
    }

    @Test
    fun `apartment types are flats`() {
        val apt = PropertyTypeDef("APARTMENT", "Apartment", "RESIDENTIAL", PropertyStructure.MULTI_UNIT, enabled = true)
        val pg = PropertyTypeDef("PG", "PG / Hostel", "RESIDENTIAL", PropertyStructure.ROOMS_BEDS, enabled = true)
        assertTrue(apt.isFlat)
        assertTrue(!pg.isFlat)
    }

    @Test
    fun `bedrooms field wins, 1RK needs none`() {
        assertEquals(3, PhotoRequirements.bedrooms(unit("u", "1", "bedrooms" to "3", "bhkType" to "2BHK")))
        assertEquals(0, PhotoRequirements.bedrooms(unit("u", "1", "bhkType" to "1RK")))
    }

    @Test
    fun `several flats - photo must be tagged with that flat`() {
        val p = Property(
            id = "p",
            units = listOf(unit("u1", "101", "bedrooms" to "1"), unit("u2", "102", "bedrooms" to "1")),
            images = listOf(PropertyImage("i1", "BEDROOM", slot = 1, unitId = "u1")),
        )
        assertEquals(listOf("u2"), PhotoRequirements.missing(p).map { it.unitId })
    }

    @Test
    fun `flat number is digits only and parking becomes bike car both`() {
        val flat = FieldOverrides.adjust(FieldDef("unitNumber", "Flat number", FieldType.TEXT, required = true))
        assertTrue(flat.digitsOnly)
        assertEquals(FieldError.NotANumber, FieldValidator.validate(listOf(flat), mapOf("unitNumber" to "30A"))["unitNumber"])

        val parking = FieldOverrides.adjust(FieldDef("parkingAvailable", "Parking", FieldType.DISCLOSURE))
        assertEquals(FieldType.SELECT, parking.type)
        assertEquals(listOf("BIKE", "CAR", "BOTH", "NONE"), parking.options)

        val slots = FieldOverrides.adjust(FieldDef("parkingSlots", "Parking slots", FieldType.INTEGER))
        assertEquals(FieldType.INTEGER, slots.type) // counts stay numbers
    }
}
