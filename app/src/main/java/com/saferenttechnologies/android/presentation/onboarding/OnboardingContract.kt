package com.saferenttechnologies.android.presentation.onboarding

import com.saferenttechnologies.android.core.mvi.UiEffect
import com.saferenttechnologies.android.core.mvi.UiIntent
import com.saferenttechnologies.android.core.mvi.UiState
import com.saferenttechnologies.android.domain.model.OnboardingContent
import com.saferenttechnologies.android.domain.model.OnboardingPage

data class OnboardingState(
    /** Decision made (show or skip) — the splash waits for this. */
    val isLoaded: Boolean = false,
    /** Finished, skipped, or not needed → go to the app. */
    val isFinished: Boolean = false,
    val content: OnboardingContent? = null,
    val currentPage: Int = 0,
    /** Videos start muted; the speaker button toggles sound. */
    val isMuted: Boolean = true,
) : UiState {
    val pages: List<OnboardingPage> get() = content?.pages.orEmpty()
    val isLastPage: Boolean get() = currentPage >= pages.lastIndex
}

sealed interface OnboardingIntent : UiIntent {
    data object Load : OnboardingIntent
    data class PageChanged(val page: Int) : OnboardingIntent
    data object Next : OnboardingIntent
    data object Skip : OnboardingIntent
    data object ToggleMute : OnboardingIntent
}

sealed interface OnboardingEffect : UiEffect {
    data class ScrollToPage(val page: Int) : OnboardingEffect
    data object Finished : OnboardingEffect
}
