package com.example.chatimage.util

import java.net.URI

object UrlUtils {

    fun resolveEndpoint(
        baseUrl: String,
        pathOrUrl: String
    ): String {
        val path = pathOrUrl.trim()

        if (
            path.startsWith("https://") ||
            path.startsWith("http://")
        ) {
            return path
        }

        val base = baseUrl
            .trim()
            .trimEnd('/')

        if (path.isBlank()) {
            return base
        }

        return base + "/" +
            path.trimStart('/')
    }

    fun resolveRelativeUrl(
        baseUrl: String,
        relativeOrAbsolute: String
    ): String {
        val raw = relativeOrAbsolute.trim()

        if (
            raw.startsWith("https://") ||
            raw.startsWith("http://")
        ) {
            return raw
        }

        return try {
            val normalizedBase =
                if (baseUrl.endsWith("/")) {
                    baseUrl
                } else {
                    "$baseUrl/"
                }

            URI(normalizedBase)
                .resolve(raw)
                .toString()
        } catch (_: Exception) {
            baseUrl.trimEnd('/') +
                "/" +
                raw.trimStart('/')
        }
    }

    fun isHttpOrHttps(
        value: String
    ): Boolean {
        return value.startsWith(
            "https://",
            ignoreCase = true
        ) || value.startsWith(
            "http://",
            ignoreCase = true
        )
    }

    fun isSecureHttps(
        value: String
    ): Boolean {
        return value.startsWith(
            "https://",
            ignoreCase = true
        )
    }

    fun removeSensitiveQueryValues(
        value: String
    ): String {
        return try {
            val uri = URI(value)

            val query = uri.rawQuery
                ?: return value

            val redactedQuery = query
                .split("&")
                .joinToString("&") { item ->
                    val name = item
                        .substringBefore("=")

                    val sensitive =
                        name.contains(
                            "token",
                            ignoreCase = true
                        ) ||
                        name.contains(
                            "key",
                            ignoreCase = true
                        ) ||
                        name.contains(
                            "secret",
                            ignoreCase = true
                        ) ||
                        name.contains(
                            "signature",
                            ignoreCase = true
                        ) ||
                        name.equals(
                            "sig",
                            ignoreCase = true
                        )

                    if (sensitive) {
                        "$name=***"
                    } else {
                        item
                    }
                }

            URI(
                uri.scheme,
                uri.authority,
                uri.path,
                redactedQuery,
                uri.fragment
            ).toString()
        } catch (_: Exception) {
            value
        }
    }
}
