package com.saferenttechnologies.android.presentation.permission

import com.saferenttechnologies.android.core.log.NoOpLogger
import com.saferenttechnologies.android.domain.model.AppPermission
import com.saferenttechnologies.android.domain.model.PermissionRules
import com.saferenttechnologies.android.domain.model.PermissionStatus
import com.saferenttechnologies.android.testutil.FakePermissionManager
import com.saferenttechnologies.android.testutil.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LaunchPermissionsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val manager = FakePermissionManager()
    private val now = 1_800_000_000_000L

    private fun viewModel() = LaunchPermissionsViewModel(manager, NoOpLogger).also { it.now = { now } }

    private fun TestScope.effectsOf(vm: LaunchPermissionsViewModel): List<LaunchPermissionsEffect> {
        val effects = mutableListOf<LaunchPermissionsEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.effect.toList(effects) }
        return effects
    }

    private fun LaunchPermissionsViewModel.start(rationale: Map<AppPermission, Boolean> = emptyMap()) =
        onIntent(LaunchPermissionsIntent.Start(rationale))

    @Test
    fun `granted permissions skip the popup`() = runTest {
        manager.granted += setOf(AppPermission.LOCATION, AppPermission.NOTIFICATIONS)
        val vm = viewModel().apply { start() }
        assertNull(vm.state.value.prompt)
    }

    @Test
    fun `denied shows our popup first, location then notifications, Enable requests the system dialog`() = runTest {
        val vm = viewModel()
        val effects = effectsOf(vm)
        vm.start()

        assertEquals(AppPermission.LOCATION, vm.state.value.prompt?.permission)
        assertEquals(PermissionStatus.DENIED, vm.state.value.prompt?.status)

        vm.onIntent(LaunchPermissionsIntent.Enable)
        assertEquals(listOf<LaunchPermissionsEffect>(LaunchPermissionsEffect.RequestSystem(AppPermission.LOCATION)), effects)
        assertNull(vm.state.value.prompt) // system dialog on screen

        vm.onIntent(LaunchPermissionsIntent.SystemResult)
        assertEquals(AppPermission.NOTIFICATIONS, vm.state.value.prompt?.permission)
    }

    @Test
    fun `permanently denied shows Open settings`() = runTest {
        manager.asked += AppPermission.LOCATION // asked before + no rationale = permanently denied
        manager.granted += AppPermission.NOTIFICATIONS
        val vm = viewModel()
        val effects = effectsOf(vm)
        vm.start(rationale = mapOf(AppPermission.LOCATION to false))

        assertEquals(PermissionStatus.PERMANENTLY_DENIED, vm.state.value.prompt?.status)
        vm.onIntent(LaunchPermissionsIntent.Enable)
        assertEquals(listOf<LaunchPermissionsEffect>(LaunchPermissionsEffect.OpenSettings), effects)
    }

    @Test
    fun `Not now waits a day`() = runTest {
        manager.granted += AppPermission.NOTIFICATIONS
        manager.lastPrompt[AppPermission.LOCATION] = now - 60_000 // asked a minute ago
        assertNull(viewModel().apply { start() }.state.value.prompt)

        manager.lastPrompt[AppPermission.LOCATION] = now - PermissionRules.PROMPT_INTERVAL_MS
        assertEquals(AppPermission.LOCATION, viewModel().apply { start() }.state.value.prompt?.permission)
    }

    @Test
    fun `rules - status`() {
        assertEquals(PermissionStatus.GRANTED, PermissionRules.status(true, askedBefore = true, shouldShowRationale = false))
        assertEquals(PermissionStatus.DENIED, PermissionRules.status(false, askedBefore = false, shouldShowRationale = false))
        assertEquals(PermissionStatus.DENIED, PermissionRules.status(false, askedBefore = true, shouldShowRationale = true))
        assertEquals(PermissionStatus.PERMANENTLY_DENIED, PermissionRules.status(false, askedBefore = true, shouldShowRationale = false))
    }
}
