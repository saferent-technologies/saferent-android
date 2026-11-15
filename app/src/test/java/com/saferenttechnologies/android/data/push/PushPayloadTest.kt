package com.saferenttechnologies.android.data.push

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PushPayloadTest {
    private val data = mapOf("notificationId" to "5b2e", "link" to "/properties/8f1c", "type" to "GENERAL")

    @Test fun `notification title and body first, link and id go to the open intent`() {
        val p = PushPayload.from("Approved 🎉", "\"Royals\" is verified.", data)!!
        assertEquals("Approved 🎉", p.title)
        assertEquals("\"Royals\" is verified.", p.body)
        assertEquals(mapOf("link" to "/properties/8f1c", "notificationId" to "5b2e", "type" to "GENERAL"), p.extras)
        assertEquals("5b2e".hashCode(), p.androidId)
    }

    @Test fun `falls back to data title and body`() {
        val p = PushPayload.from(null, null, data + mapOf("title" to "T", "body" to "B"))!!
        assertEquals("T", p.title)
        assertEquals("B", p.body)
    }

    @Test fun `nothing to show`() = assertNull(PushPayload.from(null, " ", data))

    @Test fun `default channel id`() = assertEquals("saferent_default", NotificationChannels.DEFAULT)
}
