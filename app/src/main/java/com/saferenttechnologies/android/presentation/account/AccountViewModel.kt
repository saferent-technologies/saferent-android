package com.saferenttechnologies.android.presentation.account

import androidx.lifecycle.viewModelScope
import com.saferenttechnologies.android.core.mvi.MviViewModel
import com.saferenttechnologies.android.core.mvi.UiEffect
import com.saferenttechnologies.android.core.mvi.UiIntent
import com.saferenttechnologies.android.core.mvi.UiState
import com.saferenttechnologies.android.core.session.SessionEvent
import com.saferenttechnologies.android.core.session.SessionEvents
import com.saferenttechnologies.android.domain.model.LegalSettings
import com.saferenttechnologies.android.domain.model.SupportSettings
import com.saferenttechnologies.android.domain.repository.ConfigRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AccountState(
    val isOpen: Boolean = false,
    val support: SupportSettings = SupportSettings(),
    val legal: LegalSettings = LegalSettings(),
    val confirmSignOut: Boolean = false,
) : UiState

sealed interface AccountIntent : UiIntent {
    data object Open : AccountIntent
    data object Close : AccountIntent
    data class AskSignOut(val show: Boolean) : AccountIntent
}

sealed interface AccountEffect : UiEffect

/** Profile tab: who I am, role switch, shortcuts, help, legal, sign out. Support / legal come from app config. */
@HiltViewModel
class AccountViewModel @Inject constructor(
    private val config: ConfigRepository,
    sessionEvents: SessionEvents,
) : MviViewModel<AccountState, AccountIntent, AccountEffect>(AccountState()) {

    init {
        viewModelScope.launch { sessionEvents.events.collect { if (it == SessionEvent.SignedOut) setState { AccountState() } } }
    }

    override suspend fun handleIntent(intent: AccountIntent) {
        when (intent) {
            AccountIntent.Open -> {
                setState { copy(isOpen = true, confirmSignOut = false) }
                runCatching { config.getConfig() }.getOrNull()?.let { c -> setState { copy(support = c.support, legal = c.legal) } }
            }
            AccountIntent.Close -> setState { copy(isOpen = false, confirmSignOut = false) }
            is AccountIntent.AskSignOut -> setState { copy(confirmSignOut = intent.show) }
        }
    }
}
