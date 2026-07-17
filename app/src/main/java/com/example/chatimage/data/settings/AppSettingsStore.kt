package com.example.chatimage.data.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.chatimage.data.model.AppSettings
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.chatImageDataStore by preferencesDataStore(
    name = "chatimage_v3_app_settings"
)

class AppSettingsStore(
    private val context: Context
) {

    private object Keys {
        val appSettingsJson =
            stringPreferencesKey(
                "app_settings_json"
            )
    }

    val settingsFlow: Flow<AppSettings> =
        context.chatImageDataStore
            .data
            .catch { exception ->
                if (exception is IOException) {
                    emit(
                        androidx.datastore
                            .preferences
                            .core
                            .emptyPreferences()
                    )
                } else {
                    throw exception
                }
            }
            .map { preferences ->
                val raw = preferences[
                    Keys.appSettingsJson
                ].orEmpty()

                AppSettingsCodec.decode(raw)
            }

    suspend fun get(): AppSettings {
        return settingsFlow.first()
    }

    suspend fun save(
        settings: AppSettings
    ) {
        val encoded =
            AppSettingsCodec.encode(settings)

        context.chatImageDataStore.edit {
            it[Keys.appSettingsJson] =
                encoded
        }
    }

    suspend fun update(
        transform: (
            AppSettings
        ) -> AppSettings
    ) {
        context.chatImageDataStore.edit {
            val current = AppSettingsCodec.decode(
                it[Keys.appSettingsJson]
                    .orEmpty()
            )

            it[Keys.appSettingsJson] =
                AppSettingsCodec.encode(
                    transform(current)
                )
        }
    }

    suspend fun reset() {
        context.chatImageDataStore.edit {
            it.remove(Keys.appSettingsJson)
        }
    }

    suspend fun exportJson(): String {
        return AppSettingsCodec.encode(
            get()
        )
    }

    suspend fun importJson(
        json: String
    ): Result<Unit> {
        return try {
            val decoded =
                AppSettingsCodec.decode(json)

            save(decoded)

            Result.success(Unit)
        } catch (exception: Exception) {
            Result.failure(exception)
        }
    }
}
