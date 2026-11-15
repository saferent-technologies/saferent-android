package com.saferenttechnologies.android.data.push

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PushImagesTest {
    private val api = "https://api.saferenttechnologies.com/"

    @Test fun `relative path goes to our API`() {
        assertEquals("https://api.saferenttechnologies.com/api/v1/files/announcements/i1", PushImages.resolve("/api/v1/files/announcements/i1", api))
        assertTrue(PushImages.sameHost("https://api.saferenttechnologies.com/x", api))
    }

    @Test fun `other hosts only over https, token never sent there`() {
        assertEquals("https://cdn.example.com/a.jpg", PushImages.resolve("https://cdn.example.com/a.jpg", api))
        assertFalse(PushImages.sameHost("https://cdn.example.com/a.jpg", api))
        assertNull(PushImages.resolve("http://cdn.example.com/a.jpg", api))
        assertNull(PushImages.resolve("", api))
    }

    @Test fun `payload picks the image from data or the notification block`() {
        val p = PushPayload.from("Water off", "Tank cleaning", mapOf("imageUrl" to "/api/v1/files/announcements/i1", "type" to "ANNOUNCEMENT"))!!
        assertEquals("/api/v1/files/announcements/i1", p.imageUrl)
        val q = PushPayload.from("Water off", "Tank cleaning", emptyMap(), notificationImage = "https://cdn.example.com/a.jpg")!!
        assertEquals("https://cdn.example.com/a.jpg", q.imageUrl)
        assertEquals("", PushPayload.from("Hi", "There", emptyMap())!!.imageUrl)
    }
}
