package com.puneeee.voicecatcher

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Encrypts private transcripts and coordinates at rest using this device's Android Keystore key. */
class SecurePreferenceStore(context: Context) {
    private val preferences = context.getSharedPreferences("voice_catcher_tasks", Context.MODE_PRIVATE)

    init {
        listOf("tasks", "captures").forEach { key ->
            preferences.getString(key, null)?.let { legacy -> write(key, legacy) }
        }
    }

    fun readCandidates(key: String): List<String> = buildList {
        decrypt(preferences.getString("$key.v2", null))?.let(::add)
        decrypt(preferences.getString("$key.backup", null))?.let { if (it !in this) add(it) }
    }

    fun write(key: String, value: String) {
        val encrypted = encrypt(value)
        val old = preferences.getString("$key.v2", null)
        check(preferences.edit()
            .putString("$key.backup", old ?: encrypted)
            .putString("$key.v2", encrypted)
            .remove(key)
            .commit()) { "Could not save your data. Please free some storage and try again." }
    }

    fun clear(key: String) {
        preferences.edit().remove("$key.v2").remove("$key.backup").remove(key).apply()
    }

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        return listOf(
            Base64.encodeToString(cipher.iv, Base64.NO_WRAP),
            Base64.encodeToString(cipher.doFinal(value.toByteArray(StandardCharsets.UTF_8)), Base64.NO_WRAP),
        ).joinToString(":")
    }

    private fun decrypt(value: String?): String? = runCatching {
        val parts = value?.split(":") ?: return null
        if (parts.size != 2) return null
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)))
        String(cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)), StandardCharsets.UTF_8)
    }.getOrNull()

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(
                KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build(),
            )
        }.generateKey()
    }

    private companion object {
        const val KEY_ALIAS = "voice_catcher_private_data"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
