package com.saferenttechnologies.android

import android.Manifest
import android.app.Activity
import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.saferenttechnologies.android.domain.usecase.ForceRefreshAppConfigUseCase
import com.saferenttechnologies.android.domain.usecase.ScheduleSplashAssetsUpdateUseCase
import com.saferenttechnologies.android.domain.usecase.SyncAppConfigUseCase
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Hilt entry point. Syncs the app config once per process at startup. */
@HiltAndroidApp
class SafeRentApp : Application() {

    @Inject
    lateinit var syncAppConfig: SyncAppConfigUseCase

    @Inject
    lateinit var forceRefreshAppConfig: ForceRefreshAppConfigUseCase

    @Inject
    lateinit var scheduleSplashAssetsUpdate: ScheduleSplashAssetsUpdateUseCase

    /** Lives as long as the process. */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate() // Hilt injects fields here
        // Once per process: sends If-None-Match, 304 keeps cache, never throws.
        // Then, if the splash version changed, download the new splash in the background.
        appScope.launch {
            syncAppConfig()
            scheduleSplashAssetsUpdate()
        }
        if (BuildConfig.DEBUG) {
            Log.d(
                TAG,
                "Build: ${BuildConfig.BUILD_TYPE} v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) " +
                    "| API base URL: ${BuildConfig.API_BASE_URL}",
            )
            requestDebugPermissions()
        }
    }

    /**
     * Debug only, asked on the first screen of each launch while missing:
     * - POST_NOTIFICATIONS (Android 13+) → Chucker's "tap to inspect" notification.
     * - ACCESS_LOCAL_NETWORK (Android 17+, targetSdk 37) → reach the dev API on the
     *   laptop's LAN IP. Without it connections to 192.168.x.x silently time out.
     * The startup sync usually fires before the user answers the popup, so when
     * ACCESS_LOCAL_NETWORK gets granted we retry the config call once automatically.
     */
    private fun requestDebugPermissions() {
        val wanted = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
            if (Build.VERSION.SDK_INT >= API_ANDROID_17) {
                add(PERMISSION_ACCESS_LOCAL_NETWORK)
            }
        }
        if (wanted.isEmpty()) return

        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            private var asked = false
            private var waitingForLocalNetwork = false

            override fun onActivityResumed(activity: Activity) {
                if (!asked) {
                    asked = true
                    val missing = wanted.filter { !activity.isGranted(it) }
                    if (missing.isNotEmpty()) {
                        waitingForLocalNetwork = PERMISSION_ACCESS_LOCAL_NETWORK in missing
                        Log.d(TAG, "Requesting debug permissions: $missing")
                        ActivityCompat.requestPermissions(activity, missing.toTypedArray(), REQUEST_DEBUG_PERMISSIONS)
                    }
                    return
                }
                // The activity resumes again after the permission dialog closes.
                if (waitingForLocalNetwork && activity.isGranted(PERMISSION_ACCESS_LOCAL_NETWORK)) {
                    waitingForLocalNetwork = false
                    Log.d(TAG, "ACCESS_LOCAL_NETWORK granted → retrying config call")
                    appScope.launch {
                        forceRefreshAppConfig()
                        scheduleSplashAssetsUpdate()
                    }
                }
            }

            private fun Activity.isGranted(permission: String): Boolean =
                ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityStarted(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
    }

    private companion object {
        const val TAG = "SafeRent"
        const val REQUEST_DEBUG_PERMISSIONS = 9001
        const val API_ANDROID_17 = 37
        const val PERMISSION_ACCESS_LOCAL_NETWORK = "android.permission.ACCESS_LOCAL_NETWORK"
    }
}
