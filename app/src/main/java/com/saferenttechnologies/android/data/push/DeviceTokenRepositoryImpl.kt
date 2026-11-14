package com.saferenttechnologies.android.data.push

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.android.gms.tasks.Task
import com.google.firebase.messaging.FirebaseMessaging
import com.saferenttechnologies.android.core.di.SessionPreferences
import com.saferenttechnologies.android.core.log.AppLogger
import com.saferenttechnologies.android.domain.repository.DeviceTokenRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

@Singleton
class DeviceTokenRepositoryImpl @Inject constructor(
    @SessionPreferences private val dataStore: DataStore<Preferences>,
    private val logger: AppLogger,
) : DeviceTokenRepository {

    override suspend fun currentToken(): String? = try {
        withTimeoutOrNull(TOKEN_TIMEOUT_MS) { FirebaseMessaging.getInstance().token.awaitOrNull() }
            ?.takeIf { it.isNotBlank() }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        logger.w(TAG, "FCM token unavailable: ${e::class.java.simpleName}", e)
        null
    }

    override suspend fun lastSentToken(): String? =
        dataStore.data
            .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
            .first()[KEY_LAST_SENT]

    override suspend fun saveLastSentToken(token: String) {
        runCatching { dataStore.edit { it[KEY_LAST_SENT] = token } }
    }

    override suspend fun clearLastSentToken() {
        runCatching { dataStore.edit { it.remove(KEY_LAST_SENT) } }
    }

    private companion object {
        const val TAG = "SafeRent-Push"
        const val TOKEN_TIMEOUT_MS = 10_000L
        val KEY_LAST_SENT = stringPreferencesKey("fcm_token_last_sent")
    }
}

/** Play-services Task → suspend (null on failure). Avoids an extra coroutines-play-services dependency. */
internal suspend fun <T> Task<T>.awaitOrNull(): T? = suspendCancellableCoroutine { cont ->
    addOnCompleteListener { task ->
        if (cont.isActive) cont.resume(if (task.isSuccessful) task.result else null)
    }
}
