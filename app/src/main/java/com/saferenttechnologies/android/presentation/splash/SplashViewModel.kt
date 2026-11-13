package com.saferenttechnologies.android.presentation.splash

import androidx.lifecycle.viewModelScope
import com.saferenttechnologies.android.core.mvi.MviViewModel
import com.saferenttechnologies.android.domain.usecase.GetSplashAppearanceUseCase
import com.saferenttechnologies.android.domain.usecase.GetSplashConfigUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Shows the saved splash (or bundled defaults) for minDurationMs. Local only — the
 * network side (download of a newer splash) runs separately in SplashAssetsWorker.
 *
 * `splash.enabled` is read from the cached config (not the splash store) so an admin
 * can turn the splash off/on without bumping its version. Disabled → skip straight on.
 */
@HiltViewModel
class SplashViewModel @Inject constructor(
    private val getSplashAppearance: GetSplashAppearanceUseCase,
    private val getSplashConfig: GetSplashConfigUseCase,
) : MviViewModel<SplashState, SplashIntent, SplashEffect>(SplashState()) {

    private var started = false

    init {
        onIntent(SplashIntent.Start)
    }

    override suspend fun handleIntent(intent: SplashIntent) {
        when (intent) {
            SplashIntent.Start -> start()
        }
    }

    private suspend fun start() {
        if (started) return
        started = true
        if (!getSplashConfig().enabled) {
            setState { copy(isReady = true, isFinished = true) }
            sendEffect(SplashEffect.Finished)
            return
        }
        val appearance = getSplashAppearance()
        setState { copy(appearance = appearance, isReady = true) }
        viewModelScope.launch {
            delay(appearance.minDurationMs)
            setState { copy(isFinished = true) }
            sendEffect(SplashEffect.Finished)
        }
    }
}
