package com.saferenttechnologies.android.presentation.property

import org.junit.Assert.assertEquals
import org.junit.Test

class CameraActionsTest {
    @Test
    fun `camera never offers a gallery or file option`() {
        CameraMode.entries.forEach { mode ->
            val all = CameraActions.beforeCapture(mode) + CameraActions.afterCapture(mode)
            assertEquals(setOf(CameraAction.CLOSE, CameraAction.CAPTURE, CameraAction.RETAKE, CameraAction.USE_PHOTO), all.toSet())
            all.forEach { action ->
                val name = action.name
                assert("GALLERY" !in name && "FILE" !in name && "PICK" !in name) { "Unexpected camera action $name" }
            }
        }
        // And the enum itself has nothing else.
        assertEquals(4, CameraAction.entries.size)
    }
}
