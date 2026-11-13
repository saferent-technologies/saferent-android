package com.saferenttechnologies.android.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.saferenttechnologies.android.core.di.OnboardingPreferences
import com.saferenttechnologies.android.core.log.AppLogger
import com.saferenttechnologies.android.domain.repository.OnboardingRepository
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** Preferences DataStore "saferent_onboarding". */
@Singleton
class OnboardingRepositoryImpl @Inject constructor(
    @OnboardingPreferences private val dataStore: DataStore<Preferences>,
    private val logger: AppLogger,
) : OnboardingRepository {

    override suspend fun isCompleted(): Boolean =
        dataStore.data
            .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
            .first()[KEY_COMPLETED] ?: false

    override suspend fun markCompleted(version: String) {
        try {
            dataStore.edit { p ->
                p[KEY_COMPLETED] = true
                p[KEY_COMPLETED_VERSION] = version
                p[KEY_COMPLETED_AT] = System.currentTimeMillis()
            }
            logger.d(TAG, "Onboarding completed (version=$version)")
        } catch (e: IOException) {
            logger.w(TAG, "Couldn't save onboarding completion", e) // shows again next launch
        }
    }

    companion object {
        const val NAME = "saferent_onboarding"
        private const val TAG = "SafeRent-Onboarding"
        private val KEY_COMPLETED = booleanPreferencesKey("onboarding_completed")
        private val KEY_COMPLETED_VERSION = stringPreferencesKey("onboarding_completed_version")
        private val KEY_COMPLETED_AT = longPreferencesKey("onboarding_completed_at")
    }
}
