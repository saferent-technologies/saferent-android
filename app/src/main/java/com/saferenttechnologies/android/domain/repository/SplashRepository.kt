package com.saferenttechnologies.android.domain.repository

import com.saferenttechnologies.android.domain.model.SplashAppearance
import com.saferenttechnologies.android.domain.model.SplashAssetsResult
import com.saferenttechnologies.android.domain.model.SplashSettings
import kotlinx.coroutines.flow.Flow

interface SplashRepository {
    /** Saved splash (or defaults). Local only. */
    suspend fun getAppearance(): SplashAppearance
    fun observeAppearance(): Flow<SplashAppearance>

    /** True when [remote] differs from what is saved (or saved files are missing). */
    suspend fun needsUpdate(remote: SplashSettings): Boolean

    /**
     * Downloads [remote] logo + Lottie into a fresh folder, then atomically saves
     * version/title/description/paths and permanently deletes every older asset folder.
     * On failure nothing changes (old splash stays). Never throws.
     */
    suspend fun updateAssets(remote: SplashSettings): SplashAssetsResult
}

/** Runs the splash asset download in the background, even if the app is closed. */
interface SplashAssetsScheduler {
    fun scheduleUpdate()
}
