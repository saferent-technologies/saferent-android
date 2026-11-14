package com.saferenttechnologies.android.core.android

import android.app.Activity
import android.app.Application
import android.os.Bundle
import java.lang.ref.WeakReference
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The activity currently on screen (weakly held — never leaks). Firebase phone auth needs
 * an Activity for its reCAPTCHA / Play Integrity fallback; this keeps that Android detail
 * inside the data layer instead of passing activities through ViewModels.
 */
@Singleton
class CurrentActivityHolder @Inject constructor() : Application.ActivityLifecycleCallbacks {

    private var current: WeakReference<Activity>? = null

    val activity: Activity? get() = current?.get()?.takeUnless { it.isFinishing || it.isDestroyed }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = set(activity)
    override fun onActivityStarted(activity: Activity) = set(activity)
    override fun onActivityResumed(activity: Activity) = set(activity)
    override fun onActivityDestroyed(activity: Activity) {
        if (current?.get() === activity) current = null
    }
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

    private fun set(activity: Activity) {
        current = WeakReference(activity)
    }
}
