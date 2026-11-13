package com.saferenttechnologies.android.presentation.config

import com.saferenttechnologies.android.domain.model.AppConfig
import com.saferenttechnologies.android.domain.model.AppError
import com.saferenttechnologies.android.domain.model.AppInfo
import com.saferenttechnologies.android.domain.model.ConfigSyncResult
import com.saferenttechnologies.android.domain.model.Maintenance
import com.saferenttechnologies.android.domain.model.UiSettings
import com.saferenttechnologies.android.domain.usecase.ForceRefreshAppConfigUseCase
import com.saferenttechnologies.android.domain.usecase.GetAppConfigUseCase
import com.saferenttechnologies.android.domain.usecase.ObserveAppConfigUseCase
import com.saferenttechnologies.android.domain.usecase.SyncAppConfigUseCase
import com.saferenttechnologies.android.testutil.FakeConfigRepository
import com.saferenttechnologies.android.testutil.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppConfigViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeConfigRepository()

    private fun viewModel(versionName: String = "1.0.0") = AppConfigViewModel(
        observeAppConfig = ObserveAppConfigUseCase(repository),
        syncAppConfig = SyncAppConfigUseCase(repository),
        forceRefreshAppConfig = ForceRefreshAppConfigUseCase(repository),
        getAppConfig = GetAppConfigUseCase(repository),
        currentVersionName = versionName,
    )

    private fun TestScope.collectEffects(vm: AppConfigViewModel): List<AppConfigEffect> {
        val effects = mutableListOf<AppConfigEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.effect.toList(effects) }
        return effects
    }

    @Test
    fun `init only observes the cache - no network`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(0, repository.syncCalls)
        assertEquals(0, repository.forceCalls)
        assertEquals(AppConfigState(), vm.state.value)
    }

    @Test
    fun `Observe updates state from DataStore`() = runTest {
        val vm = viewModel()
        val saved = AppConfig(configVersion = 5, ui = UiSettings(primaryColor = "#000000"))

        repository.config.value = saved
        advanceUntilIdle()

        assertEquals(saved, vm.state.value.config)
        assertEquals(5, vm.state.value.configVersion)
    }

    @Test
    fun `SyncOnce sets isSyncing, then stores lastSync`() = runTest {
        val gate = CompletableDeferred<Unit>()
        repository.gate = gate
        repository.syncResult = ConfigSyncResult.Updated(3)
        repository.configAfterSync = AppConfig(configVersion = 3)
        val vm = viewModel()

        vm.onIntent(AppConfigIntent.SyncOnce)
        advanceUntilIdle()
        assertTrue(vm.state.value.isSyncing)
        assertNull(vm.state.value.lastSync)

        gate.complete(Unit)
        advanceUntilIdle()
        assertFalse(vm.state.value.isSyncing)
        assertEquals(ConfigSyncResult.Updated(3), vm.state.value.lastSync)
        assertEquals(3, vm.state.value.configVersion)
        assertEquals(1, repository.syncCalls)
    }

    @Test
    fun `failed sync emits SyncFailed and keeps cached config`() = runTest {
        repository.config.value = AppConfig(configVersion = 2)
        repository.syncResult = ConfigSyncResult.Failed(AppError.Network())
        val vm = viewModel()
        val effects = collectEffects(vm)

        vm.onIntent(AppConfigIntent.SyncOnce)
        advanceUntilIdle()

        assertEquals(listOf(AppConfigEffect.SyncFailed(AppError.Network().message)), effects)
        assertEquals(ConfigSyncResult.Failed(AppError.Network()), vm.state.value.lastSync)
        assertEquals(2, vm.state.value.configVersion)
        assertFalse(vm.state.value.isSyncing)
    }

    @Test
    fun `maintenance and required update emit effects after sync`() = runTest {
        repository.configAfterSync = AppConfig(
            maintenance = Maintenance(enabled = true, title = "Back soon"),
            app = AppInfo(latestVersion = "2.0.0", minimumSupportedVersion = "1.5.0"),
        )
        val vm = viewModel(versionName = "1.0")
        val effects = collectEffects(vm)

        vm.onIntent(AppConfigIntent.SyncOnce)
        advanceUntilIdle()

        assertEquals(listOf(AppConfigEffect.MaintenanceOn, AppConfigEffect.UpdateRequired), effects)
    }

    @Test
    fun `ForceRefresh uses the force refresh use case`() = runTest {
        val vm = viewModel()

        vm.onIntent(AppConfigIntent.ForceRefresh)
        advanceUntilIdle()

        assertEquals(1, repository.forceCalls)
        assertEquals(0, repository.syncCalls)
    }

    @Test
    fun `state is immutable - changes produce new copies, old snapshots stay intact`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        val before = vm.state.value
        val snapshots = mutableListOf<AppConfigState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.toList(snapshots) }

        vm.onIntent(AppConfigIntent.SyncOnce)
        advanceUntilIdle()

        val after = vm.state.value
        assertNotSame(before, after)
        assertFalse(before.isSyncing)
        assertNull(before.lastSync)
        assertEquals(ConfigSyncResult.Updated(1), after.lastSync)
        // Every emitted state is a distinct object (copy, never mutated in place).
        assertEquals(snapshots.size, snapshots.map { System.identityHashCode(it) }.toSet().size)
    }
}
