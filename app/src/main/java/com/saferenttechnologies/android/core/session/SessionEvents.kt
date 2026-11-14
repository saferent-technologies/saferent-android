package com.saferenttechnologies.android.core.session

import com.saferenttechnologies.android.domain.model.AccountBlockedInfo
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

sealed interface SessionEvent {
    /** The user chose to sign out. */
    data object SignedOut : SessionEvent

    /** The server rejected our token (401) — sign out and show login. */
    data object Expired : SessionEvent

    /** 403 ACCOUNT_DELETED / ACCOUNT_DISABLED from any signed-in API → blocked screen. */
    data class AccountBlocked(val info: AccountBlockedInfo) : SessionEvent
}

/** App-wide session signals (e.g. from the network layer) that the login flow reacts to. */
@Singleton
class SessionEvents @Inject constructor() {
    private val _events = MutableSharedFlow<SessionEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<SessionEvent> = _events.asSharedFlow()

    fun emit(event: SessionEvent) {
        _events.tryEmit(event)
    }
}
