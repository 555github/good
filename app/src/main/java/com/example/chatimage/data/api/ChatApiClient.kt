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
            val profile =
                resolvedProfile.profile

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

            var call: Call? = null

            try {
                val messageArray = JSONArray()

                messages.forEach {
                    messageArray.put(it.toJson())
                }

                val standardJson = JSONObject()
                    .put("model", model)
                    .put(
                        "messages",
                        messageArray
                    )
                    .put("stream", stream)

                if (
                    chatSettings
                        .temperatureEnabled
                ) {
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
                    chatSettings
                        .maxTokensEnabled &&
                    chatSettings
                        .maxTokensFieldName
                        .isNotBlank()
                ) {
                    standardJson.put(
                        chatSettings
                            .maxTokensFieldName,
                        chatSettings.maxTokens
                    )
                }

                if (
                    chatSettings
                        .frequencyPenaltyEnabled
                ) {
                    standardJson.put(
                        "frequency_penalty",
                        chatSettings
                            .frequencyPenalty
                    )
                }

                if (
                    chatSettings
                        .presencePenaltyEnabled
                ) {
                    standardJson.put(
                        "presence_penalty",
                        chatSettings
                            .presencePenalty
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
                    chatSettings
                        .stopSequences
                        .isNotEmpty()
                ) {
                    val stopArray = JSONArray()

                    chatSettings
                        .stopSequences
                        .filter {
                            it.isNotEmpty()
                        }
                        .forEach {
                            stopArray.put(it)
                        }

                    if (stopArray.length() == 1) {
                        standardJson.put(
                            "stop",
                            stopArray.optString(0)
                        )
                    } else if (
                        stopArray.length() > 1
                    ) {
                        standardJson.put(
                            "stop",
                            stopArray
                        )
                    }
                }

                applyResponseFormat(
                    standardJson,
                    chatSettings
                        .responseFormatMode,
                    chatSettings
                        .responseFormatJson
                )

                if (tools.isNotEmpty()) {
                    val toolsArray = JSONArray()

                    tools.forEach {
                        toolsArray.put(
                            it.toOpenAiJson()
                        )
                    }

                    standardJson.put(
                        "tools",
                        toolsArray
                    )

                    standardJson.put(
                        "tool_choice",
                        toolChoiceOverride
                            ?: appSettings
                                .search
                                .toolChoice
                    )
                }

                val extraJson =
                    JsonUtils.parseObjectOrEmpty(
                        chatSettings
                            .extraRequestJson
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

                val builder = Request.Builder()
                    .url(endpoint)
                    .post(requestBody)

                HeaderUtils.applyAuthentication(
                    builder = builder,
                    mode = profile
                        .authenticationMode,
                    apiKey = resolvedProfile.apiKey,
                    headerName = profile
                        .authorizationHeaderName,
                    prefix = profile
                        .authorizationPrefix
                )

                HeaderUtils.applyCustomHeaders(
                    builder = builder,
                    entries = HeaderUtils.decode(
                        profile.customHeadersJson
                    ),
                    category =
                        RequestCategory.CHAT
                )

                if (stream) {
                    builder.header(
                        "Accept",
                        "text/event-stream"
                    )
                } else {
                    builder.header(
                        "Accept",
                        "application/json"
                    )
                }

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

                call = client.newCall(
                    builder.build()
                )

                val cancellationHandle =
                    coroutineContext.job
                        .invokeOnCompletion {
                            cause ->
                            if (
                                cause is
                                    CancellationException
                            ) {
                                call?.cancel()
                            }
                        }

                try {
                    call.execute().use {
                        response ->
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
                                        durationMs =
                                            duration,
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
                                    response = response
                                )

                            return@withContext
                                ApiOutcome.Success(
                                    value = result,
                                    diagnostics =
                                        diagnostics
                                )
                        }

                        val result = parseStreamResponse(
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
                                response = response
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
                call?.cancel()
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
        val chatSettings =
            appSettings.chatParameters

        val protocol = chatSettings
            .streamProtocol

        val fullText = StringBuilder()
        var finishReason: String? = null

        while (!source.exhausted()) {
            coroutineContext.ensureActive()

            val rawLine =
                source.readUtf8Line()
                    ?: continue

            val line = rawLine.trim()

            if (line.isBlank()) {
                continue
            }

            val payload = when (protocol) {
                StreamProtocol.SSE -> {
                    parseSsePayload(
                        line,
                        chatSettings
                            .sseDataPrefix
                    )
                }

                StreamProtocol.JSON_LINES -> {
                    line
                }

                StreamProtocol.AUTO -> {
                    if (
                        line.startsWith(
                            chatSettings
                                .sseDataPrefix
                        )
                    ) {
                        parseSsePayload(
                            line,
                            chatSettings
                                .sseDataPrefix
                        )
                    } else if (
                        line.startsWith("{")
                    ) {
                        line
                    } else {
                        null
                    }
                }
            } ?: continue

            if (
                payload ==
                chatSettings.sseDoneMarker
            ) {
                break
            }

            val root = try {
                JSONObject(payload)
            } catch (_: Exception) {
                continue
            }

            val fragment =
                JsonUtils.readString(
                    root,
                    chatSettings
                        .streamTextPath
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
                listOf(
                    ToolCall(
                        id = "legacy_" +
                            UUID.randomUUID(),
                        type = "function",
                        functionName =
                            legacyFunctionCall
                                .optString("name"),
                        argumentsJson =
                            legacyFunctionCall
                                .optString(
                                    "arguments",
                                    "{}"
                                )
                    )
                )
            } else {
                toolCalls
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
                index in 0 until
                    array.length()
            ) {
                val item =
                    array.optJSONObject(index)
                        ?: continue

                val function =
                    item.optJSONObject(
                        "function"
                    ) ?: continue

                val name =
                    function
                        .optString("name")
                        .trim()

                if (name.isBlank()) {
                    continue
                }

                add(
                    ToolCall(
                        id = item.optString(
                            "id"
                        ).ifBlank {
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
        when (
            mode.trim().uppercase()
        ) {
            "TEXT" -> {
                json.put(
                    "response_format",
                    JSONObject()
                        .put("type", "text")
                )
            }

            "JSON_OBJECT" -> {
                json.put(
                    "response_format",
                    JSONObject()
                        .put(
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
        response: okhttp3.Response
    ): RequestDiagnostics {
        val requestId =
            listOf(
                "x-request-id",
                "request-id",
                "cf-ray"
            ).firstNotNullOfOrNull {
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
