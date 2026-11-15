package com.saferenttechnologies.android.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowseFiltersTest {
    @Test fun `defaults - page, size, available only, newest`() {
        assertEquals(
            mapOf("page" to "1", "pageSize" to "12", "availableOnly" to "true", "sort" to "newest"),
            BrowseFilters().toQuery(1, 12),
        )
    }

    @Test fun `exact place wins over free text`() {
        val q = BrowseFilters(query = "hyd", place = PlaceFilter("Hitech City, Hyderabad", city = "Hyderabad", locality = "Hitech City", state = "Telangana"))
            .toQuery(1, 12)
        assertEquals("Hyderabad", q["city"])
        assertEquals("Hitech City", q["locality"])
        assertEquals("Telangana", q["state"])
        assertNull(q["q"])
    }

    @Test fun `free text`() = assertEquals("500018", BrowseFilters(query = " 500018 ").toQuery(2, 12)["q"])

    @Test fun `near me sends 6-decimal coordinates, radius and nearest`() {
        val q = BrowseFilters(nearMe = NearMe(17.44831234, 78.3915049, 5), sort = BrowseSort.NEAREST).toQuery(1, 12)
        assertEquals("17.448312", q["lat"])
        assertEquals("78.391505", q["lng"])
        assertEquals("5", q["radiusKm"])
        assertEquals("nearest", q["sort"])
    }

    @Test fun `nearest without a location falls back to newest (server would answer 400)`() =
        assertEquals("newest", BrowseFilters(sort = BrowseSort.NEAREST).toQuery(1, 12)["sort"])

    @Test fun `all filters`() {
        val q = BrowseFilters(
            listingType = "RENT", category = "RESIDENTIAL", propertyType = "APARTMENT", minRent = 10_000, maxRent = 25_000,
            bedrooms = 2, furnishing = "SEMI_FURNISHED", availableOnly = false, sort = BrowseSort.RENT_ASC,
        ).toQuery(1, 1)
        assertEquals("RENT", q["listingType"])
        assertEquals("APARTMENT", q["propertyType"])
        assertEquals("10000", q["minRent"])
        assertEquals("25000", q["maxRent"])
        assertEquals("2", q["bedrooms"])
        assertEquals("false", q["availableOnly"])
        assertEquals("rent_asc", q["sort"])
        assertEquals("1", q["pageSize"])
    }

    @Test fun `active count and clear all`() {
        val f = BrowseFilters(maxRent = 20_000, bedrooms = 2, availableOnly = false, query = "x")
        assertEquals(3, f.activeCount)
        assertFalse(f.isEmpty)
        assertTrue(f.cleared().isEmpty)
    }

    @Test fun `INR with Indian grouping`() {
        assertEquals("₹18,000", Money.inr("18000.00"))
        assertEquals("₹1,00,000", Money.inr("100000"))
        assertEquals("₹12,34,567", Money.inr("1234567"))
        assertEquals("₹999", Money.inr("999"))
        assertEquals("", Money.inr("abc"))
        assertEquals("₹18,000 – ₹22,000", Money.range("18000.00", "22000.00"))
        assertEquals("₹18,000", Money.range("18000.00", "18000.00"))
        assertEquals("1.4 km", Money.distance(1.4))
        assertEquals("350 m", Money.distance(0.35))
    }

    @Test fun `apply validation`() {
        val today = SimpleDate(2026, 11, 14)
        val ok = ApplyInput(unitId = "u1", proposedMoveIn = SimpleDate(2026, 12, 1), occupants = 2)
        assertNull(ApplyRules.validate(ok, today))
        assertNull(ApplyRules.validate(ok.copy(proposedMoveIn = today), today)) // today is allowed
        assertEquals(ApplyError.MOVE_IN_PAST, ApplyRules.validate(ok.copy(proposedMoveIn = SimpleDate(2026, 11, 13)), today))
        assertEquals(ApplyError.MOVE_IN_REQUIRED, ApplyRules.validate(ok.copy(proposedMoveIn = null), today))
        assertEquals(ApplyError.OCCUPANTS, ApplyRules.validate(ok.copy(occupants = 0), today))
        assertEquals(ApplyError.OCCUPANTS, ApplyRules.validate(ok.copy(occupants = 21), today))
        assertEquals(ApplyError.CHOOSE_HOME, ApplyRules.validate(ok.copy(unitId = null), today))
        assertNull(ApplyRules.validate(ok.copy(unitId = null, bedId = "b1"), today)) // PG
    }

    @Test fun `apply errors - 409 already applied, 412 REVIEW_REQUIRED, other 412 not available`() {
        assertTrue(ApplyFailure.from(AppError.Server(409, message = "Already applied")) is ApplyFailure.AlreadyApplied)
        assertTrue(ApplyFailure.from(AppError.Conflict("Already applied", versionConflict = false)) is ApplyFailure.AlreadyApplied)
        val review = ApplyFailure.from(AppError.Server(412, errorCode = "REVIEW_REQUIRED", message = "Review first"))
        assertTrue(review is ApplyFailure.ReviewRequired)
        assertEquals("Review first", review.message)
        assertTrue(ApplyFailure.from(AppError.Server(412, errorCode = "INVALID_STATE", message = "Gone")) is ApplyFailure.NotAvailable)
        assertTrue(ApplyFailure.from(AppError.Network()) is ApplyFailure.Other)
    }
}
