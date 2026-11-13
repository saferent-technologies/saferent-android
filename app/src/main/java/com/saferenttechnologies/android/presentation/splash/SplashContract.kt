package com.saferenttechnologies.android.presentation.splash

import com.saferenttechnologies.android.core.mvi.UiEffect
import com.saferenttechnologies.android.core.mvi.UiIntent
import com.saferenttechnologies.android.core.mvi.UiState
import com.saferenttechnologies.android.domain.model.SplashAppearance

data class SplashState(
    val appearance: SplashAppearance = SplashAppearance(),
    /** False until the saved splash is read — the system splash stays up until then. */
    val isReady: Boolean = false,
    /** True after minDurationMs — time to leave the splash. */
    val isFinished: Boolean = false,
) : UiState

sealed interface SplashIntent : UiIntent {
    data object Start : SplashIntent
}

sealed interface SplashEffect : UiEffect {
    data object Finished : SplashEffect
}
