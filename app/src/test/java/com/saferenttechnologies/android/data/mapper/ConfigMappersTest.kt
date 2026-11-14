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

        assertTrue(config.auth.showDemographics)
        assertTrue(config.auth.demographicsSkippable)
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
    fun `media is picked by mediaType, bundled image always last`() {
        val config = parse(
            """{"onboarding":{"enabled":true,"screens":[
              {"id":"img","mediaType":"IMAGE","imageUrl":"https://x/a.png"},
              {"id":"vid","mediaType":"VIDEO","videoUrl":"https://x/a.mp4"},
              {"id":"lot","mediaType":"LOTTIE","lottieUrl":"https://x/a.json"},
              {"id":"none","mediaType":"NONE","imageUrl":"https://x/ignored.png"},
              {"id":"empty","mediaType":"VIDEO","videoUrl":""}
            ]}}""",
        )
        val (img, vid, lot, none, empty) = config.onboarding.screens
        assertEquals(listOf(OnboardingMedia.Image("https://x/a.png"), OnboardingMedia.Bundled), img.mediaCandidates)
        assertEquals(listOf(OnboardingMedia.Video("https://x/a.mp4"), OnboardingMedia.Bundled), vid.mediaCandidates)
        assertEquals(listOf(OnboardingMedia.Lottie("https://x/a.json"), OnboardingMedia.Bundled), lot.mediaCandidates)
        assertEquals(listOf(OnboardingMedia.Bundled), none.mediaCandidates)
        assertEquals(listOf(OnboardingMedia.Bundled), empty.mediaCandidates)
    }

    @Test
    fun `new backend JSON parses - unknown fields ignored, removed fields don't break`() {
        val config = parse(
            """{"configVersion":12,"brandNew":{"a":1},
               "splash":{"version":"1.9","logoUrl":"https://old/logo.png","lightLogoUrl":"https://x/l.png","darkLogoUrl":""},
               "onboarding":{"version":"2.0","enabled":true,"contentId":"old","skipButtonText":"Skip",
                 "screens":[{"id":"a","mediaUrl":"https://old","mediaType":"IMAGE","imageUrl":"https://x/a.webp","buttonText":"Get started","extra":true}]},
               "auth":{"showDemographics":false,"demographicsSkippable":false,"newFlag":1},
               "legal":{"termsVersion":"2026-12-01","privacyVersion":"2026-11-01"}}""",
        )
        assertEquals("1.9", config.splash.version)
        assertEquals("https://x/l.png", config.splash.lightLogoUrl)
        assertEquals("2.0", config.onboarding.version)
        assertEquals("Get started", config.onboarding.screens.single().buttonText)
        assertFalse(config.auth.showDemographics)
        assertFalse(config.auth.demographicsSkippable)
        assertEquals("2026-12-01", config.legal.termsVersion)
    }

    @Test
    fun `missing splash and onboarding use the spec defaults`() {
        val config = JsonObject(emptyMap()).toAppConfig(json)
        assertEquals("1.0", config.splash.version)
        assertEquals("Verified homes. Honest renting.", config.splash.description)
        assertEquals("1.0", config.onboarding.version)
        assertEquals("2026-11-01", config.legal.termsVersion)
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
