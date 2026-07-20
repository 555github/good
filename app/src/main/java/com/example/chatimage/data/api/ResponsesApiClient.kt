package com.example.chatimage.data.api

import com.example.chatimage.data.model.AppSettings
import com.example.chatimage.data.model.Citation
import com.example.chatimage.data.model.RequestDiagnostics
import com.example.chatimage.data.repository.ResolvedApiProfile
import com.example.chatimage.util.ErrorParser
import com.example.chatimage.util.HeaderUtils
import com.example.chatimage.util.JsonUtils
import com.example.chatimage.util.RequestCategory
import com.example.chatimage.util.UrlUtils
import java.util.concurrent.CancellationException
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject

class ResponsesApiClient(
    private val clientFactory: HttpClientFactory = HttpClientFactory()
) {
    suspend fun complete(
        resolvedProfile: ResolvedApiProfile,
        settings: AppSettings,
        messages: List<ChatWireMessage>,
        builtInWebSearch: Boolean = false,
        requireBuiltInWebSearch: Boolean = false,
        onStatus: suspend (String) -> Unit = {},
        onDelta: suspend (String) -> Unit = {}
    ): ApiOutcome<ChatCompletionResult> = withContext(Dispatchers.IO) {
        val profile = resolvedProfile.profile
        val endpoint = UrlUtils.resolveEndpoint(profile.baseUrl, profile.chatPath)
        val startedAt = System.currentTimeMillis()

        try {
            val requestJson = buildRequestJson(
                model = profile.chatModel,
                settings = settings,
                messages = messages,
                builtInWebSearch = builtInWebSearch,
                requireBuiltInWebSearch = requireBuiltInWebSearch
            )
            val body = requestJson.toString().toRequestBody(
                "application/json; charset=utf-8".toMediaType()
            )
            val builder = Request.Builder()
                .url(endpoint)
                .post(body)
                .header(
                    "Accept",
                    if (settings.chatParameters.streamEnabled) {
                        "text/event-stream"
                    } else {
                        "application/json"
                    }
                )

            HeaderUtils.applyAuthentication(
                builder = builder,
                mode = profile.authenticationMode,
                apiKey = resolvedProfile.apiKey,
                headerName = profile.authorizationHeaderName,
                prefix = profile.authorizationPrefix
            )
            HeaderUtils.applyCustomHeaders(
                builder = builder,
                entries = HeaderUtils.decode(profile.customHeadersJson),
                category = RequestCategory.CHAT
            )

            val call = clientFactory.chatClient(settings.timeouts)
                .newCall(builder.build())
            val cancellationHandle = coroutineContext.job.invokeOnCompletion { cause ->
                if (cause is CancellationException) call.cancel()
            }

            try {
                call.execute().use { response ->
                    processResponse(
                        response = response,
                        endpoint = endpoint,
                        model = profile.chatModel,
                        startedAt = startedAt,
                        settings = settings,
                        onStatus = onStatus,
                        onDelta = onDelta
                    )
                }
            } finally {
                cancellationHandle.dispose()
            }
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            val parsed = ErrorParser.fromException(
                exception = exception,
                endpoint = endpoint,
                model = profile.chatModel,
                durationMs = System.currentTimeMillis() - startedAt
            )
            ApiOutcome.Failure(parsed.first, parsed.second)
        }
    }

    internal fun buildRequestJson(
        model: String,
        settings: AppSettings,
        messages: List<ChatWireMessage>,
        builtInWebSearch: Boolean,
        requireBuiltInWebSearch: Boolean = false
    ): JSONObject {
        val parameters = settings.chatParameters
        val input = JSONArray()

        messages.forEach { message ->
            val item = JSONObject().put("role", message.role)
            if (message.contentParts.isNotEmpty()) {
                val content = JSONArray()
                message.contentParts.forEach { part ->
                    when (part) {
                        is ChatContentPart.Text -> content.put(
                            JSONObject()
                                .put("type", "input_text")
                                .put("text", part.text)
                        )
                        is ChatContentPart.ImageUrl -> content.put(
                            JSONObject()
                                .put("type", "input_image")
                                .put("image_url", part.url)
                                .apply {
                                    part.detail?.takeIf(String::isNotBlank)?.let {
                                        put("detail", it)
                                    }
                                }
                        )
                        is ChatContentPart.InputFile -> content.put(
                            JSONObject()
                                .put("type", "input_file")
                                .put("filename", part.fileName)
                                .put("file_data", part.dataUrl)
                        )
                    }
                }
                item.put("content", content)
            } else {
                item.put("content", message.content.orEmpty())
            }
            input.put(item)
        }

        val standard = JSONObject()
            .put("model", model)

        if (builtInWebSearch) {
            standard.put(
                "tools",
                JSONArray().put(
                    JSONObject().put(
                        "type",
                        settings.search.builtInToolType.ifBlank { "web_search" }
                    )
                )
            )
            standard.put(
                "include",
                JSONArray().put("web_search_call.action.sources")
            )
            if (requireBuiltInWebSearch) {
                standard.put("tool_choice", "required")
            }
        }

        standard
            .put("input", input)
            .put("stream", parameters.streamEnabled)

        if (parameters.temperatureEnabled) {
            standard.put("temperature", parameters.temperature)
        }
        if (parameters.topPEnabled) {
            standard.put("top_p", parameters.topP)
        }
        if (parameters.maxTokensEnabled) {
            standard.put("max_output_tokens", parameters.maxTokens)
        }
        if (
            parameters.reasoningEnabled &&
            parameters.reasoningFieldPath.isNotBlank() &&
            parameters.reasoningValue.isNotBlank()
        ) {
            JsonUtils.putObjectPath(
                standard,
                parameters.reasoningFieldPath,
                parameters.reasoningValue
            )
        }
        return JsonUtils.deepMerge(
            base = standard,
            overlay = JsonUtils.parseObjectOrEmpty(parameters.extraRequestJson),
            overlayWins = true
        )
    }

    private suspend fun processResponse(
        response: Response,
        endpoint: String,
        model: String,
        startedAt: Long,
        settings: AppSettings,
        onStatus: suspend (String) -> Unit,
        onDelta: suspend (String) -> Unit
    ): ApiOutcome<ChatCompletionResult> {
        val responseBody = response.body
        val diagnostics = RequestDiagnostics(
            endpoint = endpoint,
            method = response.request.method,
            model = model,
            httpStatus = response.code,
            durationMs = System.currentTimeMillis() - startedAt,
            contentType = response.header("Content-Type"),
            server = response.header("Server"),
            requestId = settings.diagnostics.requestIdHeaderNames
                .firstNotNullOfOrNull(response::header)
        )

        if (responseBody == null) {
            return ApiOutcome.Failure(
                com.example.chatimage.data.model.RequestError(
                    code = "EMPTY_RESPONSE",
                    message = "Responses 接口返回内容为空",
                    httpStatus = response.code
                ),
                diagnostics
            )
        }
        if (!response.isSuccessful) {
            val parsed = ErrorParser.fromHttpResponse(
                response = response,
                body = responseBody.string(),
                endpoint = endpoint,
                model = model,
                durationMs = diagnostics.durationMs,
                requestIdHeaderNames = settings.diagnostics.requestIdHeaderNames
            )
            return ApiOutcome.Failure(parsed.first, parsed.second)
        }

        val result = if (settings.chatParameters.streamEnabled) {
            parseStream(responseBody.source(), onStatus, onDelta)
        } else {
            parseNonStreamBody(responseBody.string())
        }
        return ApiOutcome.Success(result, diagnostics)
    }

    private suspend fun parseStream(
        source: okio.BufferedSource,
        onStatus: suspend (String) -> Unit,
        onDelta: suspend (String) -> Unit
    ): ChatCompletionResult {
        val text = StringBuilder()
        var completed: ChatCompletionResult? = null
        var eventCount = 0

        while (!source.exhausted()) {
            coroutineContext.ensureActive()
            val line = source.readUtf8Line()?.trim().orEmpty()
            if (!line.startsWith("data:")) continue
            val payload = line.removePrefix("data:").trim()
            if (payload.isBlank() || payload == "[DONE]") continue
            val event = runCatching { JSONObject(payload) }.getOrNull() ?: continue
            eventCount++

            when (event.optString("type")) {
                "response.output_text.delta" -> {
                    val delta = event.optString("delta")
                    if (delta.isNotEmpty()) {
                        text.append(delta)
                        onDelta(delta)
                    }
                }
                "response.web_search_call.in_progress",
                "response.web_search_call.searching" ->
                    onStatus("模型正在联网搜索...")
                "response.web_search_call.completed" ->
                    onStatus("模型已完成网页检索，正在整理结果...")
                "response.completed" -> {
                    event.optJSONObject("response")?.let {
                        completed = parseResponseObject(it)
                    }
                }
                "response.failed" -> {
                    val message = JsonUtils.readString(
                        event,
                        "response.error.message"
                    ) ?: "Responses 请求失败"
                    throw IllegalStateException(message)
                }
            }
        }

        if (eventCount == 0) {
            throw IllegalStateException("Responses 流式接口没有返回可解析的 SSE 事件")
        }
        val final = completed
        return if (final == null) {
            ChatCompletionResult(content = text.toString())
        } else {
            final.copy(content = final.content.ifBlank { text.toString() })
        }
    }

    companion object {
        fun parseNonStreamBody(body: String): ChatCompletionResult {
            return parseResponseObject(JSONObject(body))
        }

        private fun parseResponseObject(root: JSONObject): ChatCompletionResult {
            val output = root.optJSONArray("output") ?: JSONArray()
            val text = StringBuilder()
            val citations = mutableListOf<Citation>()
            val queries = mutableListOf<String>()

            for (index in 0 until output.length()) {
                val item = output.optJSONObject(index) ?: continue
                if (item.optString("type") == "web_search_call") {
                    item.optJSONObject("action")?.let { action ->
                        action.optString("query")
                            .takeIf(String::isNotBlank)
                            ?.let(queries::add)
                    }
                }
                val content = item.optJSONArray("content") ?: continue
                for (partIndex in 0 until content.length()) {
                    val part = content.optJSONObject(partIndex) ?: continue
                    if (part.optString("type") == "output_text") {
                        text.append(part.optString("text"))
                    }
                    val annotations = part.optJSONArray("annotations") ?: JSONArray()
                    for (annotationIndex in 0 until annotations.length()) {
                        val annotation = annotations.optJSONObject(annotationIndex) ?: continue
                        if (annotation.optString("type") == "url_citation") {
                            val url = annotation.optString("url")
                            if (url.isNotBlank()) {
                                citations += Citation(
                                    index = citations.size + 1,
                                    title = annotation.optString("title").ifBlank { url },
                                    url = url
                                )
                            }
                        }
                    }
                }
            }

            val usage = root.optJSONObject("usage")
            return ChatCompletionResult(
                content = text.toString(),
                finishReason = root.optString("status"),
                citations = citations.distinctBy(Citation::url)
                    .mapIndexed { index, citation -> citation.copy(index = index + 1) },
                searchQueries = queries.distinct(),
                usage = TokenUsage(
                    inputTokens = usage?.optLongOrNull("input_tokens"),
                    outputTokens = usage?.optLongOrNull("output_tokens"),
                    totalTokens = usage?.optLongOrNull("total_tokens"),
                    cachedInputTokens = JsonUtils.readString(
                        usage,
                        "input_tokens_details.cached_tokens"
                    )?.toLongOrNull()
                )
            )
        }

        private fun JSONObject.optLongOrNull(key: String): Long? {
            return if (has(key) && !isNull(key)) optLong(key) else null
        }
    }
}
