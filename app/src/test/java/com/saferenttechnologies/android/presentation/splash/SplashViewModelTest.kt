package com.saferenttechnologies.android.presentation.splash

import com.saferenttechnologies.android.core.log.NoOpLogger
import com.saferenttechnologies.android.domain.model.AppConfig
import com.saferenttechnologies.android.domain.model.ConfigSyncResult
import com.saferenttechnologies.android.domain.usecase.AwaitInitialConfigUseCase
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
        awaitInitialConfig = AwaitInitialConfigUseCase(config),
        logger = NoOpLogger,
    )

    @Test
    fun `shows saved splash immediately, finishes after minDurationMs`() = runTest {
        val saved = SplashAppearance(
            version = "1.4",
            title = "SafeRent",
            description = "Verified homes. Honest renting.",
            lightLogoPath = "/data/splash/logo-light.png",
            darkLogoPath = "/data/splash/logo-dark.png",
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
        assertEquals(null, vm.state.value.appearance.logoPath(darkTheme = false))
        assertEquals(null, vm.state.value.appearance.logoPath(darkTheme = true))
    }

    @Test
    fun `splash disabled on the server skips it immediately`() = runTest {
        config.config.value = AppConfig(splash = SplashSettings(enabled = false))
        val vm = viewModel(SplashAppearance(version = "1.4", minDurationMs = 1500))
        runCurrent()

        assertTrue(vm.state.value.isReady)
        assertTrue(vm.state.value.isFinished) // no 1.5s wait
    }

    @Test
    fun `first launch - splash stays until the config API succeeds`() = runTest {
        config.lastSavedAt = null // nothing cached yet
        val vm = viewModel(SplashAppearance(minDurationMs = 1500))
        runCurrent()

        advanceTimeBy(5_000)
        runCurrent()
        assertFalse(vm.state.value.isFinished) // min time passed, but no config yet

        config.syncResults.value = ConfigSyncResult.Updated(8)
        runCurrent()
        assertTrue(vm.state.value.isFinished)
    }

    @Test
    fun `first launch offline - gives up after the max wait and continues`() = runTest {
        config.lastSavedAt = null
        config.syncResult = ConfigSyncResult.Failed(com.saferenttechnologies.android.domain.model.AppError.Network())
        config.syncResults.value = config.syncResult
        val vm = viewModel(SplashAppearance(minDurationMs = 1500))

        advanceTimeBy(AwaitInitialConfigUseCase.MAX_WAIT_MS - 1)
        runCurrent()
        assertFalse(vm.state.value.isFinished)

        advanceTimeBy(2)
        runCurrent()
        assertTrue(vm.state.value.isFinished)
    }
}
