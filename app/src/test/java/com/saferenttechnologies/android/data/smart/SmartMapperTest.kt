package com.saferenttechnologies.android.data.smart

import com.saferenttechnologies.android.data.remote.SafeRentJson
import com.saferenttechnologies.android.domain.model.ReminderSettings
import kotlinx.serialization.json.JsonArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartMapperTest {
    private fun json(s: String) = SafeRentJson.instance.parseToJsonElement(s)

    @Test fun notReady() {
        val c = SmartMapper.comparison(json("""{"ready":false,"missing":["MOVE_OUT"],"moveIn":{"id":"i1","status":"COMPLETED","photos":[]},"moveOut":null,"comparison":null}"""))
        assertFalse(c.ready)
        assertEquals(listOf("MOVE_OUT"), c.missing)
        assertNull(c.moveOut)
    }

    @Test fun ready() {
        val c = SmartMapper.comparison(
            json(
                """{"ready":true,"missing":[],"moveIn":{"id":"i1","photos":[{"id":"p1","category":"kitchen"}]},"moveOut":{"id":"i2","photos":[]},
                "comparison":{"areas":[{"key":"wallCondition","label":"Walls","before":"GOOD","after":"POOR","change":"WORSE"}],
                "furniture":[{"item":"Chair","before":{"quantity":4,"condition":"GOOD"},"after":{"quantity":3,"condition":"GOOD"},"change":"MISSING","missing":1}],
                "appliances":[],"meter":{"moveIn":1000.5,"moveOut":1450,"used":449.5},"keys":{"moveIn":3,"moveOut":2,"missing":1},
                "damages":{"moveIn":null,"moveOut":"Scratch on door"},"repairsAtMoveOut":[],"summary":{"areasWorse":1,"itemsMissing":1,"itemsWorse":0,"hasIssues":true}}}""",
            ),
        )
        val d = c.comparison!!
        assertEquals("WORSE", d.areas.single().change)
        assertEquals(1, d.furniture.single().missing)
        assertEquals(449.5, d.meterUsed!!, 0.0)
        assertEquals(1, d.keysMissing)
        assertNull(d.damagesIn)
        assertTrue(d.summary.hasIssues)
        assertEquals("/api/v1/files/property-images/p1", c.moveIn!!.photos.single().url)
        assertEquals("KITCHEN", c.moveIn!!.photos.single().category)
    }

    @Test fun qr() {
        val q = SmartMapper.propertyQr(json("""{"property":{"code":"SR-PROP-1","title":"Green","url":"https://app.saferenttechnologies.com/p/SR-PROP-1"},"units":[{"unitId":"u1","code":"SR-UNIT-2","label":"Flat 101","url":"https://app.saferenttechnologies.com/p/SR-PROP-1/u/SR-UNIT-2"}]}"""))!!
        assertEquals(2, q.all.size)
        assertEquals("u1", q.units.single().unitId)
        val r = SmartMapper.resolution(json("""{"role":"TENANT","property":{"id":"p1","code":"SR-PROP-1","title":"Green"},"unit":{"id":"u1","code":"SR-UNIT-2","label":"Flat 101"},"leaseId":"l1","actions":["OPEN_LEASE","RAISE_REQUEST","EMERGENCY"],"message":null}"""))
        assertEquals("Green · Flat 101", r.title)
        assertEquals(listOf("OPEN_LEASE", "RAISE_REQUEST", "EMERGENCY"), r.actions)
        assertNull(r.message)
    }

    @Test fun reminders() {
        val d = SmartMapper.reminders(json("""{"settings":{"enabled":true,"beforeDays":[1,3],"overdueDays":[7,1],"smartEarly":false},"defaults":{"enabled":true,"beforeDays":[3,1],"overdueDays":[1,3,7],"smartEarly":true},"sendingHours":"9 AM – 8 PM IST"}"""))
        assertEquals(ReminderSettings(true, listOf(3, 1), listOf(1, 7), false), d.settings)
        val body = SmartMapper.reminderBody(ReminderSettings(beforeDays = listOf(3), overdueDays = listOf(7, 1)))
        assertEquals(2, (body["overdueDays"] as JsonArray).size)
    }
}
