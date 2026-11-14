package com.saferenttechnologies.android.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.saferenttechnologies.android.core.di.SessionPreferences
import com.saferenttechnologies.android.domain.model.LoginConsent
import com.saferenttechnologies.android.domain.repository.SessionRepository
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** Preferences DataStore "saferent_session". */
@Singleton
class SessionRepositoryImpl @Inject constructor(
    @SessionPreferences private val dataStore: DataStore<Preferences>,
) : SessionRepository {

    override suspend fun saveLoginChoices(
        whatsappOptIn: Boolean,
        termsAcceptedAt: Long,
        termsVersion: String,
        privacyVersion: String,
    ) {
        runCatching {
            dataStore.edit { p ->
                p[KEY_TERMS_VERSION] = termsVersion
                p[KEY_PRIVACY_VERSION] = privacyVersion
                p[KEY_WHATSAPP_OPT_IN] = whatsappOptIn
                p[KEY_TERMS_ACCEPTED_AT] = termsAcceptedAt
            }
        }
    }

    override suspend fun getLoginConsent(): LoginConsent? {
        val prefs = dataStore.data
            .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
            .first()
        val at = prefs[KEY_TERMS_ACCEPTED_AT] ?: return null
        return LoginConsent(termsAccepted = true, whatsappOptIn = prefs[KEY_WHATSAPP_OPT_IN] ?: false, consentTimeMillis = at)
    }

    /** Pending login consent is gone; the accepted versions stay (info only). */
    override suspend fun clearLoginConsent() {
        runCatching {
            dataStore.edit {
                it.remove(KEY_TERMS_ACCEPTED_AT)
                it.remove(KEY_WHATSAPP_OPT_IN)
            }
        }
    }

    override suspend fun hasAcceptedTerms(termsVersion: String, privacyVersion: String): Boolean {
        val prefs = dataStore.data
            .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
            .first()
        return prefs[KEY_TERMS_VERSION] == termsVersion && prefs[KEY_PRIVACY_VERSION] == privacyVersion
    }

    companion object {
        const val NAME = "saferent_session"
        private val KEY_WHATSAPP_OPT_IN = booleanPreferencesKey("whatsapp_opt_in")
        private val KEY_TERMS_ACCEPTED_AT = longPreferencesKey("terms_accepted_at")
        private val KEY_TERMS_VERSION = stringPreferencesKey("terms_accepted_version")
        private val KEY_PRIVACY_VERSION = stringPreferencesKey("privacy_accepted_version")
    }
}
