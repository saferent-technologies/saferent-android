package com.saferenttechnologies.android.data.browse

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.saferenttechnologies.android.core.di.BrowsePreferences
import com.saferenttechnologies.android.domain.repository.RecentSearchStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** Last 8 searches, newest first (one string, "\n"-separated). */
@Singleton
class RecentSearchStoreImpl @Inject constructor(
    @BrowsePreferences private val dataStore: DataStore<Preferences>,
) : RecentSearchStore {

    override val recent: Flow<List<String>> = dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { prefs -> prefs[KEY].orEmpty().split('\n').filter { it.isNotBlank() } }

    override suspend fun add(query: String) {
        val q = query.trim().replace('\n', ' ')
        if (q.isEmpty()) return
        runCatching {
            dataStore.edit { prefs ->
                val list = prefs[KEY].orEmpty().split('\n').filter { it.isNotBlank() && !it.equals(q, ignoreCase = true) }
                prefs[KEY] = (listOf(q) + list).take(MAX).joinToString("\n")
            }
        }
    }

    override suspend fun clear() {
        runCatching { dataStore.edit { it.remove(KEY) } }
    }

    companion object {
        const val NAME = "saferent_browse"
        private const val MAX = 8
        private val KEY = stringPreferencesKey("recent_searches")
    }
}
