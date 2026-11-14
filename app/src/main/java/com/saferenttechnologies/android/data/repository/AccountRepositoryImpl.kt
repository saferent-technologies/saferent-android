package com.saferenttechnologies.android.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.saferenttechnologies.android.core.di.AppVersionName
import com.saferenttechnologies.android.core.di.SessionPreferences
import com.saferenttechnologies.android.data.mapper.toAccountError
import com.saferenttechnologies.android.data.mapper.toDomain
import com.saferenttechnologies.android.data.mapper.toJson
import com.saferenttechnologies.android.data.mapper.toMeUser
import com.saferenttechnologies.android.data.remote.AccountApi
import com.saferenttechnologies.android.data.remote.ApiResult
import com.saferenttechnologies.android.data.remote.SafeApiCall
import com.saferenttechnologies.android.data.remote.dto.FcmTokenBody
import com.saferenttechnologies.android.data.remote.dto.LogoutBody
import com.saferenttechnologies.android.domain.model.AccountUser
import com.saferenttechnologies.android.domain.model.AppError
import com.saferenttechnologies.android.domain.model.AppResult
import com.saferenttechnologies.android.domain.model.PhoneCompletion
import com.saferenttechnologies.android.domain.model.UserProfileRequest
import com.saferenttechnologies.android.domain.model.UserProfileResult
import com.saferenttechnologies.android.domain.model.UserRole
import com.saferenttechnologies.android.domain.model.userRoleOf
import com.saferenttechnologies.android.domain.repository.AccountRepository
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AccountRepositoryImpl @Inject constructor(
    private val api: AccountApi,
    private val safeApiCall: SafeApiCall,
    private val json: Json,
    @SessionPreferences private val dataStore: DataStore<Preferences>,
    @AppVersionName private val appVersion: String,
) : AccountRepository {

    override suspend fun completePhoneSignIn(fcmToken: String?): AppResult<PhoneCompletion> {
        val result = safeApiCall("POST auth/phone/complete") {
            if (fcmToken.isNullOrBlank()) api.completePhone() else api.completePhone(FcmTokenBody(fcmToken, appVersion = appVersion))
        }
        return when (result) {
            is ApiResult.Success -> result.data?.let { AppResult.Success(it.toDomain()) } ?: AppResult.Failure(AppError.Parse())
            is ApiResult.NotModified -> AppResult.Failure(AppError.Parse())
            is ApiResult.Failure -> AppResult.Failure(result.error.toAccountError(json))
        }
    }

    override suspend fun me(): AppResult<AccountUser> =
        when (val r = safeApiCall("GET auth/me") { api.me() }) {
            is ApiResult.Success -> r.data?.toMeUser(json)?.let { AppResult.Success(it) }
                ?: AppResult.Failure(AppError.Parse())
            is ApiResult.NotModified -> AppResult.Failure(AppError.Parse())
            is ApiResult.Failure -> AppResult.Failure(r.error.toAccountError(json))
        }

    override suspend fun saveUserProfile(request: UserProfileRequest): AppResult<UserProfileResult> =
        when (val r = safeApiCall("POST userProfile") { api.saveUserProfile(request.toJson(appVersion)) }) {
            is ApiResult.Success -> r.data?.let { AppResult.Success(it.toDomain(r.code)) }
                ?: AppResult.Failure(AppError.Parse())
            is ApiResult.NotModified -> AppResult.Failure(AppError.Parse())
            is ApiResult.Failure -> AppResult.Failure(r.error.toAccountError(json))
        }

    override suspend fun registerFcmToken(token: String): AppResult<Boolean> =
        when (val r = safeApiCall("PUT devices/fcm-token") { api.registerFcmToken(FcmTokenBody(token, appVersion = appVersion)) }) {
            is ApiResult.Success -> AppResult.Success(r.data?.fcmTokenSaved ?: true)
            is ApiResult.NotModified -> AppResult.Success(true)
            is ApiResult.Failure -> AppResult.Failure(r.error.toAccountError(json))
        }

    override suspend fun logout(fcmToken: String?): AppResult<Unit> =
        when (val r = safeApiCall("POST auth/logout") { api.logout(LogoutBody(fcmToken)) }) {
            is ApiResult.Success, is ApiResult.NotModified -> AppResult.Success(Unit)
            is ApiResult.Failure -> AppResult.Failure(r.error)
        }

    override suspend fun getSavedActiveRole(): UserRole? =
        dataStore.data
            .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
            .first()[KEY_ACTIVE_ROLE]
            .let(::userRoleOf)

    override suspend fun saveActiveRole(role: UserRole) {
        runCatching { dataStore.edit { it[KEY_ACTIVE_ROLE] = role.name } }
    }

    private companion object {
        val KEY_ACTIVE_ROLE = stringPreferencesKey("active_role")
    }
}
