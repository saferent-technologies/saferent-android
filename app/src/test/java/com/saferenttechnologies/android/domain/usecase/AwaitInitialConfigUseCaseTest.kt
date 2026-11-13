package com.saferenttechnologies.android.domain.usecase

import com.saferenttechnologies.android.domain.model.AppError
import com.saferenttechnologies.android.domain.model.ConfigSyncResult
import com.saferenttechnologies.android.testutil.FakeConfigRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AwaitInitialConfigUseCaseTest {

    private val config = FakeConfigRepository()
    private val useCase = AwaitInitialConfigUseCase(config)

    @Test
    fun `config already cached - returns immediately without network`() = runTest {
        config.lastSavedAt = 123L
        assertTrue(useCase())
        assertEquals(0, config.forceCalls)
    }

    @Test
    fun `first launch - waits for the startup sync to succeed`() = runTest {
        config.lastSavedAt = null
        val ready = async { useCase() }
        runCurrent()
        assertFalse(ready.isCompleted)

        config.syncResults.value = ConfigSyncResult.Updated(8)
        runCurrent()
        assertTrue(ready.await())
        assertEquals(0, config.forceCalls)
    }

    @Test
    fun `first launch - retries after a failure until success`() = runTest {
        config.lastSavedAt = null
        config.syncResults.value = ConfigSyncResult.Failed(AppError.Network())
        config.syncResult = ConfigSyncResult.Failed(AppError.Network())
        val ready = async { useCase() }

        advanceTimeBy(AwaitInitialConfigUseCase.RETRY_DELAY_MS + 1)
        runCurrent()
        assertEquals(1, config.forceCalls) // retried once, still failing

        config.syncResult = ConfigSyncResult.Updated(8) // network back
        advanceTimeBy(AwaitInitialConfigUseCase.RETRY_DELAY_MS + 1)
        runCurrent()
        assertTrue(ready.await())
        assertEquals(2, config.forceCalls)
    }

    @Test
    fun `first launch offline - gives up after the max wait`() = runTest {
        config.lastSavedAt = null
        config.syncResults.value = ConfigSyncResult.Failed(AppError.Network())
        config.syncResult = ConfigSyncResult.Failed(AppError.Network())

        assertFalse(useCase())
    }
}
