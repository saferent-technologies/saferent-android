package com.saferenttechnologies.android.presentation.splash

import com.saferenttechnologies.android.domain.model.AppConfig
import com.saferenttechnologies.android.domain.model.SplashAppearance
import com.saferenttechnologies.android.domain.model.SplashSettings
import com.saferenttechnologies.android.domain.usecase.GetSplashAppearanceUseCase
import com.saferenttechnologies.android.domain.usecase.GetSplashConfigUseCase
import com.saferenttechnologies.android.testutil.FakeConfigRepository
import com.saferenttechnologies.android.testutil.FakeSplashRepository
import com.saferenttechnologies.android.testutil.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SplashViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val config = FakeConfigRepository()

    private fun viewModel(saved: SplashAppearance = SplashAppearance()) = SplashViewModel(
        getSplashAppearance = GetSplashAppearanceUseCase(FakeSplashRepository(saved)),
        getSplashConfig = GetSplashConfigUseCase(config),
    )

    @Test
    fun `shows saved splash immediately, finishes after minDurationMs`() = runTest {
        val saved = SplashAppearance(
            version = "1.4",
            title = "SafeRent",
            description = "Verified homes. Honest renting.",
            logoPath = "/data/splash/logo.png",
            minDurationMs = 1500,
        )
        val vm = viewModel(saved)
        runCurrent()

        assertTrue(vm.state.value.isReady)
        assertEquals(saved, vm.state.value.appearance)
        assertFalse(vm.state.value.isFinished)

        advanceTimeBy(1499)
        runCurrent()
        assertFalse(vm.state.value.isFinished)

        advanceTimeBy(2)
        runCurrent()
        assertTrue(vm.state.value.isFinished)
    }

    @Test
    fun `no saved splash uses bundled defaults`() = runTest {
        val vm = viewModel()
        runCurrent()

        assertEquals(SplashAppearance(), vm.state.value.appearance)
        assertEquals("SafeRent", vm.state.value.appearance.title)
        assertEquals(null, vm.state.value.appearance.logoPath)
    }

    @Test
    fun `splash disabled on the server skips it immediately`() = runTest {
        config.config.value = AppConfig(splash = SplashSettings(enabled = false))
        val vm = viewModel(SplashAppearance(version = "1.4", minDurationMs = 1500))
        runCurrent()

        assertTrue(vm.state.value.isReady)
        assertTrue(vm.state.value.isFinished) // no 1.5s wait
    }
}
