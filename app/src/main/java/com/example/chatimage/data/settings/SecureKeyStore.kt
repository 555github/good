package com.example.chatimage.data.settings

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.util.UUID

class SecureKeyStore(
    context: Context
) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(
            MasterKey.KeyScheme.AES256_GCM
        )
        .build()

    private val preferences =
        EncryptedSharedPreferences.create(
            context,
            "chatimage_v3_secure_secrets",
            masterKey,
            EncryptedSharedPreferences
                .PrefKeyEncryptionScheme
                .AES256_SIV,
            EncryptedSharedPreferences
                .PrefValueEncryptionScheme
                .AES256_GCM
        )

    fun createAlias(
        prefix: String = "secret"
    ): String {
        return "${prefix}_${UUID.randomUUID()}"
    }

    fun put(
        alias: String,
        value: String
    ) {
        if (alias.isBlank()) {
            return
        }

        preferences.edit()
            .putString(alias, value)
            .apply()
    }

    fun get(
        alias: String
    ): String {
        if (alias.isBlank()) {
            return ""
        }

        return preferences
            .getString(alias, "")
            .orEmpty()
    }

    fun contains(
        alias: String
    ): Boolean {
        if (alias.isBlank()) {
            return false
        }

        return preferences.contains(alias)
    }

    fun remove(
        alias: String
    ) {
        if (alias.isBlank()) {
            return
        }

        preferences.edit()
            .remove(alias)
            .apply()
    }

    fun clearAll() {
        preferences.edit()
            .clear()
            .apply()
    }
}
