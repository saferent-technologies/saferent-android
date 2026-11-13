package com.saferenttechnologies.android.data.repository

import com.saferenttechnologies.android.core.di.IoDispatcher
import com.saferenttechnologies.android.core.log.AppLogger
import com.saferenttechnologies.android.data.local.SplashDataStore
import com.saferenttechnologies.android.data.remote.toAppError
import com.saferenttechnologies.android.data.splash.SplashAssetFiles
import com.saferenttechnologies.android.domain.model.SplashAppearance
import com.saferenttechnologies.android.domain.model.SplashAssetsResult
import com.saferenttechnologies.android.domain.model.SplashSettings
import com.saferenttechnologies.android.domain.model.SplashUpdatePolicy
import com.saferenttechnologies.android.domain.repository.SplashRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

@Singleton
class SplashRepositoryImpl @Inject constructor(
    private val store: SplashDataStore,
    private val files: SplashAssetFiles,
    private val logger: AppLogger,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : SplashRepository {

    private val mutex = Mutex()

    override suspend fun getAppearance(): SplashAppearance = store.read()

    override fun observeAppearance(): Flow<SplashAppearance> = store.appearance

    override suspend fun needsUpdate(remote: SplashSettings): Boolean = withContext(ioDispatcher) {
        val saved = store.read()
        SplashUpdatePolicy.needsUpdate(saved, remote, saved.filesExist())
    }

    override suspend fun updateAssets(remote: SplashSettings): SplashAssetsResult =
        withContext(ioDispatcher) {
            mutex.withLock {
                val saved = store.read()
                if (!SplashUpdatePolicy.needsUpdate(saved, remote, saved.filesExist())) {
                    return@withLock SplashAssetsResult.UpToDate
                }
                logger.d(TAG, "Updating splash ${saved.version} → ${remote.version}")

                val dir = files.newVersionDir(remote.version)
                try {
                    // Empty URL on the server = use the app's bundled logo / loading animation.
                    val logo = remote.logoUrl.takeIf { it.isNotBlank() }?.let { files.downloadLogo(it, dir) }
                    val lottie = remote.lottieUrl.takeIf { it.isNotBlank() }?.let { files.downloadLottie(it, dir) }

                    val updated = SplashAppearance(
                        version = remote.version,
                        title = remote.title.ifBlank { SplashAppearance.DEFAULT_TITLE },
                        description = remote.description.ifBlank { SplashAppearance.DEFAULT_DESCRIPTION },
                        logoPath = logo?.absolutePath,
                        lottiePath = lottie?.absolutePath,
                        minDurationMs = remote.minDurationMs.coerceIn(0L, SplashAppearance.MAX_MIN_DURATION_MS),
                    )
                    store.save(updated) // switch first …
                    files.deleteAllExcept(keep = dir) // … then permanently delete older versions
                    logger.d(TAG, "Splash ${remote.version} saved — shown from next launch: $updated")
                    SplashAssetsResult.Updated(remote.version)
                } catch (e: CancellationException) {
                    dir.deleteRecursively()
                    throw e
                } catch (e: Exception) {
                    dir.deleteRecursively() // keep the current splash untouched
                    logger.w(TAG, "Splash ${remote.version} download failed — keeping ${saved.version}", e)
                    SplashAssetsResult.Failed(e.toAppError())
                }
            }
        }

    private fun SplashAppearance.filesExist(): Boolean =
        listOfNotNull(logoPath, lottiePath).all { File(it).isFile }

    private companion object {
        const val TAG = "SafeRent-Splash"
    }
}
