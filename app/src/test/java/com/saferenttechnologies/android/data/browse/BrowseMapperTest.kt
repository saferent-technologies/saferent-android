package com.saferenttechnologies.android.data.browse

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowseMapperTest {
    private fun json(s: String) = Json.parseToJsonElement(s)

    @Test fun `page uses meta totalPages and total`() {
        val data = json(
            """[{"id":"8f1c","code":"SR-PROP-000010","title":"Royals Residency","propertyTypeLabel":"Apartment","locality":"Erragadda",
               "city":"Hyderabad","rentFrom":"18000.00","rentTo":"22000.00","availableCount":2,"completenessScore":86,
               "verificationStatus":"VERIFIED","ownerVerified":true,"coverImageId":"img1","distanceKm":1.4}]""",
        )
        val page = BrowseMapper.page(data, json("""{"page":1,"pageSize":12,"total":23,"totalPages":2}"""), 1)
        assertEquals(2, page.totalPages)
        assertEquals(23, page.total)
        assertTrue(page.hasMore)
        val l = page.items.single()
        assertTrue(l.verified && l.ownerVerified)
        assertEquals(1.4, l.distanceKm!!, 0.0)
        assertEquals("img1", l.coverImageId)
    }

    @Test fun `last page has no more, missing distance is null`() {
        val page = BrowseMapper.page(json("""[{"id":"a"}]"""), json("""{"page":2,"totalPages":2,"total":13}"""), 2)
        assertFalse(page.hasMore)
        assertNull(page.items.single().distanceKm)
    }

    @Test fun `location suggestion keeps the exact filters`() {
        val s = BrowseMapper.suggestion(
            json("""{"kind":"LOCALITY","name":"Hitech City","city":"Hyderabad","state":"Telangana","label":"Hitech City, Hyderabad",
                    "listingCount":9,"filters":{"locality":"Hitech City","city":"Hyderabad","state":"Telangana"}}""").jsonObject,
        )!!
        assertEquals("Hitech City", s.filter.locality)
        assertEquals("Hyderabad", s.filter.city)
        assertEquals("Area · Hyderabad, Telangana", s.subLabel)
        assertEquals(9, s.listingCount)
    }

    @Test fun `detail - units with breakdown, legend, my application`() {
        val d = BrowseMapper.detail(
            json(
                """{"property":{"id":"8f1c","title":"Royals Residency","structure":"MULTI_UNIT","latitude":"17.448312","longitude":"78.391504",
                    "units":[{"id":"u2","unitNumber":"303","floorNumber":3,"bedrooms":2,"bathrooms":2,"availabilityStatus":"AVAILABLE",
                      "attributes":{"bhkType":"2BHK"},"pricing":{"monthlyRent":"18000.00","securityDeposit":"50000.00",
                      "breakdown":{"monthlyTotal":"20000.00","moveInTotal":"70000.00","lines":[{"label":"Rent","amount":"18000.00","frequency":"MONTHLY"},
                      {"label":"Water","amount":null,"frequency":"MONTHLY"}]}}}],
                    "amenities":[{"key":"LIFT","label":"Lift","status":"YES"}],
                    "utilities":{"waterSupplyType":"MUNICIPAL","powerBackup":"NOT_DISCLOSED"},
                    "images":[{"id":"img1","category":"FRONT_VIEW","url":"/api/v1/files/property-images/img1"}],
                    "disclosureLegend":{"NOT_DISCLOSED":"Not disclosed by the landlord — ask before you rent."}},
                  "myApplication":{"id":"a1","status":"PENDING","unitId":"u2"}}""",
            ) as JsonObject,
        )!!
        val unit = d.units.single()
        assertTrue(unit.available)
        assertEquals("2BHK", unit.bhkType)
        assertEquals("70000.00", unit.breakdown!!.moveInTotal)
        assertFalse(unit.breakdown!!.lines[1].disclosed)
        assertEquals(17.448312, d.latitude!!, 0.0)
        assertEquals("Water supply type", d.utilities.first().label)
        assertEquals("PENDING", d.myApplication?.status)
        assertEquals(1, d.photos.size)
    }
}
