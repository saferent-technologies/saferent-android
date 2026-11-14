package com.saferenttechnologies.android.testutil

import com.saferenttechnologies.android.domain.model.AccountUser
import com.saferenttechnologies.android.domain.model.AppResult
import com.saferenttechnologies.android.domain.model.ConsentStatus
import com.saferenttechnologies.android.domain.model.PhoneCompletion
import com.saferenttechnologies.android.domain.model.UserProfileRequest
import com.saferenttechnologies.android.domain.model.UserProfileResult
import com.saferenttechnologies.android.domain.model.UserRole
import com.saferenttechnologies.android.domain.repository.AccountRepository
import com.saferenttechnologies.android.domain.repository.DeviceTokenRepository

/** [calls] records the order of server calls ("logout:<token>", "fcm:<token>", …). */
class FakeAccountRepository(val calls: MutableList<String> = mutableListOf()) : AccountRepository {
    var completion: AppResult<PhoneCompletion> = AppResult.Success(PhoneCompletion())
    var meResult: AppResult<AccountUser> = AppResult.Success(AccountUser(id = "u1", role = UserRole.TENANT))
    var saveResult: AppResult<UserProfileResult>? = null
    var fcmResult: AppResult<Boolean> = AppResult.Success(true)
    var savedRole: UserRole? = null

    val requests = mutableListOf<UserProfileRequest>()
    val completeTokens = mutableListOf<String?>()
    val fcmTokens = mutableListOf<String>()
    var meCalls = 0
        private set

    override suspend fun completePhoneSignIn(fcmToken: String?): AppResult<PhoneCompletion> {
        completeTokens += fcmToken
        calls += "complete:$fcmToken"
        return completion
    }

    override suspend fun me(): AppResult<AccountUser> {
        meCalls++
        return meResult
    }

    override suspend fun saveUserProfile(request: UserProfileRequest): AppResult<UserProfileResult> {
        requests += request
        return saveResult ?: AppResult.Success(
            UserProfileResult(
                user = AccountUser(id = "u1", role = request.role, roles = listOfNotNull(request.role)),
                isProfileCompleted = request.isProfileCompleted,
                missingFields = emptyList(),
                role = request.role,
                roles = listOfNotNull(request.role),
                termsAccepted = request.consents != null,
                consents = ConsentStatus(),
                created = false,
            ),
        )
    }

    override suspend fun registerFcmToken(token: String): AppResult<Boolean> {
        fcmTokens += token
        calls += "fcm:$token"
        return fcmResult
    }

    override suspend fun logout(fcmToken: String?): AppResult<Unit> {
        calls += "logout:$fcmToken"
        return AppResult.Success(Unit)
    }

    override suspend fun getSavedActiveRole(): UserRole? = savedRole

    override suspend fun saveActiveRole(role: UserRole) {
        savedRole = role
    }
}

class FakeDeviceTokenRepository(val calls: MutableList<String> = mutableListOf()) : DeviceTokenRepository {
    /** null = Firebase couldn't give a token. */
    var token: String? = "fcm-token-1"
    var lastSent: String? = null

    override suspend fun currentToken(): String? = token
    override suspend fun lastSentToken(): String? = lastSent

    override suspend fun saveLastSentToken(token: String) {
        lastSent = token
    }

    override suspend fun clearLastSentToken() {
        lastSent = null
        calls += "clearToken"
    }
}
