package com.saferenttechnologies.android.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UnitPricingTest {
    @Test fun `unit-priced types`() {
        assertTrue(UnitPricing.isUnitPriced("APARTMENT", null))
        assertTrue(UnitPricing.isUnitPriced("INDEPENDENT_BUILDING", null))
        assertTrue(UnitPricing.isUnitPriced("COMMERCIAL_COMPLEX", null))
        assertTrue(UnitPricing.isUnitPriced("VILLA", "COMMUNITY"))
        assertFalse(UnitPricing.isUnitPriced("VILLA", "STANDALONE"))
        assertFalse(UnitPricing.isUnitPriced("VILLA", null))
        assertFalse(UnitPricing.isUnitPriced("INDIVIDUAL_HOUSE", null))
        assertFalse(UnitPricing.isUnitPriced("SHOP", null))
    }

    @Test fun `PG and hostel are room-priced, the building never carries a price`() {
        assertTrue(UnitPricing.isRoomPriced(PropertyStructure.ROOMS_BEDS))
        assertFalse(UnitPricing.isRoomPriced(PropertyStructure.MULTI_UNIT))
        assertTrue(UnitPricing.hidesBuildingPrice("PG", null, PropertyStructure.ROOMS_BEDS))
        assertTrue(UnitPricing.hidesBuildingPrice("COMMERCIAL_COMPLEX", null, PropertyStructure.MULTI_UNIT))
        assertFalse(UnitPricing.hidesBuildingPrice("INDIVIDUAL_HOUSE", null, PropertyStructure.SINGLE_UNIT))
    }

    @Test fun `unit word - server label first, else by type`() {
        assertEquals("Portion", UnitPricing.unitWord("INDEPENDENT_BUILDING"))
        assertEquals("Shop", UnitPricing.unitWord("COMMERCIAL_COMPLEX"))
        assertEquals("Villa", UnitPricing.unitWord("VILLA"))
        assertEquals("Room", UnitPricing.unitWord("HOSTEL", structure = PropertyStructure.ROOMS_BEDS))
        assertEquals("Floor", UnitPricing.unitWord("INDEPENDENT_BUILDING", unitLabel = "Floor"))
    }

    @Test fun `common charges never send the price for these types`() {
        val keys = PropertyForms.pricingFor(apartment = UnitPricing.hidesBuildingPrice("PG", null, PropertyStructure.ROOMS_BEDS), listingType = "RENT", types = null)
            .map { it.key }
        assertTrue(keys.none { it in PropertyForms.FLAT_PRICE_KEYS })
    }

    @Test fun `rooms without rent per bed or bed rent are flagged`() {
        val rooms = listOf(
            PropertyRoom("r1", "101", values = mapOf("rentPerBed" to "8000")),
            PropertyRoom("r2", "102", beds = listOf(Bed("b1", "B1", rent = "9000"))),
            PropertyRoom("r3", "103"),
        )
        assertEquals(listOf("103"), ApartmentRules.roomsWithoutPrice(rooms).map { it.roomNumber })
    }

    @Test fun `PG room photo - any photo of that room counts`() {
        val p = Property(
            id = "p",
            structure = PropertyStructure.ROOMS_BEDS,
            rooms = listOf(PropertyRoom("r1", "101"), PropertyRoom("r2", "102")),
            images = listOf(PropertyImage("i1", "OTHER", roomId = "r1")),
        )
        assertEquals(listOf("r2"), PhotoRequirements.missing(p).map { it.roomId })
    }

    @Test fun `listing - selecting a unit swaps photos for any unit-priced type`() {
        val d = ListingDetail(
            id = "p1",
            propertyType = "COMMERCIAL_COMPLEX",
            units = listOf(UnitOption("s1", "1", availabilityStatus = "AVAILABLE", imageIds = listOf("a")), UnitOption("s2", "2", availabilityStatus = "AVAILABLE")),
            photos = listOf(ListingPhoto("b", "FRONT_VIEW", "/b"), ListingPhoto("a", "OTHER", "/a", unitId = "s1")),
        )
        assertTrue(d.isUnitPriced)
        assertEquals("Shop", d.unitWord)
        assertEquals(listOf("a", "b"), FlatSelection.photos(d, "s1").map { it.id })
        assertEquals(listOf("b"), FlatSelection.photos(d, "s2").map { it.id })
        val community = d.copy(propertyType = "VILLA", building = mapOf("villaListing" to "COMMUNITY"))
        assertTrue(community.isUnitPriced)
    }
}
