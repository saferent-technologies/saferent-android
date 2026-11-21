package com.saferenttechnologies.android.data.society

import com.saferenttechnologies.android.data.remote.SafeRentJson
import com.saferenttechnologies.android.domain.model.Society
import com.saferenttechnologies.android.domain.model.SocietyApproval
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SocietyMapperTest {
    private fun json(s: String) = SafeRentJson.instance.parseToJsonElement(s)

    @Test fun readsOwnerData() {
        val d = SocietyMapper.data(
            json(
                """{"society":{"isSociety":true,"maintenanceAmount":9000,"maintenanceFrequency":"QUARTERLY","maintenanceMonthly":3000,"gateAccessMethods":["MYGATE"],
                "rules":[{"title":"No parking on the ramp","description":""}]},
                "parkingSlots":[{"id":"s1","slotNumber":"B1-23","unitId":"u1","unitLabel":"Flat 101","vehicleType":"CAR","covered":true}],
                "rulesDocument":{"documentId":"d1","sharedWithTenant":true}}""",
            ),
        )
        assertEquals("9000", d.society!!.maintenanceAmount)
        assertEquals(3000.0, d.society!!.maintenanceMonthly!!, 0.0)
        assertEquals("Flat 101", d.parkingSlots.single().unitLabel)
        assertTrue(d.rulesDocument!!.sharedWithTenant)
    }

    @Test fun bodyNeverSendsComputedFields() {
        val b = SocietyMapper.body(Society(isSociety = true, maintenanceAmount = "9000", maintenanceFrequency = "QUARTERLY", maintenanceMonthly = 3000.0, updatedAt = "x"))
        assertFalse(b.containsKey("maintenanceMonthly"))
        assertFalse(b.containsKey("updatedAt"))
        assertEquals(9000.0, b["maintenanceAmount"]!!.jsonPrimitive.content.toDouble(), 0.0)
        assertEquals(setOf("isSociety"), SocietyMapper.body(Society(isSociety = false)).keys)
    }

    @Test fun listing() {
        val l = SocietyMapper.listing(json("""{"maintenancePaidBy":"INCLUDED_IN_RENT","move":{"requiresPermission":true,"noticeDays":3},"parking":[{"vehicleType":"CAR","covered":true,"count":40}]}""") as JsonObject)!!
        assertEquals("INCLUDED_IN_RENT", l.maintenancePaidBy)
        assertEquals(3, l.move!!.noticeDays)
        assertEquals(40, l.parking.single().count)
        assertNull(SocietyMapper.listing(null))
    }

    @Test fun lease() {
        val l = SocietyMapper.lease(json("""{"society":null,"myParking":[{"id":"s1","slotNumber":"B1-23"}],"approval":{"status":"PENDING"},"can":{"editApproval":true}}"""))
        assertNull(l.society)
        assertEquals("B1-23", l.myParking.single().slotNumber)
        assertEquals(SocietyApproval("PENDING", null, ""), l.approval)
        assertTrue(l.canEditApproval)
    }
}
