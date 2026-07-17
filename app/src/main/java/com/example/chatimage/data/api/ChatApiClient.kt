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
        withContext(Dispatchers.IO) {
            val profile = resolvedProfile.profile
            val chatSettings =
                appSettings.chatParameters

            val stream = streamOverride
                ?: chatSettings.streamEnabled

            val endpoint = UrlUtils.resolveEndpoint(
                profile.baseUrl,
                profile.chatPath
            )

            val model = profile.chatModel
            val startedAt =
                System.currentTimeMillis()

            var activeCall: Call? = null

            try {
                val messageArray = JSONArray()

                messages.forEach { message ->
                    messageArray.put(
                        message.toJson()
                    )
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
                    chatSettings.maxTokensFieldName
                        .isNotBlank()
                ) {
                    standardJson.put(
                        chatSettings.maxTokensFieldName,
                        chatSettings.maxTokens
                    )
                }

                if (
                    chatSettings
                        .frequencyPenaltyEnabled
                ) {
                    standardJson.put(
                        "frequency_penalty",
                        chatSettings.frequencyPenalty
                    )
                }

                if (
                    chatSettings
                        .presencePenaltyEnabled
                ) {
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
                    chatSettings.stopSequences
                        .isNotEmpty()
                ) {
                    val validStops =
                        chatSettings.stopSequences
                            .filter(String::isNotEmpty)

                    when (validStops.size) {
                        0 -> Unit

                        1 -> standardJson.put(
                            "stop",
                            validStops.first()
                        )

                        else -> {
                            val stopArray =
                                JSONArray()

                            validStops.forEach {
                                stopArray.put(it)
                            }

                            standardJson.put(
                                "stop",
                                stopArray
                            )
                        }
                    }
                }

                applyResponseFormat(
                    json = standardJson,
                    mode =
                        chatSettings
                            .responseFormatMode,
                    customJson =
                        chatSettings
                            .responseFormatJson
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

                    val toolChoice =
                        toolChoiceOverride
                            ?: appSettings
                                .search
                                .toolChoice

                    standardJson.put(
                        "tool_choice",
                        toolChoice
                    )
                }

                val extraJson =
                    JsonUtils.parseObjectOrEmpty(
                        chatSettings.extraRequestJson
                    )

                val requestJson =
                    JsonUtils.deepMerge(
                        base = standardJson,
                        overlay = extraJson,
                        overlayWins = true
                    )

                val requestBody = requestJson
                    .toString()
                    .toRequestBody(
                        "application/json; charset=utf-8"
                            .toMediaType()
                    )

                val requestBuilder =
                    Request.Builder()
                        .url(endpoint)
                        .post(requestBody)

                HeaderUtils.applyAuthentication(
                    builder = requestBuilder,
                    mode =
                        profile.authenticationMode,
                    apiKey =
                        resolvedProfile.apiKey,
                    headerName =
                        profile
                            .authorizationHeaderName,
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

                requestBuilder.header(
                    "Accept",
                    if (stream) {
                        "text/event-stream"
                    } else {
                        "application/json"
                    }
                )

                val client =
                    if (useToolDecisionTimeout) {
                        clientFactory
                            .toolDecisionClient(
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
                    coroutineContext.job
                        .invokeOnCompletion { cause ->
                            if (
                                cause is
                                CancellationException
                            ) {
                                call.cancel()
                            }
                        }

                try {
                    call.execute().use { response ->
                        val responseBody =
                            response.body
                                ?: throw
                                    IllegalStateException(
                                        "聊天接口返回内容为空"
                                    )

                        if (!response.isSuccessful) {
                            val body =
                                responseBody.string()

                            val duration =
                                System.currentTimeMillis() -
                                    startedAt

                            val parsed =
                                ErrorParser
                                    .fromHttpResponse(
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

                            return@withContext
                                ApiOutcome.Failure(
                                    error = parsed.first,
                                    diagnostics =
                                        parsed.second
                                )
                        }

                        if (!stream) {
                            val body =
                                responseBody.string()

                            val result =
                                parseNonStreamResponse(
                                    body = body,
                                    appSettings =
                                        appSettings
                                )

                            val diagnostics =
                                buildDiagnostics(
                                    endpoint = endpoint,
                                    model = model,
                                    durationMs =
                                        System
                                            .currentTimeMillis() -
                                            startedAt,
                                    response = response,
                                    requestIdHeaderNames =
                                        appSettings
                                            .diagnostics
                                            .requestIdHeaderNames
                                )

                            return@withContext
                                ApiOutcome.Success(
                                    value = result,
                                    diagnostics =
                                        diagnostics
                                )
                        }

                        val result =
                            parseStreamResponse(
                                source =
                                    responseBody.source(),
                                appSettings =
                                    appSettings,
                                onDelta = onDelta
                            )

                        val diagnostics =
                            buildDiagnostics(
                                endpoint = endpoint,
                                model = model,
                                durationMs =
                                    System
                                        .currentTimeMillis() -
                                        startedAt,
                                response = response,
                                requestIdHeaderNames =
                                    appSettings
                                        .diagnostics
                                        .requestIdHeaderNames
                            )

                        return@withContext
                            ApiOutcome.Success(
                                value = result,
                                diagnostics =
                                    diagnostics
                            )
                    }
                } finally {
                    cancellationHandle.dispose()
                }
            } catch (
                exception: CancellationException
            ) {
                activeCall?.cancel()
                throw exception
            } catch (exception: Exception) {
                val duration =
                    System.currentTimeMillis() -
                        startedAt

                val parsed =
                    ErrorParser.fromException(
                        exception = exception,
                        endpoint = endpoint,
                        model = model,
                        durationMs = duration
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
        val settings =
            appSettings.chatParameters

        val fullText = StringBuilder()
        var finishReason: String? = null
        var receivedJson = false

        while (!source.exhausted()) {
            coroutineContext.ensureActive()

            val rawLine =
                source.readUtf8Line()
                    ?: continue

            val line = rawLine.trim()

            if (line.isBlank()) {
                continue
            }

            val payload = when (
                settings.streamProtocol
            ) {
                StreamProtocol.SSE -> {
                    parseSsePayload(
                        line = line,
                        prefix =
                            settings.sseDataPrefix
                    )
                }

                StreamProtocol.JSON_LINES -> {
                    line.takeIf {
                        it.startsWith("{") ||
                            it.startsWith("[")
                    }
                }

                StreamProtocol.AUTO -> {
                    when {
                        line.startsWith(
                            settings.sseDataPrefix
                        ) -> {
                            parseSsePayload(
                                line = line,
                                prefix =
                                    settings
                                        .sseDataPrefix
                            )
                        }

                        line.startsWith("{") -> {
                            line
                        }

                        else -> null
                    }
                }
            } ?: continue

            if (
                payload ==
                settings.sseDoneMarker
            ) {
                break
            }

            if (payload.isBlank()) {
                continue
            }

            val root = try {
                JSONObject(payload)
            } catch (_: Exception) {
                continue
            }

            receivedJson = true

            val fragment =
                JsonUtils.readString(
                    root,
                    settings.streamTextPath
                ).orEmpty()

            if (fragment.isNotEmpty()) {
                fullText.append(fragment)
                onDelta(fragment)
            }

            finishReason =
                JsonUtils.readString(
                    root,
                    "choices[0].finish_reason"
                ) ?: finishReason
        }

        if (
            !receivedJson &&
            fullText.isEmpty()
        ) {
            throw IllegalStateException(
                "服务器返回了流式响应，但没有解析到 JSON 数据。请检查流式协议、SSE 前缀和文本字段路径，或暂时关闭流式输出。"
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
            appSettings
                .chatParameters
                .nonStreamTextPath
        ).orEmpty()

        val toolCalls = parseToolCalls(
            JsonUtils.readArray(
                root,
                "choices[0].message.tool_calls"
            )
        )

        val legacyFunctionCall =
            JsonUtils.readObject(
                root,
                "choices[0].message.function_call"
            )

        val allCalls =
            if (
                toolCalls.isEmpty() &&
                legacyFunctionCall != null
            ) {
                val functionName =
                    legacyFunctionCall
                        .optString("name")
                        .trim()

                if (functionName.isBlank()) {
                    emptyList()
                } else {
                    listOf(
                        ToolCall(
                            id =
                                "legacy_" +
                                    UUID.randomUUID(),
                            functionName =
                                functionName,
                            argumentsJson =
                                legacyFunctionCall
                                    .optString(
                                        "arguments",
                                        "{}"
                                    )
                        )
                    )
                }
            } else {
                toolCalls
            }

        if (
            content.isBlank() &&
            allCalls.isEmpty()
        ) {
            throw IllegalStateException(
                "接口成功返回，但没有按当前字段路径找到文本或 Tool Calls。响应预览：${body.take(1000)}"
            )
        }

        return ChatCompletionResult(
            content = content,
            toolCalls = allCalls,
            finishReason =
                JsonUtils.readString(
                    root,
                    "choices[0].finish_reason"
                ),
            rawResponsePreview =
                body.take(3000)
        )
    }

    private fun parseToolCalls(
        array: JSONArray?
    ): List<ToolCall> {
        if (array == null) {
            return emptyList()
        }

        return buildList {
            for (
                index in 0 until array.length()
            ) {
                val item =
                    array.optJSONObject(index)
                        ?: continue

                val function =
                    item.optJSONObject("function")
                        ?: continue

                val name =
                    function
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

        return line
            .removePrefix(prefix)
            .trim()
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
        requestIdHeaderNames:
            List<String>
    ): RequestDiagnostics {
        val requestId =
            requestIdHeaderNames
                .firstNotNullOfOrNull {
                    response.header(it)
                }

        return RequestDiagnostics(
            endpoint = endpoint,
            method =
                response.request.method,
            model = model,
            httpStatus = response.code,
            durationMs = durationMs,
            contentType =
                response.header(
                    "Content-Type"
                ),
            server =
                response.header("Server"),
            requestId = requestId
        )
    }
}
