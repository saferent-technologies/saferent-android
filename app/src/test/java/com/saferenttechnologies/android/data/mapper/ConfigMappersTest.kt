package com.saferenttechnologies.android.data.mapper

import com.saferenttechnologies.android.data.remote.SafeRentJson
import com.saferenttechnologies.android.data.remote.dto.ConfigDataDto
import com.saferenttechnologies.android.domain.model.AppConfig
import com.saferenttechnologies.android.domain.model.OnboardingMedia
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
        assertTrue(config.splash.lightLogoUrl.endsWith("0b6f2c1e-6d3a-4f7e-9a51-1c2d3e4f5a6b.png"))
        assertTrue(config.splash.darkLogoUrl.endsWith("4c3d2e1f-7a8b-4c9d-8e7f-6a5b4c3d2e1f.png"))
        assertEquals("1.4", config.onboarding.version)
        assertEquals("Skip", config.onboarding.skipButtonText)
        assertEquals(1500L, config.splash.minDurationMs)
        assertTrue(config.splash.lottieUrl.startsWith("http://"))

        val screens = config.onboarding.screens
        assertEquals(listOf("screen-verified", "screen-rental", "screen-charges"), screens.map { it.id })
        assertEquals(listOf("IMAGE", "VIDEO", "LOTTIE"), screens.map { it.mediaType })
        assertEquals(listOf("Next", "Next", "Get started"), screens.map { it.buttonText })
        // media picked by availability: video → image → lottie, bundled image always last
        assertEquals(
            listOf(OnboardingMedia.Image(screens[0].imageUrl), OnboardingMedia.Bundled),
            screens[0].mediaCandidates,
        )
        assertTrue((screens[1].mediaCandidates.first() as OnboardingMedia.Video).url.endsWith(".mp4"))
        assertTrue((screens[2].mediaCandidates.first() as OnboardingMedia.Lottie).url.endsWith(".json"))
    }

    @Test
    fun `missing splash and onboarding version defaults to empty`() {
        val config = parse("""{"splash":{"enabled":true},"onboarding":{"enabled":true}}""")
        assertEquals("", config.splash.version)
        assertEquals("", config.onboarding.version)
    }

    @Test
    fun `media priority is video, then image, then lottie, then bundled - mediaType ignored`() {
        val config = parse(
            """{"onboarding":{"enabled":true,"screens":[
              {"id":"all","mediaType":"LOTTIE","imageUrl":"https://x/a.png","videoUrl":"https://x/a.mp4","lottieUrl":"https://x/a.json"},
              {"id":"img+lottie","mediaType":"NONE","imageUrl":"https://x/b.png","lottieUrl":"https://x/b.json"},
              {"id":"none","mediaType":"VIDEO"}
            ]}}""",
        )
        val (all, imageLottie, none) = config.onboarding.screens
        assertEquals(
            listOf(
                OnboardingMedia.Video("https://x/a.mp4"),
                OnboardingMedia.Image("https://x/a.png"),
                OnboardingMedia.Lottie("https://x/a.json"),
                OnboardingMedia.Bundled,
            ),
            all.mediaCandidates,
        )
        assertEquals(OnboardingMedia.Image("https://x/b.png"), imageLottie.mediaCandidates.first())
        assertEquals(listOf(OnboardingMedia.Bundled), none.mediaCandidates)
    }

    @Test
    fun `missing onboarding is enabled with default texts`() {
        val onboarding = JsonObject(emptyMap()).toAppConfig(json).onboarding
        assertTrue(onboarding.enabled)
        assertEquals("Skip", onboarding.skipButtonText)
        assertTrue(onboarding.screens.isEmpty())
    }

    @Test
    fun `disabled onboarding exposes no screens`() {
        val config = parse(
            """{"onboarding":{"enabled":false,"screens":[{"id":"x","mediaType":"IMAGE"}]}}""",
        )
        assertTrue(config.onboarding.screens.isEmpty())
    }
}
