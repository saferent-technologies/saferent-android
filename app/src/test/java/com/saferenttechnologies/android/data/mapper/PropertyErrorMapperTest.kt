package com.saferenttechnologies.android.data.mapper

import com.saferenttechnologies.android.domain.model.AppError
import com.saferenttechnologies.android.domain.model.InvitationLink
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PropertyErrorMapperTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test fun `412 keeps the server message as is plus problems`() {
        val e = AppError.Server(
            httpCode = 412,
            errorCode = "INVALID_STATE",
            message = "This property is under verification and can't be edited right now.",
            details = """{"problems":["Add at least 4 photos"]}""",
        ).toPropertyError(json)
        e as AppError.InvalidState
        assertEquals("This property is under verification and can't be edited right now.", e.message)
        assertEquals(listOf("Add at least 4 photos"), e.problems)
    }

    @Test fun `409 VERSION_CONFLICT asks for a reload`() {
        val e = AppError.Server(409, reason = "VERSION_CONFLICT", message = "Someone changed this. Reloading.").toPropertyError(json)
        assertTrue((e as AppError.Conflict).versionConflict)
        assertEquals("Someone changed this. Reloading.", e.message)
    }

    @Test fun `other 409 is not a reload`() {
        val e = AppError.Server(409, errorCode = "CONFLICT", message = "Flat number already exists").toPropertyError(json)
        assertFalse((e as AppError.Conflict).versionConflict)
    }

    @Test fun `share uses link shareText`() {
        val link = InvitationLink(url = "https://saferent.in/i/abc", shareText = "Hi Ravi, join Green Villa on SafeRent: https://saferent.in/i/abc")
        assertEquals(link.shareText, link.shareMessage)
        assertEquals("https://saferent.in/i/abc", InvitationLink(url = "https://saferent.in/i/abc").shareMessage)
    }
}
