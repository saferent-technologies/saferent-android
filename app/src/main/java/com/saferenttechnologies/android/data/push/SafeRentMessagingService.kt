package com.saferenttechnologies.android.data.push

import android.content.Context
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.saferenttechnologies.android.R
import com.saferenttechnologies.android.core.log.AppLogger
import com.saferenttechnologies.android.domain.repository.AuthRepository
import com.saferenttechnologies.android.domain.usecase.IncomingPush
import com.saferenttechnologies.android.domain.usecase.NotificationEvents
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * FCM entry point.
 * - New token → queue PUT devices/fcm-token (only when signed in; otherwise phone/complete sends it).
 * - Message while the app is OPEN → we show it ourselves (the system shows it in the background / killed)
 *   and tell the app (bell badge, list, in-app banner).
 * Never logs the token, title or body.
 */
@AndroidEntryPoint
class SafeRentMessagingService : FirebaseMessagingService() {

    @Inject lateinit var auth: AuthRepository
    @Inject lateinit var scheduler: FcmTokenScheduler
    @Inject lateinit var events: NotificationEvents
    @Inject lateinit var logger: AppLogger

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        val signedIn = auth.currentUser() != null
        logger.d(TAG, "new FCM token (signedIn=$signedIn)")
        if (signedIn) scheduler.schedule(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val data = message.data
        val payload = PushPayload.from(message.notification?.title, message.notification?.body, data, propertyStatusText(this, data))
        logger.d(TAG, "push received type=${payload?.type ?: data["type"].orEmpty()}")
        payload ?: return
        NotificationBuilder.show(this, payload)
        events.newNotification(IncomingPush(payload.notificationId, payload.title, payload.link, payload.type))
    }

    companion object {
        private const val TAG = "SR-Push"

        /**
         * Old property status pushes (data: type = PROPERTY_STATUS_CHANGED, status, propertyTitle)
         * that carry no title / body of their own.
         */
        fun propertyStatusText(context: Context, data: Map<String, String>): Pair<String, String>? {
            if (data["type"]?.uppercase() != "PROPERTY_STATUS_CHANGED") return null
            val name = data["propertyTitle"]?.takeIf { it.isNotBlank() } ?: data["propertyCode"].orEmpty()
            val (titleRes, bodyRes) = when (data["status"]?.uppercase()) {
                "APPROVED", "VERIFIED" -> R.string.push_property_approved_title to R.string.push_property_approved_body
                "REJECTED" -> R.string.push_property_rejected_title to R.string.push_property_rejected_body
                "CHANGES_REQUESTED", "NEEDS_CHANGES" -> R.string.push_property_changes_title to R.string.push_property_changes_body
                "PUBLISHED", "LIVE" -> R.string.push_property_published_title to R.string.push_property_published_body
                else -> R.string.push_property_updated_title to R.string.push_property_updated_body
            }
            return context.getString(titleRes) to context.getString(bodyRes, name)
        }
    }
}
