package com.saferenttechnologies.android.presentation.config

import com.saferenttechnologies.android.core.mvi.UiEffect
import com.saferenttechnologies.android.core.mvi.UiIntent
import com.saferenttechnologies.android.core.mvi.UiState
import com.saferenttechnologies.android.domain.model.AppConfig
import com.saferenttechnologies.android.domain.model.ConfigSyncResult

data class AppConfigState(
    /** Defaults until DataStore emits. */
    val config: AppConfig = AppConfig(),
    val isSyncing: Boolean = false,
    val lastSync: ConfigSyncResult? = null,
    val configVersion: Int = 0,
) : UiState

sealed interface AppConfigIntent : UiIntent {
    /** Start collecting the cached config from DataStore (no network). */
    data object Observe : AppConfigIntent

    /** Network sync — at most once per app process. */
    data object SyncOnce : AppConfigIntent

    /** Manual refresh that ignores the once-per-process guard. */
    data object ForceRefresh : AppConfigIntent
}

sealed interface AppConfigEffect : UiEffect {
    data class SyncFailed(val message: String) : AppConfigEffect
    data object MaintenanceOn : AppConfigEffect
    data object UpdateRequired : AppConfigEffect
}
