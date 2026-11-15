package com.saferenttechnologies.android.data.permission

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import com.saferenttechnologies.android.core.di.PermissionPreferences
import com.saferenttechnologies.android.domain.model.AppPermission
import com.saferenttechnologies.android.domain.repository.PermissionManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidPermissionManager @Inject constructor(
    @ApplicationContext private val context: Context,
    @PermissionPreferences private val dataStore: DataStore<Preferences>,
) : PermissionManager {

    override fun isSupported(permission: AppPermission): Boolean = Build.VERSION.SDK_INT >= permission.minSdk

    override fun isGranted(permission: AppPermission): Boolean {
        if (!isSupported(permission)) return true // nothing to ask on this Android version
        return permission.manifestNames.any {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    override suspend fun askedBefore(permission: AppPermission): Boolean = prefs()[askedKey(permission)] ?: false

    override suspend fun markAsked(permission: AppPermission) {
        runCatching { dataStore.edit { it[askedKey(permission)] = true } }
    }

    override suspend fun lastPromptAt(permission: AppPermission): Long? = prefs()[promptKey(permission)]

    override suspend fun markPrompted(permission: AppPermission, at: Long) {
        runCatching { dataStore.edit { it[promptKey(permission)] = at } }
    }

    private suspend fun prefs(): Preferences =
        dataStore.data.catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }.first()

    private fun askedKey(p: AppPermission) = booleanPreferencesKey("asked_${p.name.lowercase()}")
    private fun promptKey(p: AppPermission) = longPreferencesKey("last_prompt_${p.name.lowercase()}")

    companion object {
        const val NAME = "saferent_permissions"
    }
}
