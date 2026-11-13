package com.saferenttechnologies.android.domain.model

/**
 * What the splash screen shows right now. Local only — read from DataStore, never the network.
 *
 * Null paths = use the bundled default (logo vector / loading Lottie for the current theme).
 * A new server splash is downloaded in the background and only shown from the NEXT launch.
 */
data class SplashAppearance(
    /** Splash content version these assets belong to. "0" = bundled defaults. */
    val version: String = DEFAULT_VERSION,
    val title: String = DEFAULT_TITLE,
    val description: String = DEFAULT_DESCRIPTION,
    /** Downloaded light-theme logo, or null → bundled logo. */
    val lightLogoPath: String? = null,
    /** Downloaded dark-theme logo, or null → bundled logo. */
    val darkLogoPath: String? = null,
    /** Absolute path of the downloaded loading Lottie, or null for the bundled one. */
    val lottiePath: String? = null,
    val minDurationMs: Long = DEFAULT_MIN_DURATION_MS,
) {
    /** Logo file for the current theme, or null → bundled logo for that theme. */
    fun logoPath(darkTheme: Boolean): String? = if (darkTheme) darkLogoPath else lightLogoPath

    companion object {
        const val DEFAULT_VERSION = "0"
        const val DEFAULT_TITLE = "SafeRent"
        const val DEFAULT_DESCRIPTION = "Verified Rentals Across India"
        const val DEFAULT_MIN_DURATION_MS = 1500L
        const val MAX_MIN_DURATION_MS = 10_000L
    }
}

/** Outcome of a splash asset update. Never throws. */
sealed interface SplashAssetsResult {
    data class Updated(val version: String) : SplashAssetsResult
    data object UpToDate : SplashAssetsResult
    data class Failed(val error: AppError) : SplashAssetsResult
}

/** Pure rule for "should the splash assets be (re)downloaded?". */
object SplashUpdatePolicy {
    /**
     * @param savedFilesExist false when a saved asset file was removed (e.g. storage cleared).
     * @return true when the server version differs from the saved one (upgrade or rollback),
     *         or the same version is saved but its files are missing.
     *         A blank server version never triggers a download.
     */
    fun needsUpdate(saved: SplashAppearance, remote: SplashSettings, savedFilesExist: Boolean): Boolean {
        if (remote.version.isBlank()) return false
        if (AppInfo.compareVersions(remote.version, saved.version) != 0) return true
        return !savedFilesExist
    }
}
