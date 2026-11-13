package com.saferenttechnologies.android.domain.usecase

import com.saferenttechnologies.android.domain.model.DefaultOnboarding
import com.saferenttechnologies.android.domain.model.OnboardingContent
import com.saferenttechnologies.android.domain.model.OnboardingDecision
import com.saferenttechnologies.android.domain.model.OnboardingDecision.HiddenReason
import com.saferenttechnologies.android.domain.model.OnboardingPage
import com.saferenttechnologies.android.domain.repository.ConfigRepository
import com.saferenttechnologies.android.domain.repository.OnboardingRepository
import javax.inject.Inject

/**
 * Whether to show onboarding now (with its content) or why not. Reads the cached config only (no network).
 *
 * - `enabled = false` → Hidden(DISABLED)
 * - `showOnce = true` and already finished/skipped → Hidden(ALREADY_COMPLETED)
 * - no screens from the server (first install, empty cache) → built-in default screens
 * - empty button / skip texts → defaults ("Next", last page "Get started", "Skip")
 */
class GetOnboardingUseCase @Inject constructor(
    private val config: ConfigRepository,
    private val onboarding: OnboardingRepository,
) {
    suspend operator fun invoke(): OnboardingDecision {
        val settings = config.getOnboarding()
        if (!settings.enabled) return OnboardingDecision.Hidden(HiddenReason.DISABLED)
        if (settings.showOnce && onboarding.isCompleted()) {
            return OnboardingDecision.Hidden(HiddenReason.ALREADY_COMPLETED)
        }

        val serverPages = settings.screens.mapIndexed { index, screen ->
            val isLast = index == settings.screens.lastIndex
            OnboardingPage(
                id = screen.id.ifBlank { "screen-$index" },
                title = screen.title,
                description = screen.description,
                buttonText = screen.buttonText.ifBlank {
                    if (isLast) DefaultOnboarding.GET_STARTED else DefaultOnboarding.NEXT
                },
                media = screen.mediaCandidates,
            )
        }
        val useDefault = serverPages.isEmpty()
        val content = OnboardingContent(
            version = if (useDefault) DefaultOnboarding.VERSION else settings.version,
            skippable = settings.skippable,
            skipButtonText = settings.skipButtonText.ifBlank { DefaultOnboarding.SKIP },
            pages = if (useDefault) DefaultOnboarding.pages else serverPages,
        )
        return OnboardingDecision.Show(content)
    }
}

/** Finish or skip: never show again while `showOnce` is on. */
class CompleteOnboardingUseCase @Inject constructor(
    private val onboarding: OnboardingRepository,
) {
    suspend operator fun invoke(version: String) = onboarding.markCompleted(version)
}
