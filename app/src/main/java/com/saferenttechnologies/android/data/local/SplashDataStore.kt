package com.saferenttechnologies.android.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.saferenttechnologies.android.core.di.SplashPreferences
import com.saferenttechnologies.android.domain.model.SplashAppearance
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** Preferences DataStore "saferent_splash": the splash currently in use. */
@Singleton
class SplashDataStore @Inject constructor(
    @SplashPreferences private val dataStore: DataStore<Preferences>,
) {
    val appearance: Flow<SplashAppearance> = dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { p ->
            SplashAppearance(
                version = p[KEY_VERSION] ?: SplashAppearance.DEFAULT_VERSION,
                title = p[KEY_TITLE]?.takeIf { it.isNotBlank() } ?: SplashAppearance.DEFAULT_TITLE,
                description = p[KEY_DESCRIPTION]?.takeIf { it.isNotBlank() }
                    ?: SplashAppearance.DEFAULT_DESCRIPTION,
                logoPath = p[KEY_LOGO_PATH],
                lottiePath = p[KEY_LOTTIE_PATH],
                minDurationMs = p[KEY_MIN_DURATION_MS] ?: SplashAppearance.DEFAULT_MIN_DURATION_MS,
            )
        }

    suspend fun read(): SplashAppearance = appearance.first()

    /** Replaces everything in one atomic write. Null paths are removed (→ bundled defaults). */
    suspend fun save(appearance: SplashAppearance) {
        dataStore.edit { p ->
            p[KEY_VERSION] = appearance.version
            p[KEY_TITLE] = appearance.title
            p[KEY_DESCRIPTION] = appearance.description
            p[KEY_MIN_DURATION_MS] = appearance.minDurationMs
            appearance.logoPath?.let { p[KEY_LOGO_PATH] = it } ?: p.remove(KEY_LOGO_PATH)
            appearance.lottiePath?.let { p[KEY_LOTTIE_PATH] = it } ?: p.remove(KEY_LOTTIE_PATH)
        }
    }

    companion object {
        const val NAME = "saferent_splash"
        private val KEY_VERSION = stringPreferencesKey("splash_version")
        private val KEY_TITLE = stringPreferencesKey("splash_title")
        private val KEY_DESCRIPTION = stringPreferencesKey("splash_description")
        private val KEY_LOGO_PATH = stringPreferencesKey("splash_logo_path")
        private val KEY_LOTTIE_PATH = stringPreferencesKey("splash_lottie_path")
        private val KEY_MIN_DURATION_MS = longPreferencesKey("splash_min_duration_ms")
    }
}
