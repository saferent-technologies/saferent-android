package com.saferenttechnologies.android.testutil

import com.saferenttechnologies.android.domain.model.SplashAppearance
import com.saferenttechnologies.android.domain.model.SplashAssetsResult
import com.saferenttechnologies.android.domain.model.SplashSettings
import com.saferenttechnologies.android.domain.model.SplashUpdatePolicy
import com.saferenttechnologies.android.domain.repository.SplashAssetsScheduler
import com.saferenttechnologies.android.domain.repository.SplashRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeSplashRepository(initial: SplashAppearance = SplashAppearance()) : SplashRepository {
    val appearance = MutableStateFlow(initial)
    var filesExist = true

    override suspend fun getAppearance(): SplashAppearance = appearance.value
    override fun observeAppearance(): Flow<SplashAppearance> = appearance
    override suspend fun needsUpdate(remote: SplashSettings): Boolean =
        SplashUpdatePolicy.needsUpdate(appearance.value, remote, filesExist)

    override suspend fun updateAssets(remote: SplashSettings): SplashAssetsResult =
        SplashAssetsResult.UpToDate
}

class FakeSplashAssetsScheduler : SplashAssetsScheduler {
    var scheduledCount = 0
        private set

    override fun scheduleUpdate() {
        scheduledCount++
    }
}
