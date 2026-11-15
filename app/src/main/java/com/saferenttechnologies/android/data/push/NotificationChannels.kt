package com.saferenttechnologies.android.data.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.saferenttechnologies.android.R

/** "saferent_default" — also FCM's default channel (manifest meta-data). Safe to call many times. */
object NotificationChannels {
    const val DEFAULT = "saferent_default"
    private const val OLD_GENERAL = "saferent_general"

    fun create(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(DEFAULT, context.getString(R.string.notification_channel_name), NotificationManager.IMPORTANCE_HIGH).apply {
            description = context.getString(R.string.notification_channel_description)
            enableVibration(true)
            // Default sound is on for IMPORTANCE_HIGH.
        }
        manager.createNotificationChannel(channel)
        // The earlier build used another id → remove it so settings show one SafeRent channel.
        manager.deleteNotificationChannel(OLD_GENERAL)
    }
}
