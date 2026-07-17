package com.example.chatimage.data.api

import com.example.chatimage.data.model.AppSettings
import com.example.chatimage.data.model.Citation
import com.example.chatimage.data.model.RequestDiagnostics
import com.example.chatimage.data.model.ToolCallMode
import com.example.chatimage.data.repository.ResolvedApiProfile
import com.example.chatimage.data.repository.ResolvedSearchProfile
import kotlinx.coroutines.CancellationException
import org.json.JSONArray
import org.json.JSONObject

class ToolCallEngine(
    private val chatApiClient: ChatApiClient,
    private val searchApiClient: SearchApiClient
) {

    suspend fun answer(
        apiProfile: ResolvedApiProfile,
        searchProfile: ResolvedSearchProfile?,
        appSettings: AppSettings,
        messages: List<ChatWireMessage>,
        forceSearch: Boolean,
        onStatus: suspend (String) -> Unit = {},
        onDelta: suspend (String) -> Unit = {}
    ): ApiOutcome<ToolEngineResult> {
        val searchSettings =
            appSettings.search

        if (searchProfile == null) {
            return ApiOutcome.Failure(
                error =
                    com.example.chatimage
                        .data.model
                        .RequestError(
                            code =
                                "NO_SEARCH_PROFILE",
                            message =
                                "尚未配置可用的搜索 API。",
                            retryable = false
                        )
            )
        }

        if (
            forceSearch ||
            searchSettings.toolCallMode ==
                ToolCallMode.DISABLED
        ) {
            return injectedSearchAnswer(
                apiProfile = apiProfile,
                searchProfile =
                    searchProfile,
                appSettings = appSettings,
                messages = messages,
                query = latestUserText(
                    messages
                ),
                usedFallback = false,
                onStatus = onStatus,
                onDelta = onDelta
            )
        }

        val toolDefinition =
            ToolDefinition(
                name =
                    searchSettings.toolName,
                description =
                    searchSettings
                        .toolDescription,
                queryParameterName =
                    searchSettings
                        .toolQueryParameterName,
                countParameterName =
                    searchSettings
                        .toolCountParameterName
            )

        val workingMessages =
            messages.toMutableList()

        val allResults =
            mutableListOf<
                com.example.chatimage
                    .data.model
                    .SearchResult
                >()

        val allQueries =
            mutableListOf<String>()

        var lastDiagnostics:
            RequestDiagnostics? = null

        try {
            for (
                round in 0 until
                    searchSettings
                        .maximumToolRounds
                        .coerceIn(1, 10)
            ) {
                onStatus(
                    if (round == 0) {
                        "正在判断是否需要联网……"
                    } else {
                        "正在处理第 ${round + 1} 轮工具调用……"
                    }
                )

                val decision =
                    chatApiClient.complete(
                        resolvedProfile =
                            apiProfile,
                        appSettings =
                            appSettings,
                        messages =
                            workingMessages,
                        streamOverride = false,
                        tools =
                            listOf(
                                toolDefinition
                            ),
                        useToolDecisionTimeout =
                            true
                    )

                when (decision) {
                    is ApiOutcome.Failure -> {
                        if (
                            searchSettings
                                .fallbackToInjectedSearch
                        ) {
                            onStatus(
                                "Tool Calls 不兼容，已回退到普通搜索模式"
                            )

                            return injectedSearchAnswer(
                                apiProfile =
                                    apiProfile,
                                searchProfile =
                                    searchProfile,
                                appSettings =
                                    appSettings,
                                messages =
                                    messages,
                                query =
                                    latestUserText(
                                        messages
                                    ),
                                usedFallback =
                                    true,
                                onStatus =
                                    onStatus,
                                onDelta =
                                    onDelta
                            )
                        }

                        return decision
                    }

                    is ApiOutcome.Success -> {
                        lastDiagnostics =
                            decision.diagnostics

                        val value =
                            decision.value

                        if (
                            value.toolCalls
                                .isEmpty()
                        ) {
                            if (
                                value.content
                                    .isNotBlank()
                            ) {
                                return ApiOutcome.Success(
                                    value =
                                        ToolEngineResult(
                                            content =
                                                value.content,
                                            citations =
                                                buildCitations(
                                                    allResults
                                                ),
                                            searchQueries =
                                                allQueries,
                                            usedToolCalls =
                                                true,
                                            usedFallback =
                                                false,
                                            diagnostics =
                                                decision
                                                    .diagnostics
                                        ),
                                    diagnostics =
                                        decision.diagnostics
                                )
                            }

                            break
                        }

                        workingMessages +=
                            ChatWireMessage(
                                role =
                                    "assistant",
                                content =
                                    value.content
                                        .takeIf {
                                            it.isNotBlank()
                                        },
                                toolCalls =
                                    value.toolCalls
                            )

                        val calls =
                            value.toolCalls.take(
                                searchSettings
                                    .maximumCallsPerRound
                                    .coerceIn(1, 10)
                            )

                        for (call in calls) {
                            if (
                                call.functionName !=
                                searchSettings
                                    .toolName
                            ) {
                                workingMessages +=
                                    ChatWireMessage(
                                        role = "tool",
                                        toolCallId =
                                            call.id,
                                        content =
                                            JSONObject()
                                                .put(
                                                    "error",
                                                    "不允许执行未注册工具：${call.functionName}"
                                                )
                                                .toString()
                                    )

                                continue
                            }

                            val arguments =
                                try {
                                    JSONObject(
                                        call.argumentsJson
                                    )
                                } catch (_: Exception) {
                                    JSONObject()
                                }

                            val query =
                                arguments
                                    .optString(
                                        searchSettings
                                            .toolQueryParameterName
                                    )
                                    .trim()
                                    .take(
                                        searchSettings
                                            .maximumQueryCharacters
                                            .coerceAtLeast(
                                                1
                                            )
                                    )

                            val count =
                                arguments.optInt(
                                    searchSettings
                                        .toolCountParameterName,
                                    searchSettings
                                        .resultCount
                                ).coerceIn(1, 10)

                            if (query.isBlank()) {
                                workingMessages +=
                                    ChatWireMessage(
                                        role = "tool",
                                        toolCallId =
                                            call.id,
                                        content =
                                            JSONObject()
                                                .put(
                                                    "error",
                                                    "搜索关键词为空"
                                                )
                                                .toString()
                                    )

                                continue
                            }

                            onStatus(
                                "正在搜索：$query"
                            )

                            val searchResult =
                                searchApiClient.search(
                                    resolvedProfile =
                                        searchProfile,
                                    appSettings =
                                        appSettings,
                                    query = query,
                                    countOverride =
                                        count
                                )

                            when (searchResult) {
                                is ApiOutcome.Failure -> {
                                    workingMessages +=
                                        ChatWireMessage(
                                            role =
                                                "tool",
                                            toolCallId =
                                                call.id,
                                            content =
                                                JSONObject()
                                                    .put(
                                                        "error",
                                                        searchResult
                                                            .error
                                                            .message
                                                    )
                                                    .toString()
                                        )
                                }

                                is ApiOutcome.Success -> {
                                    val results =
                                        searchResult
                                            .value
                                            .results

                                    allQueries += query
                                    allResults += results

                                    workingMessages +=
                                        ChatWireMessage(
                                            role =
                                                "tool",
                                            toolCallId =
                                                call.id,
                                            content =
                                                searchResultsToJson(
                                                    query,
                                                    results
                                                )
                                        )
                                }
                            }
                        }
                    }
                }
            }

            onStatus("正在整理联网结果……")

            val finalAnswer =
                chatApiClient.complete(
                    resolvedProfile =
                        apiProfile,
                    appSettings =
                        appSettings,
                    messages =
                        workingMessages,
                    streamOverride =
                        searchSettings
                            .finalAnswerStreamEnabled,
                    tools = emptyList(),
                    onDelta = onDelta
                )

            return when (finalAnswer) {
                is ApiOutcome.Failure -> {
                    finalAnswer
                }

                is ApiOutcome.Success -> {
                    ApiOutcome.Success(
                        value =
                            ToolEngineResult(
                                content =
                                    finalAnswer
                                        .value
                                        .content,
                                citations =
                                    buildCitations(
                                        allResults
                                    ),
                                searchQueries =
                                    allQueries,
                                usedToolCalls =
                                    true,
                                usedFallback =
                                    false,
                                diagnostics =
                                    finalAnswer
                                        .diagnostics
                            ),
                        diagnostics =
                            finalAnswer
                                .diagnostics
                    )
                }
            }
        } catch (
            exception: CancellationException
        ) {
            throw exception
        } catch (exception: Exception) {
            if (
                searchSettings
                    .fallbackToInjectedSearch
            ) {
                return injectedSearchAnswer(
                    apiProfile =
                        apiProfile,
                    searchProfile =
                        searchProfile,
                    appSettings =
                        appSettings,
                    messages = messages,
                    query =
                        latestUserText(
                            messages
                        ),
                    usedFallback = true,
                    onStatus = onStatus,
                    onDelta = onDelta
                )
            }

            return ApiOutcome.Failure(
                error =
                    com.example.chatimage
                        .data.model
                        .RequestError(
                            code =
                                "TOOL_ENGINE_ERROR",
                            message =
                                exception.message
                                    ?: "Tool Calls 执行失败",
                            retryable = true
                        ),
                diagnostics =
                    lastDiagnostics
            )
        }
    }

    suspend fun injectedSearchAnswer(
        apiProfile: ResolvedApiProfile,
        searchProfile:
            ResolvedSearchProfile,
        appSettings: AppSettings,
        messages: List<ChatWireMessage>,
        query: String,
        usedFallback: Boolean,
        onStatus: suspend (String) -> Unit = {},
        onDelta: suspend (String) -> Unit = {}
    ): ApiOutcome<ToolEngineResult> {
        if (query.isBlank()) {
            return ApiOutcome.Failure(
                error =
                    com.example.chatimage
                        .data.model
                        .RequestError(
                            code =
                                "EMPTY_SEARCH_QUERY",
                            message =
                                "无法从当前消息中提取搜索关键词。"
                        )
            )
        }

        onStatus("正在搜索：$query")

        val searchOutcome =
            searchApiClient.search(
                resolvedProfile =
                    searchProfile,
                appSettings =
                    appSettings,
                query = query
            )

        if (
            searchOutcome is
                ApiOutcome.Failure
        ) {
            if (
                appSettings
                    .search
                    .allowOfflineAnswerAfterFailure
            ) {
                onStatus(
                    "搜索失败，正在使用模型已有知识回答"
                )

                val offline =
                    chatApiClient.complete(
                        resolvedProfile =
                            apiProfile,
                        appSettings =
                            appSettings,
                        messages = messages,
                        onDelta = onDelta
                    )

                return when (offline) {
                    is ApiOutcome.Failure ->
                        offline

                    is ApiOutcome.Success ->
                        ApiOutcome.Success(
                            value =
                                ToolEngineResult(
                                    content =
                                        offline
                                            .value
                                            .content,
                                    citations =
                                        emptyList(),
                                    searchQueries =
                                        listOf(query),
                                    usedToolCalls =
                                        false,
                                    usedFallback =
                                        true,
                                    diagnostics =
                                        offline
                                            .diagnostics
                                ),
                            diagnostics =
                                offline
                                    .diagnostics
                        )
                }
            }

            return searchOutcome
        }

        searchOutcome as
            ApiOutcome.Success

        val results =
            searchOutcome.value.results

        val contextMessage =
            buildSearchContext(
                appSettings = appSettings,
                query = query,
                results = results
            )

        val augmented =
            messages.toMutableList()

        val insertionIndex =
            augmented.indexOfLast {
                it.role == "user"
            }.takeIf {
                it >= 0
            } ?: augmented.size

        augmented.add(
            insertionIndex,
            ChatWireMessage(
                role = "system",
                content = contextMessage
            )
        )

        onStatus("正在根据搜索结果生成回答……")

        val finalOutcome =
            chatApiClient.complete(
                resolvedProfile =
                    apiProfile,
                appSettings =
                    appSettings,
                messages = augmented,
                streamOverride =
                    appSettings
                        .search
                        .finalAnswerStreamEnabled,
                onDelta = onDelta
            )

        return when (finalOutcome) {
            is ApiOutcome.Failure ->
                finalOutcome

            is ApiOutcome.Success ->
                ApiOutcome.Success(
                    value =
                        ToolEngineResult(
                            content =
                                finalOutcome
                                    .value
                                    .content,
                            citations =
                                buildCitations(
                                    results
                                ),
                            searchQueries =
                                listOf(query),
                            usedToolCalls =
                                false,
                            usedFallback =
                                usedFallback,
                            diagnostics =
                                finalOutcome
                                    .diagnostics
                        ),
                    diagnostics =
                        finalOutcome
                            .diagnostics
                )
        }
    }

    private fun buildSearchContext(
        appSettings: AppSettings,
        query: String,
        results: List<
            com.example.chatimage
                .data.model.SearchResult
            >
    ): String {
        return buildString {
            appendLine(
                appSettings
                    .search
                    .webContextTemplate
            )

            appendLine()
            appendLine(
                "搜索关键词：$query"
            )
            appendLine()

            results.forEachIndexed {
                index,
                result ->

                appendLine("[${index + 1}]")
                appendLine(
                    "标题：${result.title}"
                )

                if (result.url.isNotBlank()) {
                    appendLine(
                        "网址：${result.url}"
                    )
                }

                if (
                    !result.publishedAt
                        .isNullOrBlank()
                ) {
                    appendLine(
                        "日期：${result.publishedAt}"
                    )
                }

                if (
                    !result.source
                        .isNullOrBlank()
                ) {
                    appendLine(
                        "来源：${result.source}"
                    )
                }

                appendLine(
                    "摘要：${result.snippet}"
                )
                appendLine()
            }
        }
    }

    private fun searchResultsToJson(
        query: String,
        results: List<
            com.example.chatimage
                .data.model.SearchResult
            >
    ): String {
        val array = JSONArray()

        results.forEachIndexed {
            index,
            result ->
            array.put(
                JSONObject()
                    .put(
                        "index",
                        index + 1
                    )
                    .put(
                        "title",
                        result.title
                    )
                    .put(
                        "url",
                        result.url
                    )
                    .put(
                        "snippet",
                        result.snippet
                    )
                    .put(
                        "published_at",
                        result.publishedAt
                    )
                    .put(
                        "source",
                        result.source
                    )
            )
        }

        return JSONObject()
            .put("
