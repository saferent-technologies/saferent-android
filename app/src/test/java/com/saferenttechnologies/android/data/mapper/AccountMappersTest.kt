package com.saferenttechnologies.android.data.mapper

import com.saferenttechnologies.android.data.remote.SafeRentJson
import com.saferenttechnologies.android.data.remote.dto.ApiEnvelope
import com.saferenttechnologies.android.data.remote.dto.UserProfileResultDto
import com.saferenttechnologies.android.data.remote.dto.PhoneCompleteDto
import com.saferenttechnologies.android.domain.model.AppError
import com.saferenttechnologies.android.domain.model.UserProfileRequest
import com.saferenttechnologies.android.domain.model.Gender
import com.saferenttechnologies.android.domain.model.UserRole
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import com.saferenttechnologies.android.domain.model.ConsentItem
import com.saferenttechnologies.android.domain.model.ConsentType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountMappersTest {

    private val json = SafeRentJson.instance

    @Test
    fun `phone complete parses`() {
        val body = """{"success":true,"message":"ok","data":{"ok":true,"registrationRequired":true,
            "mustChangePassword":false,"isProfileCompleted":false,"missingFields":["fullName","role"],"extra":1}}"""
        val dto = json.decodeFromString(ApiEnvelope.serializer(PhoneCompleteDto.serializer()), body).data!!.toDomain()
        assertTrue(dto.registrationRequired)
        assertFalse(dto.isProfileCompleted)
        assertEquals(listOf("fullName", "role"), dto.missingFields)
    }

    @Test
    fun `auth me parses wrapped and bare user`() {
        val wrapped = json.parseToJsonElement(
            """{"user":{"id":"u1","fullName":"Ravi","role":"LANDLORD","roles":["LANDLORD","TENANT"],
               "gender":"MALE","dateOfBirth":"1990-05-01T00:00:00.000Z","city":"Hyderabad","state":"Telangana"}}""",
        )
        val user = wrapped.toMeUser(json)!!
        assertEquals(UserRole.LANDLORD, user.role)
        assertEquals(listOf(UserRole.LANDLORD, UserRole.TENANT), user.roles)
        assertEquals(Gender.MALE, user.gender)
        assertEquals("1990-05-01", user.dateOfBirth)

        val bare: JsonElement = json.parseToJsonElement("""{"id":"u2","role":"tenant"}""")
        assertEquals(UserRole.TENANT, bare.toMeUser(json)!!.role)
    }

    @Test
    fun `user profile response parses with 201 created and consents`() {
        val body = """{"success":true,"message":"Created","data":{"user":{"id":"u1","fullName":"Sita"},
            "isProfileCompleted":true,"missingFields":[],"role":"LANDLORD","roles":["LANDLORD"],"termsAccepted":true,
            "consents":{"terms":{"accepted":true,"acceptedVersion":"2026-11-01","acceptedAt":"2026-11-14T09:30:00.000Z"},
              "privacy":{"accepted":true,"acceptedVersion":"2026-11-01"},"whatsapp":{"granted":false}}}}"""
        val result = json.decodeFromString(ApiEnvelope.serializer(UserProfileResultDto.serializer()), body)
            .data!!.toDomain(httpCode = 201)
        assertTrue(result.created)
        assertEquals(UserRole.LANDLORD, result.role)
        assertEquals(UserRole.LANDLORD, result.user.role) // filled from the response role
        assertTrue(result.user.isProfileCompleted)
        assertTrue(result.termsAccepted)
        assertEquals("2026-11-01", result.consents.terms.acceptedVersion)
        assertFalse(result.consents.whatsapp.granted)
    }

    @Test
    fun `request json carries consents, clientAcceptedAt, source and appVersion`() {
        val body = UserProfileRequest(
            isProfileCompleted = true, role = UserRole.TENANT, fullName = "A",
            consents = listOf(
                ConsentItem(ConsentType.TERMS, "2026-11-01", true),
                ConsentItem(ConsentType.PRIVACY, "2026-11-01", true),
                ConsentItem(ConsentType.WHATSAPP, "", false),
            ),
            clientAcceptedAt = 1_731_576_600_000,
        ).toJson(appVersion = "1.0")
        val consents = body["consents"]!!.jsonArray
        assertEquals(3, consents.size)
        assertEquals("WHATSAPP", consents[2].jsonObject["type"]!!.jsonPrimitive.content)
        assertEquals("false", consents[2].jsonObject["granted"]!!.jsonPrimitive.content)
        assertEquals(1_731_576_600_000, body["clientAcceptedAt"]!!.jsonPrimitive.long)
        assertEquals("ANDROID", body["source"]!!.jsonPrimitive.content)
        assertEquals("1.0", body["appVersion"]!!.jsonPrimitive.content)
    }

    @Test
    fun `phone complete parses termsAccepted and fcmTokenSaved, me parses termsAccepted`() {
        val body = """{"success":true,"data":{"ok":true,"isProfileCompleted":false,"termsAccepted":true,"fcmTokenSaved":true}}"""
        val dto = json.decodeFromString(ApiEnvelope.serializer(PhoneCompleteDto.serializer()), body).data!!.toDomain()
        assertTrue(dto.termsAccepted)
        assertTrue(dto.fcmTokenSaved)

        val me = json.parseToJsonElement("""{"user":{"id":"u1","role":"TENANT"},"termsAccepted":true}""").toMeUser(json)!!
        assertTrue(me.termsAccepted)
    }

    @Test
    fun `request json sends blank optionals as null and skip sends two keys`() {
        val full = UserProfileRequest(
            isProfileCompleted = true, role = UserRole.TENANT, fullName = "A", gender = Gender.FEMALE,
            dateOfBirth = "1999-01-01", city = "C", state = "S", email = null, occupation = "Dev", referralCode = null,
        ).toJson(appVersion = "1.0")
        assertEquals(JsonNull, full["email"])
        assertEquals(JsonNull, full["referralCode"])
        assertEquals("TENANT", full["role"]!!.jsonPrimitive.content)
        assertEquals("FEMALE", full["gender"]!!.jsonPrimitive.content)

        val skip = UserProfileRequest(isProfileCompleted = false, role = UserRole.LANDLORD).toJson(appVersion = "1.0")
        assertEquals(setOf("isProfileCompleted", "role"), skip.keys)
    }

    @Test
    fun `server errors map to account errors`() {
        val v = AppError.Server(400, "VALIDATION", message = "Missing fields", details = """{"missingFields":["city","state"]}""")
            .toAccountError(json)
        assertEquals(listOf("city", "state"), (v as AppError.Validation).missingFields)

        assertTrue(AppError.Server(409, "EMAIL_IN_USE", message = "Email taken").toAccountError(json) is AppError.EmailInUse)

        val disabled = AppError.Server(403, "LOGIN_METHOD_DISABLED").toAccountError(json)
        assertEquals("Phone sign-in is turned off right now", disabled.message)

        assertNull((AppError.Server(500) .toAccountError(json) as AppError.Server).errorCode)
    }
}
