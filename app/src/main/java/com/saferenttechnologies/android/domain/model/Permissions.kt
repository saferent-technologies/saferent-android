package com.saferenttechnologies.android.domain.model

/** Runtime permissions the app asks for, always behind our own rationale popup first. */
enum class AppPermission(val manifestNames: List<String>, val minSdk: Int = 1) {
    LOCATION(listOf("android.permission.ACCESS_FINE_LOCATION", "android.permission.ACCESS_COARSE_LOCATION")),
    NOTIFICATIONS(listOf("android.permission.POST_NOTIFICATIONS"), minSdk = 33),
    CAMERA(listOf("android.permission.CAMERA")),
}

enum class PermissionStatus { GRANTED, DENIED, PERMANENTLY_DENIED }

/** What the rationale popup shows: "Enable" (system dialog) or "Open settings". */
data class PermissionPrompt(val permission: AppPermission, val status: PermissionStatus)

object PermissionRules {
    /** Asked on launch, in this order. Camera is asked only when taking the first photo. */
    val LAUNCH_ORDER = listOf(AppPermission.LOCATION, AppPermission.NOTIFICATIONS)

    /** "Not now" → ask again at most once per day. */
    const val PROMPT_INTERVAL_MS = 24L * 60 * 60 * 1000

    /**
     * Android only says "permanently denied" indirectly: we asked before and the system no longer
     * wants a rationale → the dialog won't show again, only Settings can grant it.
     */
    fun status(granted: Boolean, askedBefore: Boolean, shouldShowRationale: Boolean): PermissionStatus = when {
        granted -> PermissionStatus.GRANTED
        askedBefore && !shouldShowRationale -> PermissionStatus.PERMANENTLY_DENIED
        else -> PermissionStatus.DENIED
    }

    fun canPromptOnLaunch(lastPromptAt: Long?, now: Long): Boolean =
        lastPromptAt == null || now - lastPromptAt >= PROMPT_INTERVAL_MS
}
