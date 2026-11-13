package com.saferenttechnologies.android.data.mapper

import com.saferenttechnologies.android.data.remote.SafeRentJson
import com.saferenttechnologies.android.data.remote.dto.ConfigDataDto
import com.saferenttechnologies.android.domain.model.AppConfig
import com.saferenttechnologies.android.domain.model.ReferralProgram
import com.saferenttechnologies.android.testutil.TestConfig.REAL_DATA_JSON
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfigMappersTest {

    private val json = SafeRentJson.instance

    private fun parse(raw: String): AppConfig = json.parseToJsonElement(raw).jsonObject.toAppConfig(json)

    @Test
    fun `DTO defaults match domain defaults`() {
        assertEquals(AppConfig(), ConfigDataDto().toDomain())
    }

    @Test
    fun `empty object maps to defaults`() {
        assertEquals(AppConfig(), JsonObject(emptyMap()).toAppConfig(json))
    }

    @Test
    fun `real response maps every type exactly`() {
        val config = parse(REAL_DATA_JSON)

        assertEquals(3, config.configVersion)
        assertEquals("2026-11-13T16:40:12.000Z", config.updatedAt)
        assertNull(config.maintenance.startsAt)
        assertNull(config.maintenance.expectedEndAt)
        assertFalse(config.features.chat)
        assertTrue(config.features.extra.isEmpty())

        assertEquals("INDIVIDUAL_HOUSE", config.properties.enabledTypes[1].key)
        assertEquals("ROOMS_BEDS", config.properties.enabledTypes[2].structure)

        assertEquals("NONE", config.payments.convenienceFee.type)
        assertEquals(0.0, config.payments.convenienceFee.value, 0.0)
        assertNull(config.payments.convenienceFee.maxAmount)
        assertEquals(10.0, config.payments.minAmount!!, 0.0)

        assertEquals(ReferralProgram("TENANT_TO_TENANT", 50.0, null), config.referrals.programs[0])
        assertEquals(ReferralProgram("LANDLORD_TO_LANDLORD", null, 1), config.referrals.programs[2])

        assertEquals("1.4", config.splash.version)
        assertEquals("2.0", config.onboarding.version)
        assertEquals(1500L, config.splash.minDurationMs)
        assertTrue(config.splash.lottieUrl.startsWith("http://"))

        assertEquals(listOf("LOTTIE", "IMAGE", "VIDEO"), config.onboarding.screens.map { it.mediaType })
        assertEquals("3f9a1c7b2e10", config.onboarding.contentId)
    }

    @Test
    fun `missing splash and onboarding version defaults to empty`() {
        val config = parse("""{"splash":{"enabled":true},"onboarding":{"enabled":true}}""")
        assertEquals("", config.splash.version)
        assertEquals("", config.onboarding.version)
    }

    @Test
    fun `disabled onboarding exposes no screens`() {
        val config = parse(
            """{"onboarding":{"enabled":false,"screens":[{"id":"x","mediaType":"IMAGE"}]}}""",
        )
        assertTrue(config.onboarding.screens.isEmpty())
    }
}
