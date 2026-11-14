package com.saferenttechnologies.android.domain.repository

import com.saferenttechnologies.android.domain.model.AccountUser
import com.saferenttechnologies.android.domain.model.AppResult
import com.saferenttechnologies.android.domain.model.PhoneCompletion
import com.saferenttechnologies.android.domain.model.UserProfileRequest
import com.saferenttechnologies.android.domain.model.UserProfileResult
import com.saferenttechnologies.android.domain.model.UserRole

/** SafeRent account (backend user), not the Firebase session. */
interface AccountRepository {
    /** POST auth/phone/complete — [fcmToken] null → request sent with no body. */
    suspend fun completePhoneSignIn(fcmToken: String?): AppResult<PhoneCompletion>

    /** GET auth/me */
    suspend fun me(): AppResult<AccountUser>

    /** POST userProfile */
    suspend fun saveUserProfile(request: UserProfileRequest): AppResult<UserProfileResult>

    /** PUT devices/fcm-token → true when the server saved it. */
    suspend fun registerFcmToken(token: String): AppResult<Boolean>

    /** POST auth/logout { fcmToken } — server deletes this device's token. */
    suspend fun logout(fcmToken: String?): AppResult<Unit>

    suspend fun getSavedActiveRole(): UserRole?
    suspend fun saveActiveRole(role: UserRole)
}

/** This device's FCM token + the last one the server has. */
interface DeviceTokenRepository {
    /** Current FCM token, null if Firebase Messaging can't give one (never throws). */
    suspend fun currentToken(): String?

    suspend fun lastSentToken(): String?
    suspend fun saveLastSentToken(token: String)
    suspend fun clearLastSentToken()
}
