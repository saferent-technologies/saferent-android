package com.saferenttechnologies.android.data.safety

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The last Emergency response (raw JSON) of one lease, AES-256-GCM with a key kept in the Android
 * Keystore (never leaves the phone). Holds phone numbers and the address, so nothing is logged and it
 * is cleared on logout and when the lease ends. Any failure = no cache (the screen just loads).
 */
@Singleton
class EmergencyCache @Inject constructor(@ApplicationContext private val context: Context) {
    private val prefs by lazy { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }

    @Synchronized
    fun save(leaseId: String, json: String) {
        runCatching {
            val cipher = Cipher.getInstance(TRANSFORM).apply { init(Cipher.ENCRYPT_MODE, key()) }
            val data = cipher.doFinal(json.toByteArray(Charsets.UTF_8))
            prefs.edit()
                .putString(KEY_LEASE, leaseId)
                .putString(KEY_IV, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
                .putString(KEY_DATA, Base64.encodeToString(data, Base64.NO_WRAP))
                .apply()
        }.onFailure { clear() }
    }

    @Synchronized
    fun read(leaseId: String): String? {
        if (prefs.getString(KEY_LEASE, null) != leaseId) return null
        val iv = prefs.getString(KEY_IV, null) ?: return null
        val data = prefs.getString(KEY_DATA, null) ?: return null
        return runCatching {
            val cipher = Cipher.getInstance(TRANSFORM).apply {
                init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)))
            }
            String(cipher.doFinal(Base64.decode(data, Base64.NO_WRAP)), Charsets.UTF_8)
        }.getOrElse { clear(); null }
    }

    @Synchronized
    fun clear() {
        runCatching { prefs.edit().clear().apply() }
    }

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return gen.generateKey()
    }

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "saferent_emergency_v1"
        const val TRANSFORM = "AES/GCM/NoPadding"
        const val PREFS = "emergency_cache"
        const val KEY_LEASE = "lease"
        const val KEY_IV = "iv"
        const val KEY_DATA = "data"
    }
}
