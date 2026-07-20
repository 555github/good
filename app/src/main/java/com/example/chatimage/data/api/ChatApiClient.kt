package com.example.chatimage.data.api

import com.example.chatimage.data.model.AppSettings
import com.example.chatimage.data.model.RequestDiagnostics
import com.example.chatimage.data.model.RequestError
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
    ): ApiOutcome<ChatCompletionResult> {
        return withContext(Dispatchers.IO) {
            executeRequest(
                resolvedProfile = resolvedProfile,
                appSettings = appSettings,
                messages = messages,
                streamOverride = streamOverride,
                tools = tools,
                toolChoiceOverride = toolChoiceOverride,
                useToolDecisionTimeout =
                    useToolDecisionTimeout,
                onDelta = onDelta
            )
        }
    }

    private suspend fun executeRequest(
        resolvedProfile: ResolvedApiProfile,
        appSettings: AppSettings,
        messages: List<ChatWireMessage>,
        streamOverride: Boolean?,
        tools: List<ToolDefinition>,
        toolChoiceOverride: Any?,
        useToolDecisionTimeout: Boolean,
        onDelta: suspend (String) -> Unit
    ): ApiOutcome<ChatCompletionResult> {
        val profile = resolvedProfile.profile
        val parameters = appSettings.chatParameters

        val stream = streamOverride
            ?: parameters.streamEnabled

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

            if (stream && parameters.requestUsage) {
                standardJson.put(
                    "stream_options",
                    JSONObject().put("include_usage", true)
                )
            }

            if (
                parameters.reasoningEnabled &&
                parameters.reasoningFieldPath.isNotBlank() &&
                parameters.reasoningValue.isNotBlank()
            ) {
                JsonUtils.putObjectPath(
                    standardJson,
                    parameters.reasoningFieldPath,
                    parameters.reasoningValue
                )
            }

            if (parameters.temperatureEnabled) {
                standardJson.put(
                    "temperature",
                    parameters.temperature
                )
            }

            if (parameters.topPEnabled) {
                standardJson.put(
                    "top_p",
                    parameters.topP
                )
            }

            if (
                parameters.maxTokensEnabled &&
                parameters.maxTokensFieldName
                    .isNotBlank()
            ) {
                standardJson.put(
                    parameters.maxTokensFieldName,
                    parameters.maxTokens
                )
            }

            if (parameters.frequencyPenaltyEnabled) {
                standardJson.put(
                    "frequency_penalty",
                    parameters.frequencyPenalty
                )
            }

            if (parameters.presencePenaltyEnabled) {
                standardJson.put(
                    "presence_penalty",
                    parameters.presencePenalty
                )
            }

            if (parameters.seedEnabled) {
                standardJson.put(
                    "seed",
                    parameters.seed
                )
            }

            if (
                parameters.stopEnabled &&
                parameters.stopSequences.isNotEmpty()
            ) {
                val validStops = parameters.stopSequences
                    .filter(String::isNotEmpty)

                when (validStops.size) {
                    0 -> Unit

                    1 -> standardJson.put(
                        "stop",
                        validStops.first()
                    )

                    else -> {
                        val stopArray = JSONArray()

                        validStops.forEach { value ->
                            stopArray.put(value)
                        }

                        standardJson.put(
                            "stop",
                            stopArray
                        )
                    }
                }
            }

            applyResponseFormat(
                requestJson = standardJson,
                mode = parameters.responseFormatMode,
                customJson = parameters.responseFormatJson
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

                val toolChoice = toolChoiceOverride
                    ?: appSettings.search.toolChoice

                standardJson.put(
                    "tool_choice",
                    toolChoice
                )
            }

            val extraJson = JsonUtils.parseObjectOrEmpty(
                parameters.extraRequestJson
            )

            val finalJson = JsonUtils.deepMerge(
                base = standardJson,
                overlay = extraJson,
                overlayWins = true
            )

            val requestBody = finalJson
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
                prefix = profile.authorizationPrefix
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
                coroutineContext.job
                    .invokeOnCompletion { cause ->
                        if (
                            cause is CancellationException
                        ) {
                            call.cancel()
                        }
                    }

            try {
                val response = call.execute()

                return response.use { value ->
                    processResponse(
                        response = value,
                        endpoint = endpoint,
                        model = model,
                        stream = stream,
                        startedAt = startedAt,
                        appSettings = appSettings,
                        onDelta = onDelta
                    )
                }
            } finally {
                cancellationHandle.dispose()
            }
        } catch (exception: CancellationException) {
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

            return ApiOutcome.Failure(
                error = parsed.first,
                diagnostics = parsed.second
            )
        }
    }

    private suspend fun processResponse(
        response: Response,
        endpoint: String,
        model: String,
        stream: Boolean,
        startedAt: Long,
        appSettings: AppSettings,
        onDelta: suspend (String) -> Unit
    ): ApiOutcome<ChatCompletionResult> {
        val responseBody = response.body

        if (responseBody == null) {
            val diagnostics = buildDiagnostics(
                endpoint = endpoint,
                model = model,
                durationMs =
                    System.currentTimeMillis() -
                        startedAt,
                response = response,
                requestIdHeaderNames =
                    appSettings.diagnostics
                        .requestIdHeaderNames
            )

            return ApiOutcome.Failure(
                error = RequestError(
                    code = "EMPTY_RESPONSE",
                    message = "聊天接口返回内容为空",
                    httpStatus = response.code,
                    requestId = diagnostics.requestId,
                    durationMs =
                        diagnostics.durationMs,
                    retryable = true
                ),
                diagnostics = diagnostics
            )
        }

        if (!response.isSuccessful) {
            val errorBody = responseBody.string()

            val parsed = ErrorParser.fromHttpResponse(
                response = response,
                body = errorBody,
                endpoint = endpoint,
                model = model,
                durationMs =
                    System.currentTimeMillis() -
                        startedAt,
                requestIdHeaderNames =
                    appSettings.diagnostics
                        .requestIdHeaderNames
            )

            return ApiOutcome.Failure(
                error = parsed.first,
                diagnostics = parsed.second
            )
        }

        val result = if (stream) {
            parseStreamResponse(
                source = responseBody.source(),
                appSettings = appSettings,
                onDelta = onDelta
            )
        } else {
            parseNonStreamResponse(
                body = responseBody.string(),
                appSettings = appSettings
            )
        }

        val diagnostics = buildDiagnostics(
            endpoint = endpoint,
            model = model,
            durationMs =
                System.currentTimeMillis() -
                    startedAt,
            response = response,
            requestIdHeaderNames =
                appSettings.diagnostics
                    .requestIdHeaderNames
        )

        return ApiOutcome.Success(
            value = result,
            diagnostics = diagnostics
        )
    }

    private suspend fun parseStreamResponse(
        source: okio.BufferedSource,
        appSettings: AppSettings,
        onDelta: suspend (String) -> Unit
    ): ChatCompletionResult {
        val settings = appSettings.chatParameters

        val completeText = StringBuilder()

        var finishReason: String? = null
        var usage = TokenUsage()
        var parsedJsonCount = 0

        while (!source.exhausted()) {
            coroutineContext.ensureActive()

            val rawLine = source.readUtf8Line()
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
                        it.startsWith("{")
                    }
                }

                StreamProtocol.AUTO -> {
                    when {
                        settings.sseDataPrefix
                            .isNotBlank() &&
                            line.startsWith(
                                settings.sseDataPrefix
                            ) -> {
                            parseSsePayload(
                                line = line,
                                prefix =
                                    settings.sseDataPrefix
                            )
                        }

                        line.startsWith("{") -> line

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

            parsedJsonCount++

            val fragment = JsonUtils.readString(
                root,
                settings.streamTextPath
            ).orEmpty()

            if (fragment.isNotEmpty()) {
                completeText.append(fragment)
                onDelta(fragment)
            }

            finishReason = JsonUtils.readString(
                root,
                "choices[0].finish_reason"
            ) ?: finishReason

            root.optJSONObject("usage")?.let {
                usage = parseChatUsage(it)
            }
        }

        if (parsedJsonCount == 0) {
            throw IllegalStateException(
                "服务器返回了流式响应，但没有解析到 JSON。请检查流式协议、SSE 前缀和字段路径，或关闭流式输出。"
            )
        }

        return ChatCompletionResult(
            content = completeText.toString(),
            finishReason = finishReason,
            usage = usage
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

        val calls = parseToolCalls(
            JsonUtils.readArray(
                root,
                "choices[0].message.tool_calls"
            )
        ).toMutableList()

        if (calls.isEmpty()) {
            val legacyFunction = JsonUtils.readObject(
                root,
                "choices[0].message.function_call"
            )

            val legacyName = legacyFunction
                ?.optString("name")
                ?.trim()
                .orEmpty()

            if (legacyName.isNotBlank()) {
                calls += ToolCall(
                    id = "legacy_" +
                        UUID.randomUUID(),
                    functionName = legacyName,
                    argumentsJson =
                        legacyFunction
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
            calls.isEmpty()
        ) {
            throw IllegalStateException(
                "接口成功返回，但没有找到文本或 Tool Calls。响应预览：${body.take(1000)}"
            )
        }

        return ChatCompletionResult(
            content = content,
            toolCalls = calls,
            finishReason = JsonUtils.readString(
                root,
                "choices[0].finish_reason"
            ),
            rawResponsePreview = body.take(3000),
            usage = root.optJSONObject("usage")
                ?.let(::parseChatUsage)
                ?: TokenUsage()
        )
    }

    private fun parseChatUsage(usage: JSONObject): TokenUsage {
        fun longOrNull(key: String): Long? =
            if (usage.has(key) && !usage.isNull(key)) usage.optLong(key) else null

        return TokenUsage(
            inputTokens = longOrNull("prompt_tokens"),
            outputTokens = longOrNull("completion_tokens"),
            totalTokens = longOrNull("total_tokens"),
            cachedInputTokens = JsonUtils.readString(
                usage,
                "prompt_tokens_details.cached_tokens"
            )?.toLongOrNull()
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
                val item = array.optJSONObject(index)
                    ?: continue

                val function = item.optJSONObject(
                    "function"
                ) ?: continue

                val functionName = function
                    .optString("name")
                    .trim()

                if (functionName.isBlank()) {
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
                        functionName = functionName,
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
        requestJson: JSONObject,
        mode: String,
        customJson: String
    ) {
        when (mode.trim().uppercase()) {
            "TEXT" -> {
                requestJson.put(
                    "response_format",
                    JSONObject().put(
                        "type",
                        "text"
                    )
                )
            }

            "JSON_OBJECT" -> {
                requestJson.put(
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
                ).getOrNull()?.let { value ->
                    requestJson.put(
                        "response_format",
                        value
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
        val requestId = requestIdHeaderNames
            .firstNotNullOfOrNull { name ->
                response.header(name)
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
