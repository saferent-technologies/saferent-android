package com.saferenttechnologies.android.data.notification

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationMapperTest {
    @Test fun `data array + meta to page`() {
        val data = Json.parseToJsonElement(
            """[{"id":"5b2e","userId":"u1","title":"Approved","body":"Done","link":"/properties/8f1c","readAt":null,"createdAt":"2026-11-14T15:50:12.000Z"},
                {"id":"6c3f","title":"Old","body":"","link":"","readAt":"2026-11-13T10:00:00.000Z","createdAt":"2026-11-13T09:00:00.000Z"}]""",
        )
        val meta = Json.parseToJsonElement("""{"page":1,"pageSize":20,"total":37,"totalPages":2,"unread":4}""")
        val page = NotificationRepositoryImpl.toPage(data, meta, 1)
        assertEquals(2, page.items.size)
        assertTrue(page.items[0].isUnread)
        assertFalse(page.items[1].isUnread)
        assertEquals("/properties/8f1c", page.items[0].link)
        assertTrue(page.items[0].createdAtMillis != null)
        assertEquals(4, page.unread)
        assertTrue(page.hasMore)
    }
}
