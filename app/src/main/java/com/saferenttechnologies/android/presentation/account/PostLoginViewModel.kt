package com.saferenttechnologies.android.presentation.account

import androidx.lifecycle.viewModelScope
import com.saferenttechnologies.android.core.mvi.MviViewModel
import com.saferenttechnologies.android.core.mvi.UiEffect
import com.saferenttechnologies.android.core.mvi.UiIntent
import com.saferenttechnologies.android.core.mvi.UiState
import com.saferenttechnologies.android.core.session.SessionEvent
import com.saferenttechnologies.android.core.session.SessionEvents
import com.saferenttechnologies.android.domain.model.AppError
import com.saferenttechnologies.android.domain.model.AppResult
import com.saferenttechnologies.android.domain.usecase.HandleAccountBlockedUseCase
import com.saferenttechnologies.android.domain.model.PostLoginDestination
import com.saferenttechnologies.android.domain.usecase.ResolvePostLoginUseCase
import com.saferenttechnologies.android.domain.usecase.SignOutUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PostLoginState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val destination: PostLoginDestination? = null,
) : UiState

sealed interface PostLoginIntent : UiIntent {
    /** Signed in (OTP done or app start): phone/complete → auth/me → decide. */
    data object Load : PostLoginIntent
    data object Retry : PostLoginIntent
    data object SignOut : PostLoginIntent
}

sealed interface PostLoginEffect : UiEffect

/** Decides UserProfile vs dashboard after sign-in. */
@HiltViewModel
class PostLoginViewModel @Inject constructor(
    private val resolvePostLogin: ResolvePostLoginUseCase,
    private val signOut: SignOutUseCase,
    private val handleAccountBlocked: HandleAccountBlockedUseCase,
    sessionEvents: SessionEvents,
) : MviViewModel<PostLoginState, PostLoginIntent, PostLoginEffect>(PostLoginState()) {

    init {
        viewModelScope.launch {
            sessionEvents.events.collect { if (it == SessionEvent.SignedOut) setState { PostLoginState() } }
        }
    }

    override suspend fun handleIntent(intent: PostLoginIntent) {
        when (intent) {
            PostLoginIntent.Load, PostLoginIntent.Retry -> resolve()
            PostLoginIntent.SignOut -> signOut()
        }
    }

    private suspend fun resolve() {
        setState { PostLoginState(isLoading = true) }
        when (val r = resolvePostLogin()) {
            is AppResult.Success -> setState { PostLoginState(destination = r.data) }
            is AppResult.Failure -> {
                val error = r.error
                if (error is AppError.AccountBlocked) {
                    // Blocked screen (still signed in so "Try again" can re-check).
                    setState { PostLoginState() }
                    handleAccountBlocked(error.info)
                } else {
                    setState { PostLoginState(error = error.message) }
                }
            }
        }
    }
}
