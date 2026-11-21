package com.saferenttechnologies.android.presentation.permission

import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import com.saferenttechnologies.android.core.log.AppLogger
import com.saferenttechnologies.android.core.mvi.MviViewModel
import com.saferenttechnologies.android.core.mvi.UiEffect
import com.saferenttechnologies.android.core.mvi.UiIntent
import com.saferenttechnologies.android.core.mvi.UiState
import com.saferenttechnologies.android.domain.model.AppPermission
import com.saferenttechnologies.android.domain.model.PermissionPrompt
import com.saferenttechnologies.android.domain.model.PermissionRules
import com.saferenttechnologies.android.domain.model.PermissionStatus
import com.saferenttechnologies.android.domain.repository.PermissionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

data class LaunchPermissionsState(
    val started: Boolean = false,
    /** Popup on screen now (one at a time). */
    val prompt: PermissionPrompt? = null,
    val queue: List<PermissionPrompt> = emptyList(),
) : UiState

sealed interface LaunchPermissionsIntent : UiIntent {
    /** [rationale] = Activity.shouldShowRequestPermissionRationale per permission. */
    data class Start(val rationale: Map<AppPermission, Boolean>) : LaunchPermissionsIntent
    data object Enable : LaunchPermissionsIntent
    data object NotNow : LaunchPermissionsIntent
    data object SystemResult : LaunchPermissionsIntent
}

sealed interface LaunchPermissionsEffect : UiEffect {
    data class RequestSystem(val permission: AppPermission) : LaunchPermissionsEffect
    data object OpenSettings : LaunchPermissionsEffect
}

/**
 * Once per launch, signed in + active: our popup for each missing permission (location, then
 * notifications), one at a time, before the system dialog. "Not now" → at most once a day.
 */
@HiltViewModel
class LaunchPermissionsViewModel @Inject constructor(
    private val permissions: PermissionManager,
    private val logger: AppLogger,
) : MviViewModel<LaunchPermissionsState, LaunchPermissionsIntent, LaunchPermissionsEffect>(LaunchPermissionsState()) {

    /** Overridable in tests. */
    internal var now: () -> Long = System::currentTimeMillis

    override suspend fun handleIntent(intent: LaunchPermissionsIntent) {
        when (intent) {
            is LaunchPermissionsIntent.Start -> start(intent.rationale)
            LaunchPermissionsIntent.Enable -> {
                val prompt = currentState.prompt ?: return
                permissions.markPrompted(prompt.permission, now())
                if (prompt.status == PermissionStatus.PERMANENTLY_DENIED) {
                    sendEffect(LaunchPermissionsEffect.OpenSettings)
                    next()
                } else {
                    permissions.markAsked(prompt.permission)
                    sendEffect(LaunchPermissionsEffect.RequestSystem(prompt.permission))
                    setState { copy(prompt = null) } // system dialog on screen; SystemResult → next
                }
            }
            LaunchPermissionsIntent.NotNow -> {
                currentState.prompt?.let { permissions.markPrompted(it.permission, now()) }
                next()
            }
            LaunchPermissionsIntent.SystemResult -> next()
        }
    }

    private suspend fun start(rationale: Map<AppPermission, Boolean>) {
        if (currentState.started) return // once per launch
        val queue = PermissionRules.LAUNCH_ORDER
            .filter { permissions.isSupported(it) && !permissions.isGranted(it) }
            .filter { PermissionRules.canPromptOnLaunch(permissions.lastPromptAt(it), now()) }
            .map { p ->
                val status = PermissionRules.status(granted = false, askedBefore = permissions.askedBefore(p), rationale[p] == true)
                // "Asked before + no rationale" is also what Android reports after an "Only this time"
                // grant ran out → always offer Android's own dialog first, never "Open settings" up front.
                PermissionPrompt(p, if (status == PermissionStatus.PERMANENTLY_DENIED) PermissionStatus.DENIED else status)
            }
        logger.d(TAG, "Launch permissions to ask: ${queue.map { "${it.permission}=${it.status}" }}")
        setState { LaunchPermissionsState(started = true, prompt = queue.firstOrNull(), queue = queue.drop(1)) }
    }

    private fun next() = setState { copy(prompt = queue.firstOrNull(), queue = queue.drop(1)) }

    private companion object {
        const val TAG = "SR-Permissions"
    }
}

/** Shows the launch popups over the dashboard. */
@Composable
fun LaunchPermissionsHost(viewModel: LaunchPermissionsViewModel) {
    val state by viewModel.state.collectAsState()
    val activity = LocalActivity.current
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        viewModel.onIntent(LaunchPermissionsIntent.SystemResult)
    }
    LaunchedEffect(Unit) {
        val rationale = AppPermission.entries.associateWith { p ->
            p.manifestNames.any { activity?.shouldShowRequestPermissionRationale(it) == true }
        }
        viewModel.onIntent(LaunchPermissionsIntent.Start(rationale))
    }
    LaunchedEffect(viewModel) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is LaunchPermissionsEffect.RequestSystem -> launcher.launch(effect.permission.manifestNames.toTypedArray())
                LaunchPermissionsEffect.OpenSettings -> context.openAppSettings()
            }
        }
    }
    state.prompt?.let { prompt ->
        PermissionRationaleDialog(
            prompt = prompt,
            onEnable = { viewModel.onIntent(LaunchPermissionsIntent.Enable) },
            onNotNow = { viewModel.onIntent(LaunchPermissionsIntent.NotNow) },
        )
    }
}
