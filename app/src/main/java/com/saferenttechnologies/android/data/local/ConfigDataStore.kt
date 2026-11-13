package com.saferenttechnologies.android.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.saferenttechnologies.android.core.di.ConfigPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** What is stored in Preferences DataStore "saferent_config". */
data class CachedConfig(
    /** Raw "data" object exactly as the server sent it. */
    val json: String? = null,
    val etag: String? = null,
    val version: Int = 0,
    val savedAt: Long? = null,
)

/** Thin wrapper over Preferences DataStore "saferent_config". Local only, no network. */
@Singleton
class ConfigDataStore @Inject constructor(
    @ConfigPreferences private val dataStore: DataStore<Preferences>,
) {
    val cached: Flow<CachedConfig> = dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { prefs ->
            CachedConfig(
                json = prefs[KEY_JSON],
                etag = prefs[KEY_ETAG],
                version = prefs[KEY_VERSION] ?: 0,
                savedAt = prefs[KEY_SAVED_AT],
            )
        }

    suspend fun read(): CachedConfig = cached.first()

    /** Overwrites everything atomically. A null [etag] removes the stored one. */
    suspend fun save(json: String, etag: String?, version: Int, savedAt: Long) {
        dataStore.edit { prefs ->
            prefs[KEY_JSON] = json
            if (etag != null) prefs[KEY_ETAG] = etag else prefs.remove(KEY_ETAG)
            prefs[KEY_VERSION] = version
            prefs[KEY_SAVED_AT] = savedAt
        }
    }

    companion object {
        const val NAME = "saferent_config"
        internal val KEY_JSON = stringPreferencesKey("config_json")
        internal val KEY_ETAG = stringPreferencesKey("config_etag")
        internal val KEY_VERSION = intPreferencesKey("config_version")
        internal val KEY_SAVED_AT = longPreferencesKey("config_saved_at")
    }
}
