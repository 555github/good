package com.example.chatimage.util

import org.json.JSONArray
import org.json.JSONObject

object JsonUtils {

    private val pathPartPattern =
        Regex("""([^.\[\]]+)|\[(\d+)]""")

    fun parseObjectOrEmpty(
        raw: String
    ): JSONObject {
        if (raw.isBlank()) {
            return JSONObject()
        }

        return try {
            JSONObject(raw)
        } catch (_: Exception) {
            JSONObject()
        }
    }

    fun parseObject(
        raw: String
    ): Result<JSONObject> {
        return try {
            Result.success(
                JSONObject(raw)
            )
        } catch (exception: Exception) {
            Result.failure(exception)
        }
    }

    fun readPath(
        root: Any?,
        path: String
    ): Any? {
        if (root == null) {
            return null
        }

        if (path.isBlank()) {
            return root
        }

        var current: Any? = root

        val parts = pathPartPattern
            .findAll(path)
            .map { match ->
                val key = match
                    .groups[1]
                    ?.value

                val index = match
                    .groups[2]
                    ?.value
                    ?.toIntOrNull()

                PathPart(
                    key = key,
                    index = index
                )
            }
            .toList()

        for (part in parts) {
            current = when {
                part.key != null &&
                    current is JSONObject -> {
                    if (
                        current.has(part.key) &&
                        !current.isNull(part.key)
                    ) {
                        current.opt(part.key)
                    } else {
                        null
                    }
                }

                part.index != null &&
                    current is JSONArray -> {
                    if (
                        part.index >= 0 &&
                        part.index < current.length()
                    ) {
                        current.opt(part.index)
                    } else {
                        null
                    }
                }

                else -> null
            }

            if (current == null) {
                return null
            }
        }

        return current
    }

    fun readString(
        root: Any?,
        path: String
    ): String? {
        val value = readPath(root, path)
            ?: return null

        return when (value) {
            JSONObject.NULL -> null
            is String -> value
            is Number -> value.toString()
            is Boolean -> value.toString()
            else -> null
        }
    }

    fun readObject(
        root: Any?,
        path: String
    ): JSONObject? {
        return readPath(root, path)
            as? JSONObject
    }

    fun readArray(
        root: Any?,
        path: String
    ): JSONArray? {
        return readPath(root, path)
            as? JSONArray
    }

    fun putObjectPath(
        root: JSONObject,
        path: String,
        value: Any
    ) {
        val keys = path
            .split('.')
            .map(String::trim)
            .filter(String::isNotBlank)

        if (keys.isEmpty()) return

        var current = root
        keys.dropLast(1).forEach { key ->
            val child = current.optJSONObject(key) ?: JSONObject().also {
                current.put(key, it)
            }
            current = child
        }
        current.put(keys.last(), value)
    }

    fun firstNonBlankString(
        root: Any?,
        paths: List<String>
    ): String? {
        paths.forEach { path ->
            val value = readString(
                root,
                path
            )

            if (!value.isNullOrBlank()) {
                return value
            }
        }

        return null
    }

    fun deepMerge(
        base: JSONObject,
        overlay: JSONObject,
        overlayWins: Boolean = true
    ): JSONObject {
        val result = deepCopy(base)

        val keys = overlay.keys()

        while (keys.hasNext()) {
            val key = keys.next()
            val overlayValue = overlay.opt(key)

            if (
                overlayValue is JSONObject &&
                result.opt(key) is JSONObject
            ) {
                val merged = deepMerge(
                    result.optJSONObject(key)
                        ?: JSONObject(),
                    overlayValue,
                    overlayWins
                )

                result.put(key, merged)
            } else if (
                overlayWins ||
                !result.has(key)
            ) {
                result.put(
                    key,
                    deepCopyValue(
                        overlayValue
                    )
                )
            }
        }

        return result
    }

    fun redact(
        value: Any?,
        sensitiveKeys: Set<String> = setOf(
            "authorization",
            "api_key",
            "apikey",
            "api-key",
            "access_token",
            "token",
            "password",
            "secret",
            "cookie"
        )
    ): Any? {
        return when (value) {
            is JSONObject -> {
                val result = JSONObject()
                val keys = value.keys()

                while (keys.hasNext()) {
                    val key = keys.next()
                    val rawValue = value.opt(key)

                    val sensitive =
                        sensitiveKeys.any {
                            key.contains(
                                it,
                                ignoreCase = true
                            )
                        }

                    result.put(
                        key,
                        if (sensitive) {
                            "***"
                        } else {
                            redact(
                                rawValue,
                                sensitiveKeys
                            )
                        }
                    )
                }

                result
            }

            is JSONArray -> {
                JSONArray().apply {
                    for (
                        index in 0 until
                            value.length()
                    ) {
                        put(
                            redact(
                                value.opt(index),
                                sensitiveKeys
                            )
                        )
                    }
                }
            }

            else -> value
        }
    }

    fun toCompactString(
        value: Any?
    ): String {
        return when (value) {
            null -> ""
            JSONObject.NULL -> ""
            is JSONObject -> value.toString()
            is JSONArray -> value.toString()
            else -> value.toString()
        }
    }

    fun toPrettyString(
        value: Any?,
        indentSpaces: Int = 2
    ): String {
        return when (value) {
            null -> ""
            JSONObject.NULL -> ""
            is JSONObject ->
                value.toString(indentSpaces)

            is JSONArray ->
                value.toString(indentSpaces)

            else -> value.toString()
        }
    }

    private fun deepCopy(
        source: JSONObject
    ): JSONObject {
        return JSONObject(
            source.toString()
        )
    }

    private fun deepCopyValue(
        value: Any?
    ): Any? {
        return when (value) {
            is JSONObject ->
                JSONObject(value.toString())

            is JSONArray ->
                JSONArray(value.toString())

            else -> value
        }
    }

    private data class PathPart(
        val key: String?,
        val index: Int?
    )
}
