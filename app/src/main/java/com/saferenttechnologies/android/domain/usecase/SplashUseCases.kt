package com.saferenttechnologies.android.domain.usecase

import com.saferenttechnologies.android.domain.model.SplashAppearance
import com.saferenttechnologies.android.domain.model.SplashAssetsResult
import com.saferenttechnologies.android.domain.repository.ConfigRepository
import com.saferenttechnologies.android.domain.repository.SplashAssetsScheduler
import com.saferenttechnologies.android.domain.repository.SplashRepository
import javax.inject.Inject

/** What the splash shows now (saved assets or bundled defaults). No network. */
class GetSplashAppearanceUseCase @Inject constructor(private val splash: SplashRepository) {
    suspend operator fun invoke(): SplashAppearance = splash.getAppearance()
}

/**
 * Call after a config sync: compares the cached config's splash version with the saved one
 * and, if different, schedules a background download. Returns true when scheduled.
 */
class ScheduleSplashAssetsUpdateUseCase @Inject constructor(
    private val config: ConfigRepository,
    private val splash: SplashRepository,
    private val scheduler: SplashAssetsScheduler,
) {
    suspend operator fun invoke(): Boolean {
        val remote = config.getSplash()
        if (!remote.enabled) return false // splash off → don't spend data on its assets
        if (!splash.needsUpdate(remote)) return false
        scheduler.scheduleUpdate()
        return true
    }
}

/** Runs inside the background worker: downloads the cached config's splash if needed. */
class UpdateSplashAssetsUseCase @Inject constructor(
    private val config: ConfigRepository,
    private val splash: SplashRepository,
) {
    suspend operator fun invoke(): SplashAssetsResult {
        val remote = config.getSplash()
        if (!remote.enabled) return SplashAssetsResult.UpToDate
        return splash.updateAssets(remote)
    }
}
