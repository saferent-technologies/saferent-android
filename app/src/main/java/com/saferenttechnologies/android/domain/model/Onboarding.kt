package com.saferenttechnologies.android.domain.model

/** One onboarding page, ready to show. */
data class OnboardingPage(
    val id: String,
    val title: String,
    val description: String,
    val buttonText: String,
    /** Best first; the last one is always [OnboardingMedia.Bundled]. */
    val media: List<OnboardingMedia>,
)

/** Everything the onboarding screen needs. */
data class OnboardingContent(
    /** Server onboarding version, or [DefaultOnboarding.VERSION] for the built-in one. */
    val version: String,
    val skippable: Boolean,
    val skipButtonText: String,
    val pages: List<OnboardingPage>,
)

/** Whether onboarding is shown now — and if not, why. */
sealed interface OnboardingDecision {
    data class Show(val content: OnboardingContent) : OnboardingDecision
    data class Hidden(val reason: HiddenReason) : OnboardingDecision

    enum class HiddenReason {
        /** Server config `onboarding.enabled = false`. */
        DISABLED,

        /** `showOnce = true` and the user already finished or skipped it. */
        ALREADY_COMPLETED,
    }
}

/** Built-in onboarding: used when the server sends no onboarding screens. */
object DefaultOnboarding {
    const val VERSION = "bundled"
    const val SKIP = "Skip"
    const val NEXT = "Next"
    const val GET_STARTED = "Get started"

    val pages: List<OnboardingPage> = listOf(
        OnboardingPage(
            id = "default-verified",
            title = "Verified owners",
            description = "Every owner is verified with DigiLocker before their home goes live.",
            buttonText = NEXT,
            media = listOf(OnboardingMedia.Bundled),
        ),
        OnboardingPage(
            id = "default-rental",
            title = "Your rental in one place",
            description = "Lease, rent and receipts — all online, for tenants and owners.",
            buttonText = NEXT,
            media = listOf(OnboardingMedia.Bundled),
        ),
        OnboardingPage(
            id = "default-charges",
            title = "Every charge upfront",
            description = "Rent, deposit and maintenance are shown before you apply. No surprises.",
            buttonText = GET_STARTED,
            media = listOf(OnboardingMedia.Bundled),
        ),
    )
}
