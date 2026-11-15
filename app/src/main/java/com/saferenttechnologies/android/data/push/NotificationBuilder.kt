package com.saferenttechnologies.android.data.push

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.saferenttechnologies.android.MainActivity
import com.saferenttechnologies.android.R

/** Builds / shows the notification for the foreground case (the system shows background ones). */
object NotificationBuilder {

    fun openIntent(context: Context, payload: PushPayload): Intent =
        Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .apply { payload.extras.forEach { (k, v) -> putExtra(k, v) } }

    fun build(context: Context, payload: PushPayload): Notification {
        val pending = PendingIntent.getActivity(
            context,
            payload.androidId, // unique request code → each notification keeps its own extras
            openIntent(context, payload),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(context, NotificationChannels.DEFAULT)
            .setSmallIcon(R.drawable.ic_notifications)
            .setColor(ContextCompat.getColor(context, R.color.splash_background))
            .setContentTitle(payload.title.ifBlank { context.getString(R.string.app_name) })
            .setContentText(payload.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(payload.body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
    }

    /** false = notifications not allowed (Android 13+ permission) — nothing shown. */
    fun show(context: Context, payload: PushPayload): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false
        NotificationChannels.create(context)
        return runCatching { NotificationManagerCompat.from(context).notify(payload.androidId, build(context, payload)) }.isSuccess
    }
}
