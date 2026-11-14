package com.saferenttechnologies.android.core.android

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent

/** Opens [url] in a Custom Tab (falls back to any browser). Blank URLs are ignored. */
fun Context.openInCustomTab(url: String) {
    if (url.isBlank()) return
    val uri = Uri.parse(url)
    try {
        CustomTabsIntent.Builder().setShowTitle(true).build().launchUrl(this, uri)
    } catch (e: ActivityNotFoundException) {
        runCatching {
            startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}
