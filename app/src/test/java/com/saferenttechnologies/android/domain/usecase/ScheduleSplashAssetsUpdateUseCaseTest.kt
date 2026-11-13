package com.saferenttechnologies.android.domain.usecase

import com.saferenttechnologies.android.domain.model.AppConfig
import com.saferenttechnologies.android.domain.model.SplashAppearance
import com.saferenttechnologies.android.domain.model.SplashSettings
import com.saferenttechnologies.android.testutil.FakeConfigRepository
import com.saferenttechnologies.android.testutil.FakeSplashAssetsScheduler
import com.saferenttechnologies.android.testutil.FakeSplashRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleSplashAssetsUpdateUseCaseTest {

    private val config = FakeConfigRepository()
    private val splash = FakeSplashRepository()
    private val scheduler = FakeSplashAssetsScheduler()
    private val useCase = ScheduleSplashAssetsUpdateUseCase(config, splash, scheduler)

    @Test
    fun `schedules a download when the server splash version changed`() = runTest {
        config.config.value = AppConfig(splash = SplashSettings(version = "1.4"))

        assertTrue(useCase())
        assertEquals(1, scheduler.scheduledCount)
    }

    @Test
    fun `does nothing when the saved splash is current`() = runTest {
        config.config.value = AppConfig(splash = SplashSettings(version = "1.4"))
        splash.appearance.value = SplashAppearance(version = "1.4")

        assertFalse(useCase())
        assertEquals(0, scheduler.scheduledCount)
    }

    @Test
    fun `splash disabled never downloads assets`() = runTest {
        config.config.value = AppConfig(splash = SplashSettings(version = "2", enabled = false))

        assertFalse(useCase())
        assertEquals(0, scheduler.scheduledCount)
    }

    @Test
    fun `empty config cache (no version) never schedules`() = runTest {
        assertFalse(useCase())
        assertEquals(0, scheduler.scheduledCount)
    }
}
