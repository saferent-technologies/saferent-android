package com.saferenttechnologies.android.presentation.onboarding

import com.saferenttechnologies.android.core.log.AppLogger
import com.saferenttechnologies.android.core.mvi.MviViewModel
import com.saferenttechnologies.android.domain.model.OnboardingDecision
import com.saferenttechnologies.android.domain.usecase.CompleteOnboardingUseCase
import com.saferenttechnologies.android.domain.usecase.GetOnboardingUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val getOnboarding: GetOnboardingUseCase,
    private val completeOnboarding: CompleteOnboardingUseCase,
    private val logger: AppLogger,
) : MviViewModel<OnboardingState, OnboardingIntent, OnboardingEffect>(OnboardingState()) {

    // No auto-load: MainActivity sends Load once the splash is done, so the decision uses
    // the config the splash waited for (first launch).

    override suspend fun handleIntent(intent: OnboardingIntent) {
        when (intent) {
            OnboardingIntent.Load -> load()
            is OnboardingIntent.PageChanged -> setState {
                copy(currentPage = intent.page.coerceIn(0, (pages.size - 1).coerceAtLeast(0)))
            }
            OnboardingIntent.Next -> next()
            OnboardingIntent.Skip -> if (currentState.content?.skippable == true) finish()
            OnboardingIntent.ToggleMute -> setState { copy(isMuted = !isMuted) }
        }
    }

    private suspend fun load() {
        if (currentState.isLoaded) return
        when (val decision = getOnboarding()) {
            is OnboardingDecision.Hidden -> {
                logger.d(TAG, "Onboarding not shown: ${decision.reason}")
                setState { copy(isLoaded = true, isFinished = true) }
            }
            is OnboardingDecision.Show -> {
                val content = decision.content
                logger.d(TAG, "Showing onboarding v${content.version} (${content.pages.size} pages)")
                setState { copy(isLoaded = true, content = content, currentPage = 0) }
            }
        }
    }

    private suspend fun next() {
        val state = currentState
        if (state.content == null || state.isFinished) return
        if (state.isLastPage) {
            finish()
        } else {
            val nextPage = state.currentPage + 1
            setState { copy(currentPage = nextPage) }
            sendEffect(OnboardingEffect.ScrollToPage(nextPage))
        }
    }

    private suspend fun finish() {
        val state = currentState
        if (state.isFinished) return
        state.content?.let { completeOnboarding(it.version) }
        setState { copy(isFinished = true) }
        sendEffect(OnboardingEffect.Finished)
    }

    private companion object {
        const val TAG = "SafeRent-Onboarding"
    }
}
