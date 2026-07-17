from pathlib import Path
from textwrap import dedent


ROOT = Path(__file__).resolve().parent.parent


CHAT_API_CLIENT = r'''
package com.example.chatimage.data.api

import com.example.chatimage.data.model.AppSettings
import com.example.chatimage.data.model.RequestDiagnostics
import com.example.chatimage.data.model.StreamProtocol
import com.example.chatimage.data.repository.ResolvedApiProfile
import com.example.chatimage.util.ErrorParser
import com.example.chatimage.util.HeaderUtils
import com.example.chatimage.util.JsonUtils
import com.example.chatimage.util.RequestCategory
import com.example.chatimage.util.UrlUtils
import java.util.UUID
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject

class ChatApiClient(
    private val clientFactory: HttpClientFactory =
        HttpClientFactory()
) {

    suspend fun complete(
        resolvedProfile: ResolvedApiProfile,
        appSettings: AppSettings,
        messages: List<ChatWireMessage>,
        streamOverride: Boolean? = null,
        tools: List<ToolDefinition> = emptyList(),
        toolChoiceOverride: Any? = null,
        useToolDecisionTimeout: Boolean = false,
        onDelta: suspend (String) -> Unit = {}
    ): ApiOutcome<ChatCompletionResult> =
        withContext<ApiOutcome<ChatCompletionResult>>(
            Dispatchers.IO
        ) {
            val profile = resolvedProfile.profile
            val chatSettings = appSettings.chatParameters
            val stream = streamOverride
                ?: chatSettings.streamEnabled

            val endpoint = UrlUtils.resolveEndpoint(
                profile.baseUrl,
                profile.chatPath
            )

            val model = profile.chatModel
            val startedAt = System.currentTimeMillis()
            var activeCall: Call? = null

            try {
                val messageArray = JSONArray()

                messages.forEach { message ->
                    messageArray.put(message.toJson())
                }

                val standardJson = JSONObject()
                    .put("model", model)
                    .put("messages", messageArray)
                    .put("stream", stream)

                if (chatSettings.temperatureEnabled) {
                    standardJson.put(
                        "temperature",
                        chatSettings.temperature
                    )
                }

                if (chatSettings.topPEnabled) {
                    standardJson.put(
                        "top_p",
                        chatSettings.topP
                    )
                }

                if (
                    chatSettings.maxTokensEnabled &&
                    chatSettings.maxTokensFieldName.isNotBlank()
                ) {
                    standardJson.put(
                        chatSettings.maxTokensFieldName,
                        chatSettings.maxTokens
                    )
                }

                if (chatSettings.frequencyPenaltyEnabled) {
                    standardJson.put(
                        "frequency_penalty",
                        chatSettings.frequencyPenalty
                    )
                }

                if (chatSettings.presencePenaltyEnabled) {
                    standardJson.put(
                        "presence_penalty",
                        chatSettings.presencePenalty
                    )
                }

                if (chatSettings.seedEnabled) {
                    standardJson.put(
                        "seed",
                        chatSettings.seed
                    )
                }

                if (
                    chatSettings.stopEnabled &&
                    chatSettings.stopSequences.isNotEmpty()
                ) {
                    val stops = chatSettings.stopSequences
                        .filter(String::isNotEmpty)

                    when (stops.size) {
                        0 -> Unit

                        1 -> standardJson.put(
                            "stop",
                            stops.first()
                        )

                        else -> {
                            val stopArray = JSONArray()

                            stops.forEach(stopArray::put)

                            standardJson.put(
                                "stop",
                                stopArray
                            )
                        }
                    }
                }

                applyResponseFormat(
                    standardJson,
                    chatSettings.responseFormatMode,
                    chatSettings.responseFormatJson
                )

                if (tools.isNotEmpty()) {
                    val toolsArray = JSONArray()

                    tools.forEach { tool ->
                        toolsArray.put(
                            tool.toOpenAiJson()
                        )
                    }

                    standardJson.put(
                        "tools",
                        toolsArray
                    )

                    standardJson.put(
                        "tool_choice",
                        toolChoiceOverride
                            ?: appSettings.search.toolChoice
                    )
                }

                val requestJson = JsonUtils.deepMerge(
                    base = standardJson,
                    overlay = JsonUtils.parseObjectOrEmpty(
                        chatSettings.extraRequestJson
                    ),
                    overlayWins = true
                )

                val requestBody = requestJson
                    .toString()
                    .toRequestBody(
                        "application/json; charset=utf-8"
                            .toMediaType()
                    )

                val requestBuilder = Request.Builder()
                    .url(endpoint)
                    .post(requestBody)
                    .header(
                        "Accept",
                        if (stream) {
                            "text/event-stream"
                        } else {
                            "application/json"
                        }
                    )

                HeaderUtils.applyAuthentication(
                    builder = requestBuilder,
                    mode = profile.authenticationMode,
                    apiKey = resolvedProfile.apiKey,
                    headerName =
                        profile.authorizationHeaderName,
                    prefix =
                        profile.authorizationPrefix
                )

                HeaderUtils.applyCustomHeaders(
                    builder = requestBuilder,
                    entries = HeaderUtils.decode(
                        profile.customHeadersJson
                    ),
                    category = RequestCategory.CHAT
                )

                val client = if (useToolDecisionTimeout) {
                    clientFactory.toolDecisionClient(
                        appSettings.timeouts
                    )
                } else {
                    clientFactory.chatClient(
                        appSettings.timeouts
                    )
                }

                val call = client.newCall(
                    requestBuilder.build()
                )

                activeCall = call

                val cancellationHandle =
                    coroutineContext.job.invokeOnCompletion {
                        cause ->
                        if (cause is CancellationException) {
                            call.cancel()
                        }
                    }

                try {
                    /*
                     * 关键修复：
                     * use 内不再使用 return@withContext。
                     * 整个 use 表达式明确返回 ApiOutcome。
                     */
                    val outcome:
                        ApiOutcome<ChatCompletionResult> =
                        call.execute().use { response ->
                            val responseBody = response.body

                            if (responseBody == null) {
                                ApiOutcome.Failure(
                                    error =
                                        com.example.chatimage
                                            .data.model.RequestError(
                                                code =
                                                    "EMPTY_RESPONSE",
                                                message =
                                                    "聊天接口返回内容为空",
                                                retryable = true
                                            ),
                                    diagnostics =
                                        buildDiagnostics(
                                            endpoint,
                                            model,
                                            System.currentTimeMillis() -
                                                startedAt,
                                            response,
                                            appSettings
                                                .diagnostics
                                                .requestIdHeaderNames
                                        )
                                )
                            } else if (!response.isSuccessful) {
                                val body = responseBody.string()
                                val duration =
                                    System.currentTimeMillis() -
                                        startedAt

                                val parsed =
                                    ErrorParser.fromHttpResponse(
                                        response = response,
                                        body = body,
                                        endpoint = endpoint,
                                        model = model,
                                        durationMs = duration,
                                        requestIdHeaderNames =
                                            appSettings
                                                .diagnostics
                                                .requestIdHeaderNames
                                    )

                                ApiOutcome.Failure(
                                    error = parsed.first,
                                    diagnostics = parsed.second
                                )
                            } else if (!stream) {
                                val body = responseBody.string()
                                val completion =
                                    parseNonStreamResponse(
                                        body,
                                        appSettings
                                    )

                                ApiOutcome.Success(
                                    value = completion,
                                    diagnostics =
                                        buildDiagnostics(
                                            endpoint,
                                            model,
                                            System.currentTimeMillis() -
                                                startedAt,
                                            response,
                                            appSettings
                                                .diagnostics
                                                .requestIdHeaderNames
                                        )
                                )
                            } else {
                                val completion =
                                    parseStreamResponse(
                                        responseBody.source(),
                                        appSettings,
                                        onDelta
                                    )

                                ApiOutcome.Success(
                                    value = completion,
                                    diagnostics =
                                        buildDiagnostics(
                                            endpoint,
                                            model,
                                            System.currentTimeMillis() -
                                                startedAt,
                                            response,
                                            appSettings
                                                .diagnostics
                                                .requestIdHeaderNames
                                        )
                                )
                            }
                        }

                    outcome
                } finally {
                    cancellationHandle.dispose()
                }
            } catch (
                exception: CancellationException
            ) {
                activeCall?.cancel()
                throw exception
            } catch (exception: Exception) {
                val parsed = ErrorParser.fromException(
                    exception = exception,
                    endpoint = endpoint,
                    model = model,
                    durationMs =
                        System.currentTimeMillis() -
                            startedAt
                )

                ApiOutcome.Failure(
                    error = parsed.first,
                    diagnostics = parsed.second
                )
            }
        }

    private suspend fun parseStreamResponse(
        source: okio.BufferedSource,
        appSettings: AppSettings,
        onDelta: suspend (String) -> Unit
    ): ChatCompletionResult {
        val settings = appSettings.chatParameters
        val fullText = StringBuilder()

        var finishReason: String? = null
        var receivedJson = false

        while (!source.exhausted()) {
            coroutineContext.ensureActive()

            val line = source.readUtf8Line()
                ?.trim()
                ?: continue

            if (line.isBlank()) {
                continue
            }

            val payload = when (
                settings.streamProtocol
            ) {
                StreamProtocol.SSE -> {
                    parseSsePayload(
                        line,
                        settings.sseDataPrefix
                    )
                }

                StreamProtocol.JSON_LINES -> {
                    line.takeIf {
                        it.startsWith("{")
                    }
                }

                StreamProtocol.AUTO -> {
                    when {
                        line.startsWith(
                            settings.sseDataPrefix
                        ) -> {
                            parseSsePayload(
                                line,
                                settings.sseDataPrefix
                            )
                        }

                        line.startsWith("{") -> line
                        else -> null
                    }
                }
            } ?: continue

            if (payload == settings.sseDoneMarker) {
                break
            }

            val root = try {
                JSONObject(payload)
            } catch (_: Exception) {
                continue
            }

            receivedJson = true

            val fragment = JsonUtils.readString(
                root,
                settings.streamTextPath
            ).orEmpty()

            if (fragment.isNotEmpty()) {
                fullText.append(fragment)
                onDelta(fragment)
            }

            finishReason = JsonUtils.readString(
                root,
                "choices[0].finish_reason"
            ) ?: finishReason
        }

        if (!receivedJson && fullText.isEmpty()) {
            throw IllegalStateException(
                "没有解析到流式 JSON。请检查流式协议、SSE 前缀和文本字段路径，或关闭流式输出。"
            )
        }

        return ChatCompletionResult(
            content = fullText.toString(),
            finishReason = finishReason
        )
    }

    private fun parseNonStreamResponse(
        body: String,
        appSettings: AppSettings
    ): ChatCompletionResult {
        val root = JSONObject(body)

        val content = JsonUtils.readString(
            root,
            appSettings.chatParameters
                .nonStreamTextPath
        ).orEmpty()

        val toolCalls = parseToolCalls(
            JsonUtils.readArray(
                root,
                "choices[0].message.tool_calls"
            )
        ).toMutableList()

        if (toolCalls.isEmpty()) {
            val legacy = JsonUtils.readObject(
                root,
                "choices[0].message.function_call"
            )

            val name = legacy
                ?.optString("name")
                ?.trim()
                .orEmpty()

            if (name.isNotBlank()) {
                toolCalls += ToolCall(
                    id = "legacy_" + UUID.randomUUID(),
                    functionName = name,
                    argumentsJson = legacy
                        ?.optString(
                            "arguments",
                            "{}"
                        )
                        ?: "{}"
                )
            }
        }

        if (
            content.isBlank() &&
            toolCalls.isEmpty()
        ) {
            throw IllegalStateException(
                "接口成功返回，但没有找到文本或 Tool Calls。响应预览：${body.take(1000)}"
            )
        }

        return ChatCompletionResult(
            content = content,
            toolCalls = toolCalls,
            finishReason = JsonUtils.readString(
                root,
                "choices[0].finish_reason"
            ),
            rawResponsePreview = body.take(3000)
        )
    }

    private fun parseToolCalls(
        array: JSONArray?
    ): List<ToolCall> {
        if (array == null) {
            return emptyList()
        }

        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index)
                    ?: continue

                val function = item.optJSONObject(
                    "function"
                ) ?: continue

                val name = function
                    .optString("name")
                    .trim()

                if (name.isBlank()) {
                    continue
                }

                add(
                    ToolCall(
                        id = item
                            .optString("id")
                            .ifBlank {
                                "call_" +
                                    UUID.randomUUID()
                            },
                        type = item.optString(
                            "type",
                            "function"
                        ),
                        functionName = name,
                        argumentsJson =
                            function.optString(
                                "arguments",
                                "{}"
                            )
                    )
                )
            }
        }
    }

    private fun parseSsePayload(
        line: String,
        prefix: String
    ): String? {
        if (prefix.isBlank()) {
            return line
        }

        if (!line.startsWith(prefix)) {
            return null
        }

        return line.removePrefix(prefix).trim()
    }

    private fun applyResponseFormat(
        json: JSONObject,
        mode: String,
        customJson: String
    ) {
        when (mode.trim().uppercase()) {
            "TEXT" -> {
                json.put(
                    "response_format",
                    JSONObject().put(
                        "type",
                        "text"
                    )
                )
            }

            "JSON_OBJECT" -> {
                json.put(
                    "response_format",
                    JSONObject().put(
                        "type",
                        "json_object"
                    )
                )
            }

            "CUSTOM_JSON" -> {
                JsonUtils.parseObject(
                    customJson
                ).getOrNull()?.let {
                    json.put(
                        "response_format",
                        it
                    )
                }
            }
        }
    }

    private fun buildDiagnostics(
        endpoint: String,
        model: String,
        durationMs: Long,
        response: Response,
        requestIdHeaderNames: List<String>
    ): RequestDiagnostics {
        val requestId =
            requestIdHeaderNames
                .firstNotNullOfOrNull {
                    response.header(it)
                }

        return RequestDiagnostics(
            endpoint = endpoint,
            method = response.request.method,
            model = model,
            httpStatus = response.code,
            durationMs = durationMs,
            contentType = response.header(
                "Content-Type"
            ),
            server = response.header("Server"),
            requestId = requestId
        )
    }
}
'''


def replace_use_returns(
    relative_path: str,
    success_type: str,
) -> None:
    """
    Image/Search 文件较长，保留现有请求和解析逻辑，
    只重写会触发 Kotlin Nothing 推断的控制流结构。
    """

    path = ROOT / relative_path

    if not path.exists():
        raise RuntimeError(
            f"文件不存在：{relative_path}"
        )

    content = path.read_text(
        encoding="utf-8"
    )

    marker = (
        f"withContext<ApiOutcome<{success_type}>>("
    )

    if marker not in content:
        raise RuntimeError(
            f"{relative_path} 没有显式 withContext 类型，"
            "请确认上一轮修复已经应用。"
        )

    content = content.replace(
        "return@withContext\n"
        "                                ApiOutcome.Failure(",
        "ApiOutcome.Failure(",
    )

    content = content.replace(
        "return@withContext\n"
        "                            ApiOutcome.Success(",
        "ApiOutcome.Success(",
    )

    content = content.replace(
        "return@withContext\n"
        "                                ApiOutcome.Success(",
        "ApiOutcome.Success(",
    )

    path.write_text(
        content,
        encoding="utf-8"
    )


def ensure_horizontal_scroll_import() -> None:
    path = ROOT / (
        "app/src/main/java/com/example/chatimage/"
        "ui/settings/SettingsDialogs.kt"
    )

    content = path.read_text(
        encoding="utf-8"
    )

    import_line = (
        "import androidx.compose.foundation.horizontalScroll"
    )

    if import_line not in content:
        anchor = (
            "import androidx.compose.foundation.layout.Arrangement"
        )

        if anchor not in content:
            raise RuntimeError(
                "SettingsDialogs.kt 中找不到导入锚点"
            )

        content = content.replace(
            anchor,
            import_line + "\n" + anchor,
            1,
        )

        path.write_text(
            content,
            encoding="utf-8"
        )


def write_complete_file(
    relative_path: str,
    content: str,
) -> None:
    path = ROOT / relative_path

    path.parent.mkdir(
        parents=True,
        exist_ok=True,
    )

    path.write_text(
        dedent(content).lstrip(),
        encoding="utf-8",
    )

    print(f"已完整覆盖：{relative_path}")


def verify_no_problematic_returns(
    relative_path: str,
) -> None:
    content = (
        ROOT / relative_path
    ).read_text(encoding="utf-8")

    if "return@withContext" in content:
        raise RuntimeError(
            f"{relative_path} 中仍存在 return@withContext。"
            "该文件需要完整控制流重写。"
        )


def main() -> None:
    print("开始应用 ChatImage v3 网络层修复……")

    write_complete_file(
        (
            "app/src/main/java/com/example/chatimage/"
            "data/api/ChatApiClient.kt"
        ),
        CHAT_API_CLIENT,
    )

    replace_use_returns(
        (
            "app/src/main/java/com/example/chatimage/"
            "data/api/ImageApiClient.kt"
        ),
        "ImageGenerationResult",
    )

    replace_use_returns(
        (
            "app/src/main/java/com/example/chatimage/"
            "data/api/SearchApiClient.kt"
        ),
        "SearchResponse",
    )

    ensure_horizontal_scroll_import()

    verify_no_problematic_returns(
        (
            "app/src/main/java/com/example/chatimage/"
            "data/api/ChatApiClient.kt"
        )
    )

    print("ChatApiClient 已完成整文件修复。")
    print("ImageApiClient 和 SearchApiClient 已移除非局部返回。")
    print("全部修复步骤完成。")


if __name__ == "__main__":
    main()
