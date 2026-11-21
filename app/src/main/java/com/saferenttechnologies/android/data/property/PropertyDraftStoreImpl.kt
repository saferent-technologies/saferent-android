package com.saferenttechnologies.android.data.property

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import com.saferenttechnologies.android.core.di.SessionPreferences
import com.saferenttechnologies.android.domain.repository.PropertyDraftStore
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PropertyDraftStoreImpl @Inject constructor(
    @SessionPreferences private val dataStore: DataStore<Preferences>,
) : PropertyDraftStore {

    override suspend fun draftId(): String? =
        dataStore.data.catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }.first()[KEY]

    override suspend fun saveDraftId(id: String?) {
        runCatching { dataStore.edit { if (id == null) it.remove(KEY) else it[KEY] = id } }
    }

    override suspend fun lastStep(propertyId: String): Int? =
        runCatching { dataStore.data.first()[stepKey(propertyId)] }.getOrNull()

    override suspend fun saveLastStep(propertyId: String, step: Int?) {
        if (propertyId.isBlank()) return
        runCatching { dataStore.edit { if (step == null) it.remove(stepKey(propertyId)) else it[stepKey(propertyId)] = step } }
    }

    private companion object {
        val KEY = stringPreferencesKey("add_property_draft_id")
        fun stepKey(id: String) = intPreferencesKey("wizard_last_step_$id")
    }
}
