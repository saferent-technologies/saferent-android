package com.saferenttechnologies.android.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PanoramaCaptureTest {
    @Test fun `turning past 180 does not jump`() {
        assertEquals(20f, PanoramaCapture.advance(0f, 170f, -170f), 0.01f)
        assertEquals(-20f, PanoramaCapture.advance(0f, -170f, 170f), 0.01f)
    }

    @Test fun `shoots every 30 degrees and finishes after a full turn`() {
        assertTrue(PanoramaCapture.isAligned(31f, 1))
        assertFalse(PanoramaCapture.isAligned(40f, 1))
        assertFalse(PanoramaCapture.isFullTurn(11))
        assertTrue(PanoramaCapture.isFullTurn(12))
    }

    @Test fun `done needs at least 120 degrees`() {
        assertFalse(PanoramaCapture.canFinish(2, 48f)) // 78°
        assertTrue(PanoramaCapture.canFinish(4, 48f)) // 138°
    }
}
