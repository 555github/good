package com.example.chatimage.util

import com.example.chatimage.data.model.AuthenticationMode
import com.example.chatimage.data.model.HeaderEntry
import java.util.Locale
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

enum class RequestCategory {
    CHAT,
    IMAGE,
    SEARCH,
    DOWNLOAD
}

object HeaderUtils {

    fun encode(
        entries: List<HeaderEntry>
    ): String {
        val array = JSONArray()

        entries.forEach { entry ->
            array.put(
                JSONObject()
                    .put("id", entry.id)
                    .put("name", entry.name)
                    .put("value", entry.value)
                    .put(
                        "enabled",
                        entry.enabled
                    )
                    .put(
                        "sensitive",
                        entry.sensitive
                    )
                    .put(
                        "applyToChat",
                        entry.applyToChat
                    )
                    .put(
                        "applyToImage",
                        entry.applyToImage
                    )
                    .put(
                        "applyToSearch",
                        entry.applyToSearch
                    )
            )
        }

        return array.toString()
    }

    fun decode(
        raw: String
    ): List<HeaderEntry> {
        if (raw.isBlank()) {
            return emptyList()
        }

        return try {
            val array = JSONArray(raw)

            buildList {
                for (
                    index in 0 until
                        array.length()
                ) {
                    val item =
                        array.optJSONObject(index)
                            ?: continue

                    val name = item
                        .optString("name")
                        .trim()

                    if (name.isBlank()) {
                        continue
                    }

                    add(
                        HeaderEntry(
                            id = item.optString(
                                "id"
                            ).ifBlank {
                                java.util.UUID
                                    .randomUUID()
                                    .toString()
                            },
                            name = name,
                            value = item.optString(
                                "value"
                            ),
                            enabled =
                                item.optBoolean(
                                    "enabled",
                                    true
                                ),
                            sensitive =
                                item.optBoolean(
                                    "sensitive",
                                    false
                                ),
                            applyToChat =
                                item.optBoolean(
                                    "applyToChat",
                                    true
                                ),
                            applyToImage =
                                item.optBoolean(
                                    "applyToImage",
                                    true
                                ),
                            applyToSearch =
                                item.optBoolean(
                                    "applyToSearch",
                                    false
                                )
                        )
                    )
                }
            }
        } catch (_: Exception) {
            decodeLegacyObject(raw)
        }
    }

    fun applyAuthentication(
        builder: Request.Builder,
        mode: String,
        apiKey: String,
        headerName: String,
        prefix: String
    ) {
        if (apiKey.isBlank()) {
            return
        }

        val authenticationMode =
            enumValueOrDefault(
                mode,
                AuthenticationMode.BEARER
            )

        when (authenticationMode) {
            AuthenticationMode.BEARER -> {
                builder.header(
                    headerName.ifBlank {
                        "Authorization"
                    },
                    prefix + apiKey
                )
            }

            AuthenticationMode.X_API_KEY -> {
                builder.header(
                    headerName.ifBlank {
                        "X-API-Key"
                    },
                    prefix + apiKey
                )
            }

            AuthenticationMode.CUSTOM_HEADER -> {
                if (headerName.isNotBlank()) {
                    builder.header(
                        headerName,
                        prefix + apiKey
                    )
                }
            }

            AuthenticationMode.NONE -> Unit
        }
    }

    fun applyCustomHeaders(
        builder: Request.Builder,
        entries: List<HeaderEntry>,
        category: RequestCategory
    ) {
        entries
            .filter {
                it.enabled &&
                    it.name.isNotBlank() &&
                    appliesTo(it, category)
            }
            .forEach { entry ->
                builder.header(
                    entry.name.trim(),
                    entry.value
                )
            }
    }

    fun redactForDisplay(
        entries: List<HeaderEntry>
    ): List<HeaderEntry> {
        return entries.map { entry ->
            if (
                entry.sensitive ||
                isSensitiveHeader(
                    entry.name
                )
            ) {
                entry.copy(value = "***")
            } else {
                entry
            }
        }
    }

    fun isSensitiveHeader(
        name: String
    ): Boolean {
        val normalized = name
            .lowercase(Locale.ROOT)

        return normalized in setOf(
            "authorization",
            "proxy-authorization",
            "x-api-key",
            "api-key",
            "cookie",
            "set-cookie"
        ) ||
            normalized.contains("token") ||
            normalized.contains("secret")
    }

    private fun appliesTo(
        entry: HeaderEntry,
        category: RequestCategory
    ): Boolean {
        return when (category) {
            RequestCategory.CHAT ->
                entry.applyToChat

            RequestCategory.IMAGE ->
                entry.applyToImage

            RequestCategory.SEARCH ->
                entry.applyToSearch

            RequestCategory.DOWNLOAD ->
                entry.applyToImage
        }
    }

    private fun decodeLegacyObject(
        raw: String
    ): List<HeaderEntry> {
        return try {
            val root = JSONObject(raw)

            buildList {
                val keys = root.keys()

                while (keys.hasNext()) {
                    val key = keys.next()
                    val value = root
                        .optString(key)

                    add(
                        HeaderEntry(
                            name = key,
                            value = value,
                            sensitive =
                                isSensitiveHeader(
                                    key
                                )
                        )
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private inline fun <
        reified T : Enum<T>
        > enumValueOrDefault(
        raw: String,
        default: T
    ): T {
        return enumValues<T>()
            .firstOrNull {
                it.name.equals(
                    raw,
                    ignoreCase = true
                )
            }
            ?: default
    }
}
