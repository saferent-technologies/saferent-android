package com.saferenttechnologies.android.presentation.onboarding

import com.saferenttechnologies.android.core.log.NoOpLogger
import com.saferenttechnologies.android.domain.model.AppConfig
import com.saferenttechnologies.android.domain.model.OnboardingScreen
import com.saferenttechnologies.android.domain.model.OnboardingSettings
import com.saferenttechnologies.android.domain.usecase.CompleteOnboardingUseCase
import com.saferenttechnologies.android.domain.usecase.GetOnboardingUseCase
import com.saferenttechnologies.android.testutil.FakeConfigRepository
import com.saferenttechnologies.android.testutil.FakeOnboardingRepository
import com.saferenttechnologies.android.testutil.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val config = FakeConfigRepository()
    private val onboarding = FakeOnboardingRepository()

    private fun serverOnboarding(skippable: Boolean = true) {
        config.config.value = AppConfig(
            onboarding = OnboardingSettings(
                version = "1.4",
                skippable = skippable,
                screens = listOf(OnboardingScreen(id = "a"), OnboardingScreen(id = "b"), OnboardingScreen(id = "c")),
            ),
        )
    }

    private fun viewModel() = OnboardingViewModel(
        GetOnboardingUseCase(config, onboarding),
        CompleteOnboardingUseCase(onboarding),
        NoOpLogger,
    ).also { it.onIntent(OnboardingIntent.Load) } // MainActivity sends Load after the splash

    private fun TestScope.effectsOf(vm: OnboardingViewModel): List<OnboardingEffect> {
        val effects = mutableListOf<OnboardingEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.effect.toList(effects) }
        return effects
    }

    @Test
    fun `loads pages and starts on page 0, muted`() = runTest {
        serverOnboarding()
        val vm = viewModel()
        advanceUntilIdle()

        assertTrue(vm.state.value.isLoaded)
        assertFalse(vm.state.value.isFinished)
        assertEquals(3, vm.state.value.pages.size)
        assertEquals(0, vm.state.value.currentPage)
        assertTrue(vm.state.value.isMuted)
    }

    @Test
    fun `Next scrolls, Next on the last page completes`() = runTest {
        serverOnboarding()
        val vm = viewModel()
        val effects = effectsOf(vm)

        vm.onIntent(OnboardingIntent.Next)
        vm.onIntent(OnboardingIntent.Next)
        advanceUntilIdle()
        assertEquals(2, vm.state.value.currentPage)
        assertFalse(vm.state.value.isFinished)

        vm.onIntent(OnboardingIntent.Next)
        advanceUntilIdle()
        assertTrue(vm.state.value.isFinished)
        assertEquals("1.4", onboarding.completedVersion)
        assertEquals(
            listOf(OnboardingEffect.ScrollToPage(1), OnboardingEffect.ScrollToPage(2), OnboardingEffect.Finished),
            effects,
        )
    }

    @Test
    fun `Skip completes when skippable, ignored when not`() = runTest {
        serverOnboarding(skippable = false)
        val notSkippable = viewModel()
        notSkippable.onIntent(OnboardingIntent.Skip)
        advanceUntilIdle()
        assertFalse(notSkippable.state.value.isFinished)
        assertNull(onboarding.completedVersion)

        serverOnboarding(skippable = true)
        val skippable = viewModel()
        skippable.onIntent(OnboardingIntent.Skip)
        advanceUntilIdle()
        assertTrue(skippable.state.value.isFinished)
        assertEquals("1.4", onboarding.completedVersion)
    }

    @Test
    fun `already completed - finishes immediately without showing`() = runTest {
        serverOnboarding()
        onboarding.completed = true
        val vm = viewModel()
        advanceUntilIdle()

        assertTrue(vm.state.value.isLoaded)
        assertTrue(vm.state.value.isFinished)
        assertNull(vm.state.value.content)
    }

    @Test
    fun `swipe updates current page and mute toggles`() = runTest {
        serverOnboarding()
        val vm = viewModel()
        vm.onIntent(OnboardingIntent.PageChanged(2))
        vm.onIntent(OnboardingIntent.ToggleMute)
        advanceUntilIdle()

        assertEquals(2, vm.state.value.currentPage)
        assertTrue(vm.state.value.isLastPage)
        assertFalse(vm.state.value.isMuted)
    }
}
