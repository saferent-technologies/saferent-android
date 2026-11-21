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
        assertEquals(listOf("a"), FlatSelection.photos(d, "s1").map { it.id })
        // Unit without photos yet → building photos (front view) instead of an empty header.
        assertEquals(listOf("b"), FlatSelection.photos(d, "s2").map { it.id })
        val community = d.copy(propertyType = "VILLA", building = mapOf("villaListing" to "COMMUNITY"))
        assertTrue(community.isUnitPriced)
    }
}

class OccupiedLockTest {
    @org.junit.Test fun `occupied flat and full room are locked`() {
        org.junit.Assert.assertTrue(PropertyUnit("u1", values = mapOf("availabilityStatus" to "OCCUPIED")).isOccupied)
        org.junit.Assert.assertFalse(PropertyUnit("u2", values = mapOf("availabilityStatus" to "AVAILABLE")).isOccupied)
        val full = PropertyRoom("r1", beds = listOf(Bed("b1", "A", "OCCUPIED"), Bed("b2", "B", "OCCUPIED")))
        val half = PropertyRoom("r2", beds = listOf(Bed("b1", "A", "OCCUPIED"), Bed("b2", "B", "AVAILABLE")))
        org.junit.Assert.assertTrue(full.isFullyOccupied)
        org.junit.Assert.assertFalse(half.isFullyOccupied)
        org.junit.Assert.assertFalse(PropertyRoom("r3").isFullyOccupied)
    }
}

class ArchiveWithTenantsTest {
    @org.junit.Test fun `property with an occupied flat can't be archived`() {
        val can = PropertyPermissions(manage = true)
        val base = Property(id = "p1", publicationStatus = "PUBLISHED", activeStatus = "ACTIVE")
        org.junit.Assert.assertTrue(PropertyAction.ARCHIVE in PropertyActions.allowed(base, can))
        val occupied = base.copy(units = listOf(PropertyUnit("u1", values = mapOf("availabilityStatus" to "OCCUPIED"))))
        org.junit.Assert.assertFalse(PropertyAction.ARCHIVE in PropertyActions.allowed(occupied, can))
    }
}

class BuildingPhotosTest {
    @org.junit.Test fun `only building photos in the top gallery`() {
        val p = Property(
            id = "p",
            units = listOf(PropertyUnit("u1", imageIds = listOf("i3"))),
            images = listOf(
                PropertyImage("i1", "FRONT_VIEW"),
                PropertyImage("i2", "BEDROOM", unitId = "u1"),
                PropertyImage("i3", "KITCHEN"),
                PropertyImage("i4", "BEDROOM", roomId = "r1"),
            ),
        )
        org.junit.Assert.assertEquals(listOf("i1"), BuildingPhotos.of(p).map { it.id })
    }
}

class PropertyTabsTest {
    @org.junit.Test fun `tabs with 0 properties are hidden`() {
        val s = PropertySummary(total = 3, byStatus = mapOf("PUBLISHED" to 2, "DRAFT" to 1, "PENDING_VERIFICATION" to 0), unitsAvailable = 0)
        val tabs = s.visibleFilters(PropertyFilter.ALL)
        org.junit.Assert.assertTrue(PropertyFilter.LIVE in tabs && PropertyFilter.DRAFTS in tabs)
        org.junit.Assert.assertFalse(PropertyFilter.UNDER_VERIFICATION in tabs)
        org.junit.Assert.assertFalse(PropertyFilter.AVAILABLE_UNITS in tabs)
        // The tab you came from stays even at 0.
        org.junit.Assert.assertTrue(PropertyFilter.UNDER_VERIFICATION in s.visibleFilters(PropertyFilter.UNDER_VERIFICATION))
    }
}
