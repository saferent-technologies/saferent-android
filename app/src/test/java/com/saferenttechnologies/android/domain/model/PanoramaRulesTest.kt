package com.saferenttechnologies.android.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PanoramaRulesTest {
    @Test fun `only wide photos are panoramas`() {
        assertFalse(PanoramaRules.isPanorama(4000, 3000)) // normal 4:3
        assertTrue(PanoramaRules.isPanorama(8000, 4000)) // photo sphere 2:1
        assertTrue(PanoramaRules.isPanorama(12000, 2200)) // phone panorama
    }

    @Test fun `2 to 1 is a sphere, wider is a cylinder`() {
        val sphere = PanoramaRules.projection(8000, 4000)
        assertTrue(sphere.sphere)
        assertEquals(360f, sphere.horizontalDeg, 0f)
        val pano = PanoramaRules.projection(9000, 3000) // 3:1 → 195°
        assertFalse(pano.sphere)
        assertEquals(195f, pano.horizontalDeg, 0.01f)
        assertEquals(360f, PanoramaRules.projection(20000, 2000).horizontalDeg, 0f) // capped
    }

    @Test fun `scaled to fit 4096 x 2048`() {
        assertEquals(4096 to 2048, PanoramaRules.scaledSize(8192, 4096))
        assertEquals(4096 to 1024, PanoramaRules.scaledSize(16000, 4000))
        assertEquals(3000 to 1000, PanoramaRules.scaledSize(3000, 1000))
        assertTrue(PanoramaRules.isPanoramaCategory("panorama_360"))
    }
}
