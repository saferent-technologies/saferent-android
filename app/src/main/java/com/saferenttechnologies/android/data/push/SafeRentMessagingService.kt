package com.saferenttechnologies.android.data.push

import com.google.firebase.messaging.FirebaseMessagingService
import com.saferenttechnologies.android.domain.repository.AuthRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** FCM entry point. New token → queue PUT devices/fcm-token (only when signed in). */
@AndroidEntryPoint
class SafeRentMessagingService : FirebaseMessagingService() {

    @Inject lateinit var auth: AuthRepository
    @Inject lateinit var scheduler: FcmTokenScheduler

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        // Signed out → nothing to do; phone/complete sends the token at the next sign-in.
        if (auth.currentUser() != null) scheduler.schedule(token)
    }
}
