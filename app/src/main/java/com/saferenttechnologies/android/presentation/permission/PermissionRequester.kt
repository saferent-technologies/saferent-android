package com.saferenttechnologies.android.presentation.permission

import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import com.saferenttechnologies.android.domain.model.AppPermission
import com.saferenttechnologies.android.domain.model.PermissionPrompt
import com.saferenttechnologies.android.domain.model.PermissionRules
import com.saferenttechnologies.android.domain.model.PermissionStatus
import com.saferenttechnologies.android.domain.repository.PermissionManager
import kotlinx.coroutines.launch

/** Provided once in MainActivity (Hilt-injected PermissionManager). */
val LocalPermissionManager = staticCompositionLocalOf<PermissionManager?> { null }

/** Asks one permission on demand (camera on first photo, location for GPS) — popup first. */
class PermissionRequester internal constructor(private val start: (onResult: (Boolean) -> Unit) -> Unit) {
    /** Already granted → onResult(true) straight away. */
    fun request(onResult: (granted: Boolean) -> Unit) = start(onResult)
}

/**
 * Remember this inside the screen that needs [permission]; it also draws the rationale popup.
 * Enable → system dialog; permanently denied → Settings; Not now → onResult(false).
 */
@Composable
fun rememberPermissionRequester(permission: AppPermission): PermissionRequester {
    val manager = LocalPermissionManager.current
    val activity = LocalActivity.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var prompt by remember { mutableStateOf<PermissionPrompt?>(null) }
    var pending by remember { mutableStateOf<((Boolean) -> Unit)?>(null) }
    val currentManager by rememberUpdatedState(manager)

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        val granted = result.values.any { it }
        pending?.invoke(granted)
        pending = null
    }

    prompt?.let { p ->
        PermissionRationaleDialog(
            prompt = p,
            onEnable = {
                prompt = null
                val m = currentManager
                scope.launch {
                    m?.markPrompted(permission, System.currentTimeMillis())
                    if (p.status == PermissionStatus.PERMANENTLY_DENIED) {
                        context.openAppSettings()
                        pending?.invoke(false)
                        pending = null
                    } else {
                        m?.markAsked(permission)
                        launcher.launch(permission.manifestNames.toTypedArray())
                    }
                }
            },
            onNotNow = {
                prompt = null
                scope.launch { currentManager?.markPrompted(permission, System.currentTimeMillis()) }
                pending?.invoke(false)
                pending = null
            },
        )
    }

    return remember(permission) {
        PermissionRequester { onResult ->
            val m = currentManager
            if (m == null || m.isGranted(permission)) {
                onResult(true) // granted (or no manager in previews)
                return@PermissionRequester
            }
            scope.launch {
                val rationale = permission.manifestNames.any { activity?.shouldShowRequestPermissionRationale(it) == true }
                val status = PermissionRules.status(granted = false, askedBefore = m.askedBefore(permission), rationale)
                pending = onResult
                prompt = PermissionPrompt(permission, status)
            }
        }
    }
}
