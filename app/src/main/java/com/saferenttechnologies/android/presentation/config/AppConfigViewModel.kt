package com.saferenttechnologies.android.presentation.config

import androidx.lifecycle.viewModelScope
import com.saferenttechnologies.android.core.di.AppVersionName
import com.saferenttechnologies.android.core.mvi.MviViewModel
import com.saferenttechnologies.android.domain.model.ConfigSyncResult
import com.saferenttechnologies.android.domain.usecase.ForceRefreshAppConfigUseCase
import com.saferenttechnologies.android.domain.usecase.GetAppConfigUseCase
import com.saferenttechnologies.android.domain.usecase.ObserveAppConfigUseCase
import com.saferenttechnologies.android.domain.usecase.SyncAppConfigUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * App configuration MVI ViewModel. Ready to use, but not injected into any screen yet.
 *
 * - init only starts observing the cache (NO network).
 * - Network happens only on [AppConfigIntent.SyncOnce] / [AppConfigIntent.ForceRefresh].
 */
@HiltViewModel
class AppConfigViewModel @Inject constructor(
    private val observeAppConfig: ObserveAppConfigUseCase,
    private val syncAppConfig: SyncAppConfigUseCase,
    private val forceRefreshAppConfig: ForceRefreshAppConfigUseCase,
    private val getAppConfig: GetAppConfigUseCase,
    @AppVersionName private val currentVersionName: String,
) : MviViewModel<AppConfigState, AppConfigIntent, AppConfigEffect>(AppConfigState()) {

    private var observeJob: Job? = null

    init {
        onIntent(AppConfigIntent.Observe)
    }

    override suspend fun handleIntent(intent: AppConfigIntent) {
        when (intent) {
            AppConfigIntent.Observe -> startObserving()
            AppConfigIntent.SyncOnce -> sync { syncAppConfig() }
            AppConfigIntent.ForceRefresh -> sync { forceRefreshAppConfig() }
        }
    }

    /** Collects in its own coroutine so the intent queue keeps flowing. Idempotent. */
    private fun startObserving() {
        if (observeJob?.isActive == true) return
        observeJob = viewModelScope.launch {
            observeAppConfig().collect { config ->
                setState { copy(config = config, configVersion = config.configVersion) }
            }
        }
    }

    private suspend fun sync(call: suspend () -> ConfigSyncResult) {
        setState { copy(isSyncing = true) }
        val result = call()
        // Read the cache directly so effects use the freshly saved config
        // (the observer may not have emitted yet).
        val config = getAppConfig()
        setState {
            copy(
                isSyncing = false,
                lastSync = result,
                config = config,
                configVersion = config.configVersion,
            )
        }

        if (result is ConfigSyncResult.Failed) {
            sendEffect(AppConfigEffect.SyncFailed(result.error.message))
        }
        if (config.maintenance.enabled) {
            sendEffect(AppConfigEffect.MaintenanceOn)
        }
        if (config.app.isUpdateRequired(currentVersionName)) {
            sendEffect(AppConfigEffect.UpdateRequired)
        }
    }
}
