package com.example.chatimage.util

import com.example.chatimage.data.model.RequestDiagnostics
import com.example.chatimage.data.model.RequestError
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.Locale
import java.util.concurrent.CancellationException
import javax.net.ssl.SSLException
import okhttp3.Response
import org.json.JSONObject

object ErrorParser {

    fun fromHttpResponse(
        response: Response,
        body: String,
        endpoint: String,
        model: String?,
        durationMs: Long,
        requestIdHeaderNames: List<String>
    ): Pair<RequestError, RequestDiagnostics> {
        val status = response.code
        val contentType = response
            .header("Content-Type")
            .orEmpty()

        val requestId = findRequestId(
            response,
            requestIdHeaderNames
        )

        val readableMessage = when {
            body.isBlank() -> {
                defaultHttpMessage(status)
            }

            contentType.contains(
                "text/html",
                ignoreCase = true
            ) || looksLikeHtml(body) -> {
                parseHtmlError(body)
            }

            else -> {
                parseJsonError(body)
                    ?: body
                        .replaceApiKeys()
                        .take(2000)
            }
        }.ifBlank {
            defaultHttpMessage(status)
        }

        val code = when (status) {
            401 -> "AUTHENTICATION_ERROR"
            403 -> "PERMISSION_ERROR"
            404 -> "NOT_FOUND"
            408 -> "REQUEST_TIMEOUT"
            413 -> "REQUEST_TOO_LARGE"
            422 -> "INVALID_REQUEST"
            429 -> "RATE_LIMIT"
            500 -> "SERVER_ERROR"
            502 -> "BAD_GATEWAY"
            503 -> "SERVICE_UNAVAILABLE"
            504 -> "GATEWAY_TIMEOUT"
            else -> "HTTP_$status"
        }

        val retryable = status in setOf(
            408,
            425,
            429,
            500,
            502,
            503,
            504
        )

        val diagnostics = RequestDiagnostics(
            endpoint = endpoint,
            method = response.request.method,
            model = model,
            httpStatus = status,
            durationMs = durationMs,
            contentType = contentType
                .takeIf {
                    it.isNotBlank()
                },
            server = response
                .header("Server")
                ?.take(200),
            requestId = requestId,
            readableError = readableMessage
        )

        val error = RequestError(
            code = code,
            message = readableMessage,
            httpStatus = status,
            requestId = requestId,
            durationMs = durationMs,
            retryable = retryable,
            rawBodyPreview = body
                .replaceApiKeys()
                .take(2000)
        )

        return error to diagnostics
    }

    fun fromException(
        exception: Throwable,
        endpoint: String,
        model: String?,
        durationMs: Long
    ): Pair<RequestError, RequestDiagnostics> {
        val details = when (exception) {
            is CancellationException -> {
                Triple(
                    "CANCELLED",
                    "请求已取消",
                    false
                )
            }

            is SocketTimeoutException -> {
                Triple(
                    "CLIENT_TIMEOUT",
                    "客户端等待响应超时。若服务器返回的是 HTTP 502/504，则仍属于中转站或上游错误。",
                    true
                )
            }

            is UnknownHostException -> {
                Triple(
                    "DNS_ERROR",
                    "无法解析服务器域名，请检查网络和 API 地址。",
                    true
                )
            }

            is SSLException -> {
                Triple(
                    "SSL_ERROR",
                    "HTTPS 安全连接失败：${exception.message.orEmpty()}",
                    false
                )
            }

            else -> {
                Triple(
                    "NETWORK_ERROR",
                    exception.message
                        ?.replaceApiKeys()
                        ?.take(2000)
                        ?: "网络请求失败",
                    true
                )
            }
        }

        val diagnostics = RequestDiagnostics(
            endpoint = endpoint,
            method = "UNKNOWN",
            model = model,
            durationMs = durationMs,
            readableError = details.second
        )

        val error = RequestError(
            code = details.first,
            message = details.second,
            durationMs = durationMs,
            retryable = details.third
        )

        return error to diagnostics
    }

    fun parseJsonError(
        body: String
    ): String? {
        return try {
            val root = JSONObject(body)

            val errorObject =
                root.optJSONObject("error")

            val candidates = listOf(
                errorObject
                    ?.optString("message"),
                errorObject
                    ?.optString("detail"),
                root.optString("message"),
                root.optString("detail"),
                root.optString("error_description"),
                root.optString("error")
            )

            candidates.firstOrNull {
                !it.isNullOrBlank() &&
                    it != "null"
            }?.replaceApiKeys()
        } catch (_: Exception) {
            null
        }
    }

    fun parseHtmlError(
        html: String
    ): String {
        val title = Regex(
            """<title[^>]*>(.*?)</title>""",
            setOf(
                RegexOption.IGNORE_CASE,
                RegexOption.DOT_MATCHES_ALL
            )
        ).find(html)
            ?.groupValues
            ?.getOrNull(1)
            ?.cleanHtmlText()

        val heading = Regex(
            """<h1[^>]*>(.*?)</h1>""",
            setOf(
                RegexOption.IGNORE_CASE,
                RegexOption.DOT_MATCHES_ALL
            )
        ).find(html)
            ?.groupValues
            ?.getOrNull(1)
            ?.cleanHtmlText()

        val plainText = html
            .replace(
                Regex(
                    """<script[\s\S]*?</script>""",
                    RegexOption.IGNORE_CASE
                ),
                " "
            )
            .replace(
                Regex(
                    """<style[\s\S]*?</style>""",
                    RegexOption.IGNORE_CASE
                ),
                " "
            )
            .replace(
                Regex("""<[^>]+>"""),
                " "
            )
            .decodeBasicHtmlEntities()
            .replace(
                Regex("""\s+"""),
                " "
            )
            .trim()
            .replaceApiKeys()

        return listOfNotNull(
            heading?.takeIf {
                it.isNotBlank()
            },
            title?.takeIf {
                it.isNotBlank() &&
                    it != heading
            },
            plainText.take(600)
                .takeIf {
                    it.isNotBlank() &&
                        it != heading &&
                        it != title
                }
        )
            .distinct()
            .joinToString("\n")
            .ifBlank {
                "服务器返回了 HTML 错误页"
            }
    }

    private fun findRequestId(
        response: Response,
        names: List<String>
    ): String? {
        for (name in names) {
            val value = response
                .header(name)

            if (!value.isNullOrBlank()) {
                return value.take(300)
            }
        }

        return null
    }

    private fun defaultHttpMessage(
        status: Int
    ): String {
        return when (status) {
            400 -> "请求参数不被服务器接受"
            401 -> "API Key 无效或认证失败"
            403 -> "当前 API Key 没有调用权限"
            404 -> "接口路径或模型不存在"
            408 -> "服务器等待请求超时"
            413 -> "请求内容过大"
            422 -> "请求格式或参数不兼容"
            429 -> "请求过于频繁或额度不足"
            500 -> "服务器内部错误"
            502 -> "中转站请求上游失败"
            503 -> "服务器暂时不可用"
            504 -> "网关等待源站或上游超时"
            else -> "HTTP $status 请求失败"
        }
    }

    private fun looksLikeHtml(
        value: String
    ): Boolean {
        val start = value
            .trimStart()
            .take(100)
            .lowercase(Locale.ROOT)

        return start.startsWith("<!doctype html") ||
            start.startsWith("<html") ||
            start.contains("<head") ||
            start.contains("<body")
    }

    private fun String.cleanHtmlText():
        String {
        return replace(
            Regex("""<[^>]+>"""),
            " "
        )
            .decodeBasicHtmlEntities()
            .replace(
                Regex("""\s+"""),
                " "
            )
            .trim()
    }

    private fun String.decodeBasicHtmlEntities():
        String {
        return replace("&nbsp;", " ")
            .replace("&bull;", "•")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
    }

    private fun String.replaceApiKeys():
        String {
        return replace(
            Regex(
                """(?i)(Bearer\s+)[A-Za-z0-9._\-]+"""
            ),
            "$1***"
        ).replace(
            Regex(
                """(?i)("?(?:api[_-]?key|token|secret)"?\s*[:=]\s*"?)[^"\s,}]+"""
            ),
            "$1***"
        )
    }
}
