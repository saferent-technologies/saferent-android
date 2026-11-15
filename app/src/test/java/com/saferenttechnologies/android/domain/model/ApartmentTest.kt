package com.saferenttechnologies.android.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ApartmentTest {
    // ---------------- landlord wizard ----------------

    @Test fun `apartment common charges never carry rent, deposit or sale price`() {
        val keys = PropertyForms.pricingFor(apartment = true, listingType = "RENT", types = null).map { it.key }
        assertTrue(keys.none { it in PropertyForms.FLAT_PRICE_KEYS })
        assertTrue("lateFeePolicy" in keys && "noticePeriodDays" in keys && "agreementCharges" in keys && "rentDueDay" !in keys)
        assertTrue(PropertyForms.pricingFor(apartment = true, listingType = "SALE", types = null).isEmpty())
    }

    @Test fun `other types keep today's pricing step`() {
        val keys = PropertyForms.pricingFor(apartment = false, listingType = "RENT", types = null).map { it.key }
        assertTrue("monthlyRent" in keys)
    }

    @Test fun `flat form requires rent and deposit (RENT) or price (SALE)`() {
        val rent = PropertyForms.flatPricing("RENT")
        val errors = FieldValidator.validate(rent, mapOf("monthlyRent" to "18000"))
        assertEquals(FieldError.Required, errors["securityDeposit"])
        assertNull(errors["monthlyRent"])
        assertEquals(FieldError.Required, FieldValidator.validate(PropertyForms.flatPricing("SALE"), emptyMap())["salePrice"])
    }

    @Test fun `flat pricing body has only the price fields`() {
        val body = ApartmentRules.flatPricingBody(mapOf("monthlyRent" to " 18000 ", "securityDeposit" to "54000", "bhkType" to "2BHK", "salePrice" to "1"), "RENT")
        assertEquals(mapOf("monthlyRent" to "18000", "securityDeposit" to "54000"), body)
        assertEquals(mapOf("salePrice" to "6500000"), ApartmentRules.flatPricingBody(mapOf("salePrice" to "6500000", "monthlyRent" to "1"), "SALE"))
    }

    @Test fun `flats without rent are flagged and rent problems open the Flats step`() {
        val units = listOf(
            PropertyUnit("u1", unitNumber = "101", values = mapOf("monthlyRent" to "15000.00")),
            PropertyUnit("u2", unitNumber = "102"),
        )
        assertEquals(listOf("102"), ApartmentRules.flatsWithoutPrice(units, "RENT").map { it.unitNumber })
        assertEquals(WizardStep.UNITS, ApartmentRules.stepFor("Add the rent.", apartment = true))
        assertEquals(SubmitProblems.stepFor("Add the rent."), ApartmentRules.stepFor("Add the rent.", apartment = false))
    }

    @Test fun `flat amenity chip cycles yes - no - not disclosed - not set`() {
        assertEquals("YES", FlatAmenityCycle.next(null))
        assertEquals("NO", FlatAmenityCycle.next("YES"))
        assertEquals("NOT_DISCLOSED", FlatAmenityCycle.next("NO"))
        assertNull(FlatAmenityCycle.next("NOT_DISCLOSED"))
    }

    @Test fun `only APARTMENT is an apartment`() {
        assertTrue(isApartmentType("APARTMENT"))
        assertFalse(isApartmentType("INDEPENDENT_HOUSE"))
        assertFalse(isApartmentType(null))
    }

    // ---------------- tenant listing ----------------

    private val listing = ListingDetail(
        id = "p1",
        propertyType = "APARTMENT",
        units = listOf(
            UnitOption("u1", "101", availabilityStatus = "RESERVED", monthlyRent = "15000.00", imageIds = listOf("i101")),
            UnitOption("u2", "102", availabilityStatus = "AVAILABLE", monthlyRent = "22000.00", imageIds = listOf("i102")),
        ),
        photos = listOf(
            ListingPhoto("front", "FRONT_VIEW", "/f"),
            ListingPhoto("i101", "BEDROOM", "/a", unitId = "u1"),
            ListingPhoto("i102", "BEDROOM", "/b", unitId = "u2"),
        ),
    )

    @Test fun `default flat = first AVAILABLE, a kept flat stays`() {
        assertEquals("u2", FlatSelection.default(listing))
        assertEquals("u1", FlatSelection.default(listing, current = "u1"))
        assertEquals("u2", FlatSelection.default(listing, current = "gone"))
    }

    @Test fun `changing flat swaps photos - flat's own first, then the building`() {
        assertEquals(listOf("i102", "front"), FlatSelection.photos(listing, "u2").map { it.id })
        assertEquals(listOf("i101", "front"), FlatSelection.photos(listing, "u1").map { it.id })
    }

    @Test fun `non-apartment listings show every photo as before`() {
        val house = listing.copy(propertyType = "INDEPENDENT_HOUSE")
        assertFalse(house.isUnitPriced)
        assertEquals(3, FlatSelection.photos(house, "u2").size)
    }
}
