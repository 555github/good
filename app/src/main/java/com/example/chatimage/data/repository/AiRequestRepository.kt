package com.example.chatimage.data.repository

import com.example.chatimage.data.api.ApiOutcome
import com.example.chatimage.data.api.ChatApiClient
import com.example.chatimage.data.api.ChatWireMessage
import com.example.chatimage.data.api.ImageApiClient
import com.example.chatimage.data.api.ImageGenerationResult
import com.example.chatimage.data.api.ImageRequestOptions
import com.example.chatimage.data.api.ResponsesApiClient
import com.example.chatimage.data.api.TokenUsage
import com.example.chatimage.data.api.ToolCallEngine
import com.example.chatimage.data.model.AppSettings
import com.example.chatimage.data.model.Citation
import com.example.chatimage.data.model.RequestDiagnostics
import com.example.chatimage.data.model.RequestError
import com.example.chatimage.data.model.RequestRoute
import com.example.chatimage.data.model.ToolCallMode
import com.example.chatimage.data.model.WebSearchMode
import com.example.chatimage.data.model.WebSearchProvider
import com.example.chatimage.domain.IntentRouter

data class AssistantAnswer(
    val content: String,
    val citations: List<Citation> =
        emptyList(),
    val searchQueries: List<String> =
        emptyList(),
    val usedToolCalls: Boolean = false,
    val usedSearchFallback: Boolean =
        false,
    val usage: TokenUsage = TokenUsage()
)

class AiRequestRepository(
    private val chatApiClient: ChatApiClient,
    private val responsesApiClient:
        ResponsesApiClient,
    private val imageApiClient:
        ImageApiClient,
    private val toolCallEngine:
        ToolCallEngine,
    private val intentRouter:
        IntentRouter = IntentRouter()
) {

    suspend fun answerChat(
        apiProfile: ResolvedApiProfile,
        searchProfile:
            ResolvedSearchProfile?,
        settings: AppSettings,
        messages: List<ChatWireMessage>,
        userPrompt: String,
        forceSearchForThisRequest:
            Boolean = false,
        onStatus: suspend (String) -> Unit =
            {},
        onDelta: suspend (String) -> Unit =
            {}
    ): ApiOutcome<AssistantAnswer> {
        val searchMode =
            settings.search.mode

        val localRuleNeedsSearch =
            intentRouter.shouldSearch(
                prompt = userPrompt,
                settings = settings
            )

        val forceSearch =
            forceSearchForThisRequest ||
                searchMode ==
                WebSearchMode.ALWAYS

        val toolCallsEnabled =
            settings.search
                .toolCallMode !=
                ToolCallMode.DISABLED

        val usesResponses = apiProfile.profile.chatPath
            .trimEnd('/')
            .endsWith("/responses", ignoreCase = true)

        if (
            settings.search.provider == WebSearchProvider.MODEL_BUILT_IN &&
            searchMode != WebSearchMode.OFF
        ) {
            if (!usesResponses) {
                return ApiOutcome.Failure(
                    error = RequestError(
                        code = "BUILT_IN_SEARCH_REQUIRES_RESPONSES",
                        message = "模型内置联网需要将当前线路的聊天接口设为 /responses",
                        retryable = false
                    )
                )
            }

            val response = responsesApiClient.complete(
                resolvedProfile = apiProfile,
                settings = settings,
                messages = messages,
                builtInWebSearch = true,
                onStatus = onStatus,
                onDelta = onDelta
            )
            return mapDirectOutcome(response)
        }

        if (
            usesResponses &&
            settings.search.provider == WebSearchProvider.THIRD_PARTY &&
            searchMode == WebSearchMode.AUTO &&
            (toolCallsEnabled || localRuleNeedsSearch)
        ) {
            if (searchProfile == null) {
                return if (settings.search.allowOfflineAnswerAfterFailure) {
                    directChat(apiProfile, settings, messages, onDelta)
                } else {
                    noSearchProfileFailure()
                }
            }

            return mapToolOutcome(
                toolCallEngine.injectedSearchAnswer(
                    apiProfile = apiProfile,
                    searchProfile = searchProfile,
                    appSettings = settings,
                    messages = messages,
                    query = userPrompt,
                    usedFallback = true,
                    onStatus = onStatus,
                    onDelta = onDelta
                )
            )
        }

        /*
         * 强制搜索始终直接调用搜索 API，不需要让模型先判断。
         */
        if (forceSearch) {
            if (searchProfile == null) {
                return noSearchProfileFailure()
            }

            val outcome =
                toolCallEngine
                    .injectedSearchAnswer(
                        apiProfile =
                            apiProfile,
                        searchProfile =
                            searchProfile,
                        appSettings = settings,
                        messages = messages,
                        query = userPrompt,
                        usedFallback = false,
                        onStatus = onStatus,
                        onDelta = onDelta
                    )

            return mapToolOutcome(outcome)
        }

        /*
         * AUTO + Tool Calls 开启：
         * 即使本地关键词没有命中，也允许模型自行决定是否搜索。
         */
        if (
            searchMode ==
            WebSearchMode.AUTO &&
            toolCallsEnabled
        ) {
            if (searchProfile == null) {
                if (
                    settings.search
                        .allowOfflineAnswerAfterFailure
                ) {
                    return directChat(
                        apiProfile =
                            apiProfile,
                        settings = settings,
                        messages = messages,
                        onDelta = onDelta
                    )
                }

                return noSearchProfileFailure()
            }

            val outcome =
                toolCallEngine.answer(
                    apiProfile =
                        apiProfile,
                    searchProfile =
                        searchProfile,
                    appSettings = settings,
                    messages = messages,
                    forceSearch = false,
                    onStatus = onStatus,
                    onDelta = onDelta
                )

            return mapToolOutcome(outcome)
        }

        /*
         * AUTO + Tool Calls 关闭：
         * 仅当本地关键词判断需要联网时搜索。
         */
        if (
            searchMode ==
            WebSearchMode.AUTO &&
            localRuleNeedsSearch
        ) {
            if (searchProfile == null) {
                if (
                    settings.search
                        .allowOfflineAnswerAfterFailure
                ) {
                    return directChat(
                        apiProfile =
                            apiProfile,
                        settings = settings,
                        messages = messages,
                        onDelta = onDelta
                    )
                }

                return noSearchProfileFailure()
            }

            val outcome =
                toolCallEngine
                    .injectedSearchAnswer(
                        apiProfile =
                            apiProfile,
                        searchProfile =
                            searchProfile,
                        appSettings = settings,
                        messages = messages,
                        query = userPrompt,
                        usedFallback = false,
                        onStatus = onStatus,
                        onDelta = onDelta
                    )

            return mapToolOutcome(outcome)
        }

        return directChat(
            apiProfile = apiProfile,
            settings = settings,
            messages = messages,
            onDelta = onDelta
        )
    }

    suspend fun generateImage(
        apiProfile: ResolvedApiProfile,
        settings: AppSettings,
        options: ImageRequestOptions
    ): ApiOutcome<ImageGenerationResult> {
        return imageApiClient.generate(
            resolvedProfile = apiProfile,
            appSettings = settings,
            options = options
        )
    }

    private suspend fun directChat(
        apiProfile: ResolvedApiProfile,
        settings: AppSettings,
        messages: List<ChatWireMessage>,
        onDelta: suspend (String) -> Unit
    ): ApiOutcome<AssistantAnswer> {
        val outcome = if (
            apiProfile.profile.chatPath
                .trimEnd('/')
                .endsWith("/responses", ignoreCase = true)
        ) {
            responsesApiClient.complete(
                resolvedProfile = apiProfile,
                settings = settings,
                messages = messages,
                onDelta = onDelta
            )
        } else {
            chatApiClient.complete(
                resolvedProfile =
                    apiProfile,
                appSettings = settings,
                messages = messages,
                onDelta = onDelta
            )
        }

        return mapDirectOutcome(outcome)
    }

    private fun mapDirectOutcome(
        outcome: ApiOutcome<com.example.chatimage.data.api.ChatCompletionResult>
    ): ApiOutcome<AssistantAnswer> {
        return when (outcome) {
            is ApiOutcome.Failure -> {
                outcome
            }

            is ApiOutcome.Success -> {
                ApiOutcome.Success(
                    value = AssistantAnswer(
                        content =
                            outcome.value.content,
                        citations = outcome.value.citations,
                        searchQueries = outcome.value.searchQueries,
                        usedToolCalls = outcome.value.searchQueries.isNotEmpty(),
                        usage = outcome.value.usage
                    ),
                    diagnostics =
                        outcome.diagnostics
                )
            }
        }
    }

    private fun mapToolOutcome(
        outcome:
            ApiOutcome<
                com.example.chatimage
                    .data.api.ToolEngineResult
                >
    ): ApiOutcome<AssistantAnswer> {
        return when (outcome) {
            is ApiOutcome.Failure -> {
                outcome
            }

            is ApiOutcome.Success -> {
                ApiOutcome.Success(
                    value = AssistantAnswer(
                        content =
                            outcome.value.content,
                        citations =
                            outcome.value.citations,
                        searchQueries =
                            outcome
                                .value
                                .searchQueries,
                        usedToolCalls =
                            outcome
                                .value
                                .usedToolCalls,
                        usedSearchFallback =
                            outcome
                                .value
                                .usedFallback,
                        usage = outcome.value.usage
                    ),
                    diagnostics =
                        outcome.diagnostics
                )
            }
        }
    }

    private fun noSearchProfileFailure():
        ApiOutcome.Failure {
        return ApiOutcome.Failure(
            error = RequestError(
                code =
                    "NO_SEARCH_PROFILE",
                message =
                    "当前请求需要联网，但尚未配置可用的搜索 API。请在设置中新增并启用搜索配置。",
                retryable = false
            ),
            diagnostics = null
        )
    }
}
