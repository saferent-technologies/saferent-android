package com.saferenttechnologies.android.data.invitation

import com.saferenttechnologies.android.presentation.invitation.ordinal
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InvitationMapperTest {
    private fun json(s: String): JsonElement = Json.parseToJsonElement(s)
    private val now = 1_800_000_000_000L // 2027-01-15

    @Test fun `wrapped shape`() {
        val inv = InvitationMapper.parse(
            json(
                """{"invitation":{"id":"i1","code":"INV-9","status":"PENDING","recipientName":"Ravi Kumar","monthlyRent":"18000.00",
                   "securityDeposit":36000,"maintenanceCharges":"1500","startDate":"2027-02-01","rentDueDay":5,"noticePeriodDays":30,
                   "lockInMonths":11,"expiresAt":"2027-02-01T10:00:00.000Z"},
                   "property":{"id":"p1","title":"Green Heights","locality":"Madhapur","city":"Hyderabad","images":[{"id":"img1"}]},
                   "unit":{"unitNumber":"Flat 302"},"landlord":{"fullName":"Suresh Reddy","verified":true}}""",
            ),
            "tok",
            now,
        )!!
        assertEquals("PENDING", inv.status)
        assertEquals("p1", inv.propertyId)
        assertEquals("Madhapur, Hyderabad", inv.place)
        assertEquals("img1", inv.coverImageId)
        assertEquals("Flat 302", inv.target)
        assertEquals("Suresh Reddy", inv.landlordName)
        assertTrue(inv.landlordVerified)
        assertEquals(5, inv.rentDueDay)
        assertEquals("55500.00", inv.moveInTotal)
    }

    @Test fun `flat shape with pg bed and expired pending`() {
        val inv = InvitationMapper.parse(
            json("""{"id":"i2","status":"PENDING","expiresAt":"2026-01-01T00:00:00Z","property":{"id":"p2","title":"Sai PG"},"bed":{"bedNumber":"B","room":{"roomNumber":"2"}}}"""),
            "tok",
            now,
        )!!
        assertEquals("EXPIRED", inv.status)
        assertEquals("Room 2 · Bed B", inv.target)
        assertEquals("", inv.moveInTotal)
    }

    @Test fun `lease id from accept`() {
        assertEquals("le1", InvitationMapper.leaseId(json("""{"lease":{"id":"le1"}}""")))
        assertEquals("le2", InvitationMapper.leaseId(json("""{"leaseId":"le2"}""")))
        assertNull(InvitationMapper.leaseId(json("""{}""")))
    }

    @Test fun `ordinals`() {
        assertEquals(listOf("1st", "2nd", "3rd", "4th", "11th", "12th", "13th", "21st", "22nd", "31st"), listOf(1, 2, 3, 4, 11, 12, 13, 21, 22, 31).map(::ordinal))
    }
}
