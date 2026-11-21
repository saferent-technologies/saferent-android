package com.saferenttechnologies.android.presentation.safety

import androidx.lifecycle.viewModelScope
import com.saferenttechnologies.android.core.mvi.MviViewModel
import com.saferenttechnologies.android.core.mvi.UiEffect
import com.saferenttechnologies.android.core.mvi.UiIntent
import com.saferenttechnologies.android.core.mvi.UiState
import com.saferenttechnologies.android.core.session.SessionEvent
import com.saferenttechnologies.android.core.session.SessionEvents
import com.saferenttechnologies.android.domain.model.AppError
import com.saferenttechnologies.android.domain.model.AppResult
import com.saferenttechnologies.android.domain.model.EmergencyInfo
import com.saferenttechnologies.android.domain.model.SafetyRules
import com.saferenttechnologies.android.domain.repository.SafetyRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

data class EmergencyState(
    val isOpen: Boolean = false,
    val leaseId: String? = null,
    val info: EmergencyInfo? = null,
    val loading: Boolean = false,
    /** Showing the saved copy because the server couldn't be reached. */
    val offline: Boolean = false,
    /** LEASE_ENDED / not found: message, nothing else shown. */
    val ended: String? = null,
    /** No saved copy and the load failed. */
    val error: String? = null,
) : UiState

sealed interface EmergencyIntent : UiIntent {
    data class Open(val leaseId: String) : EmergencyIntent
    data object Close : EmergencyIntent
    data object Refresh : EmergencyIntent
    /** No active lease any more → the saved copy goes. */
    data object Forget : EmergencyIntent
}

sealed interface EmergencyEffect : UiEffect

/** Tenant Emergency screen: saved copy first (works offline), then the server. */
@HiltViewModel
class EmergencyViewModel @Inject constructor(
    private val repository: SafetyRepository,
    sessionEvents: SessionEvents,
) : MviViewModel<EmergencyState, EmergencyIntent, EmergencyEffect>(EmergencyState()) {

    init {
        viewModelScope.launch {
            sessionEvents.events.collect {
                if (it == SessionEvent.SignedOut) {
                    repository.clearEmergency()
                    setState { EmergencyState() }
                }
            }
        }
    }

    override suspend fun handleIntent(intent: EmergencyIntent) {
        when (intent) {
            is EmergencyIntent.Open -> {
                val cached = repository.cachedEmergency(intent.leaseId)
                setState { EmergencyState(isOpen = true, leaseId = intent.leaseId, info = cached) }
                load()
            }
            EmergencyIntent.Close -> setState { EmergencyState() }
            EmergencyIntent.Refresh -> load()
            EmergencyIntent.Forget -> repository.clearEmergency()
        }
    }

    private suspend fun load() {
        val id = currentState.leaseId ?: return
        if (currentState.loading) return
        setState { copy(loading = true, error = null) }
        when (val r = repository.emergency(id)) {
            is AppResult.Success -> setState { copy(info = r.data, loading = false, offline = false, ended = null) }
            is AppResult.Failure -> {
                val e = r.error
                when {
                    e is AppError.AccountBlocked -> setState { copy(loading = false) }
                    SafetyRules.leaseGone(e) -> setState { copy(info = null, loading = false, offline = false, ended = e.message) }
                    info != null -> setState { copy(loading = false, offline = true) }
                    else -> setState { copy(loading = false, error = e.message) }
                }
            }
        }
    }

    private val info get() = currentState.info
}
