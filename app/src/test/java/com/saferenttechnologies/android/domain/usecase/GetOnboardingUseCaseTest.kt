package com.saferenttechnologies.android.domain.usecase

import com.saferenttechnologies.android.domain.model.AppConfig
import com.saferenttechnologies.android.domain.model.DefaultOnboarding
import com.saferenttechnologies.android.domain.model.OnboardingDecision
import com.saferenttechnologies.android.domain.model.OnboardingDecision.HiddenReason
import com.saferenttechnologies.android.domain.model.OnboardingMedia
import com.saferenttechnologies.android.domain.model.OnboardingScreen
import com.saferenttechnologies.android.domain.model.OnboardingSettings
import com.saferenttechnologies.android.testutil.FakeConfigRepository
import com.saferenttechnologies.android.testutil.FakeOnboardingRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GetOnboardingUseCaseTest {

    private val config = FakeConfigRepository()
    private val onboarding = FakeOnboardingRepository()
    private val useCase = GetOnboardingUseCase(config, onboarding)

    private fun settings(vararg screens: OnboardingScreen, block: OnboardingSettings.() -> OnboardingSettings = { this }) {
        config.config.value = AppConfig(onboarding = OnboardingSettings(version = "1.4", screens = screens.toList()).block())
    }

    @Test
    fun `empty cache shows the built-in default onboarding`() = runTest {
        val content = (useCase() as OnboardingDecision.Show).content
        assertEquals(DefaultOnboarding.VERSION, content.version)
        assertEquals(DefaultOnboarding.pages, content.pages)
        assertEquals(listOf(OnboardingMedia.Bundled), content.pages.first().media)
        assertEquals("Skip", content.skipButtonText)
    }

    @Test
    fun `server screens are used with their button texts and media priority`() = runTest {
        settings(
            OnboardingScreen(id = "a", title = "A", buttonText = "Continue", mediaType = "VIDEO", videoUrl = "v.mp4"),
            OnboardingScreen(id = "b", title = "B", buttonText = "Let's go"),
        ) { copy(skipButtonText = "Later") }

        val content = (useCase() as OnboardingDecision.Show).content
        assertEquals("1.4", content.version)
        assertEquals("Later", content.skipButtonText)
        assertEquals(listOf("Continue", "Let's go"), content.pages.map { it.buttonText })
        assertEquals(OnboardingMedia.Video("v.mp4"), content.pages[0].media.first())
    }

    @Test
    fun `blank button texts fall back to Next and Get started on the last page`() = runTest {
        settings(
            OnboardingScreen(id = "a", buttonText = ""),
            OnboardingScreen(id = "b", buttonText = ""),
            OnboardingScreen(id = "c", buttonText = ""),
        ) {
            copy(skipButtonText = "")
        }

        val content = (useCase() as OnboardingDecision.Show).content
        assertEquals(listOf("Next", "Next", "Get started"), content.pages.map { it.buttonText })
        assertEquals("Skip", content.skipButtonText)
    }

    @Test
    fun `disabled onboarding is not shown`() = runTest {
        settings(OnboardingScreen(id = "a")) { copy(enabled = false) }
        assertEquals(OnboardingDecision.Hidden(HiddenReason.DISABLED), useCase())
    }

    @Test
    fun `showOnce hides it after completion, showOnce false shows it again`() = runTest {
        onboarding.completed = true
        settings(OnboardingScreen(id = "a"))
        assertEquals(OnboardingDecision.Hidden(HiddenReason.ALREADY_COMPLETED), useCase())

        settings(OnboardingScreen(id = "a")) { copy(showOnce = false) }
        assertTrue(useCase() is OnboardingDecision.Show)
    }
}
