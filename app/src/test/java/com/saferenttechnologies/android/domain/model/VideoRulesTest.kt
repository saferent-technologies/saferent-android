package com.saferenttechnologies.android.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoRulesTest {
    private val flat = PropertyVideo("v1", unitId = "u1")
    private val other = PropertyVideo("v2", unitId = "u2")
    private val building = PropertyVideo("v3")

    @Test fun `selected flat shows only its own video, building video when it has none`() {
        assertEquals(listOf(flat), VideoRules.forFlat(listOf(other, building, flat), "u1", unitPriced = true))
        assertEquals(listOf(building), VideoRules.forFlat(listOf(building, flat), "u9", unitPriced = true))
        assertEquals(3, VideoRules.forFlat(listOf(other, building, flat), "u1", unitPriced = false).size)
    }

    @Test fun `no flat chosen and no building video shows the flat videos`() {
        assertEquals(listOf(other, flat), VideoRules.forFlat(listOf(other, flat), null, unitPriced = true))
        assertEquals(listOf(building), VideoRules.forFlat(listOf(other, building, flat), null, unitPriced = true))
    }

    @Test fun `length rules`() {
        assertFalse(VideoRules.canStop(3))
        assertTrue(VideoRules.canStop(5))
        assertEquals("1:00", VideoRules.clock(60))
    }
}

class VideoRequiredTest {
    @Test fun `every flat needs a video, uploading counts`() {
        val units = listOf("u1" to "101", "u2" to "102", "u3" to "103")
        val missing = VideoRules.unitsWithoutVideo(units, listOf(PropertyVideo("v", unitId = "u1")), uploadingUnitIds = setOf("u2"))
        assertEquals(listOf("103"), missing)
    }
}
