package com.saferenttechnologies.android.presentation.tenant

import androidx.lifecycle.viewModelScope
import com.saferenttechnologies.android.core.mvi.MviViewModel
import com.saferenttechnologies.android.core.mvi.UiEffect
import com.saferenttechnologies.android.core.mvi.UiIntent
import com.saferenttechnologies.android.core.mvi.UiState
import com.saferenttechnologies.android.core.session.SessionEvent
import com.saferenttechnologies.android.core.session.SessionEvents
import com.saferenttechnologies.android.domain.model.AppError
import com.saferenttechnologies.android.domain.model.AppResult
import com.saferenttechnologies.android.domain.model.TenantApplication
import com.saferenttechnologies.android.domain.repository.BrowseRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MyApplicationsState(
    val isOpen: Boolean = false,
    val status: String = "PENDING",
    val items: List<TenantApplication> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    val message: String? = null,
    val busyId: String? = null,
) : UiState

sealed interface MyApplicationsIntent : UiIntent {
    data object Open : MyApplicationsIntent
    data object Close : MyApplicationsIntent
    data class SelectStatus(val status: String) : MyApplicationsIntent
    data object Refresh : MyApplicationsIntent
    data class Withdraw(val id: String) : MyApplicationsIntent
    data object MessageShown : MyApplicationsIntent
}

sealed interface MyApplicationsEffect : UiEffect

@HiltViewModel
class MyApplicationsViewModel @Inject constructor(
    private val repository: BrowseRepository,
    sessionEvents: SessionEvents,
) : MviViewModel<MyApplicationsState, MyApplicationsIntent, MyApplicationsEffect>(MyApplicationsState()) {

    init {
        viewModelScope.launch { sessionEvents.events.collect { if (it == SessionEvent.SignedOut) setState { MyApplicationsState() } } }
    }

    override suspend fun handleIntent(intent: MyApplicationsIntent) {
        when (intent) {
            MyApplicationsIntent.Open -> {
                setState { copy(isOpen = true) }
                load()
            }
            MyApplicationsIntent.Close -> setState { copy(isOpen = false) }
            is MyApplicationsIntent.SelectStatus -> {
                setState { copy(status = intent.status, items = emptyList()) }
                load()
            }
            MyApplicationsIntent.Refresh -> if (currentState.isOpen) load()
            is MyApplicationsIntent.Withdraw -> {
                setState { copy(busyId = intent.id) }
                val r = repository.withdraw(intent.id)
                setState { copy(busyId = null, message = (r as? AppResult.Failure)?.error?.takeUnless { it is AppError.AccountBlocked }?.message ?: (r as? AppResult.Success)?.data) }
                load()
            }
            MyApplicationsIntent.MessageShown -> setState { copy(message = null) }
        }
    }

    private suspend fun load() {
        setState { copy(loading = true, error = null) }
        when (val r = repository.myApplications(currentState.status)) {
            is AppResult.Success -> setState { copy(loading = false, items = r.data) }
            is AppResult.Failure -> setState { copy(loading = false, error = r.error.takeUnless { it is AppError.AccountBlocked }?.message) }
        }
    }

    companion object {
        val STATUSES = listOf("PENDING", "APPROVED", "REJECTED", "WITHDRAWN")
    }
}
