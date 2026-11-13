package com.saferenttechnologies.android.core.mvi

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Marker for a screen's single immutable state. */
interface UiState

/** Marker for user/system actions sent to a ViewModel. */
interface UiIntent

/** Marker for one-off events (navigation, toasts, dialogs). */
interface UiEffect

/**
 * Base MVI ViewModel.
 *
 * - [state] is the single source of truth (immutable, updated only via [setState] with `copy`).
 * - [effect] delivers one-off events; each effect is consumed once.
 * - [onIntent] is the ONLY public entry point. Intents are queued in a Channel and
 *   handled strictly in order by [handleIntent].
 *
 * Long-running work that must not block the intent queue (e.g. collecting a Flow)
 * should be launched in [viewModelScope] from inside [handleIntent].
 */
abstract class MviViewModel<S : UiState, I : UiIntent, E : UiEffect>(initial: S) : ViewModel() {

    private val _state = MutableStateFlow(initial)
    val state: StateFlow<S> = _state.asStateFlow()

    private val _effect = Channel<E>(Channel.BUFFERED)
    val effect: Flow<E> = _effect.receiveAsFlow()

    private val intents = Channel<I>(Channel.UNLIMITED)

    init {
        viewModelScope.launch {
            for (intent in intents) handleIntent(intent)
        }
    }

    fun onIntent(intent: I) {
        intents.trySend(intent)
    }

    protected val currentState: S
        get() = _state.value

    protected fun setState(reducer: S.() -> S) {
        _state.update { it.reducer() }
    }

    protected fun sendEffect(effect: E) {
        _effect.trySend(effect)
    }

    protected abstract suspend fun handleIntent(intent: I)
}
