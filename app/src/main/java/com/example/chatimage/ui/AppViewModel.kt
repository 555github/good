package com.example.chatimage.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.chatimage.ChatImageApplication
import com.example.chatimage.data.api.ApiOutcome
import com.example.chatimage.data.api.ImageRequestOptions
import com.example.chatimage.data.database.ApiProfileEntity
import com.example.chatimage.data.database.MessageEntity
import com.example.chatimage.data.database.MessageImageEntity
import com.example.chatimage.data.database.SearchProfileEntity
import com.example.chatimage.data.model.AppSettings
import com.example.chatimage.data.model.MessageStatus
import com.example.chatimage.data.model.RequestError
import com.example.chatimage.data.model.RequestRoute
import com.example.chatimage.data.model.RouteDecision
import com.example.chatimage.data.model.WebSearchMode
import com.example.chatimage.util.ImageFileUtils
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

class AppViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val app =
        application as
            ChatImageApplication

    private val container =
        app.container

    private val conversationRepository =
        container.conversationRepository

    private val apiProfileRepository =
        container.apiProfileRepository

    private val searchProfileRepository =
        container.searchProfileRepository

    private val settingsStore =
        container.settingsStore

    private val aiRequestRepository =
        container.aiRequestRepository

    private val intentRouter =
        container.intentRouter

    private val contextManager =
        container.contextManager

    private val imageParameterParser =
        container.imageParameterParser

    private val promptOptimizer =
        container.promptOptimizer

    private val exportUtils =
        container.exportUtils

    private val _uiState =
        MutableStateFlow(
            AppUiState()
        )

    val uiState =
        _uiState.asStateFlow()

    private var conversationsJob:
        Job? = null

    private var messagesJob:
        Job? = null

    private var apiProfilesJob:
        Job? = null

    private var searchProfilesJob:
        Job? = null

    private var settingsJob:
        Job? = null

    private var requestJob:
        Job? = null

    init {
        initialize()
    }

    private fun initialize() {
        viewModelScope.launch {
            apiProfileRepository
                .ensureDefaultProfile()

            startSettingsObservation()
            startApiProfileObservation()
            startSearchProfileObservation()
            startConversationObservation()
        }
    }

    private fun startSettingsObservation() {
        settingsJob?.cancel()

        settingsJob =
            viewModelScope.launch {
                settingsStore
                    .settingsFlow
                    .collect { settings ->
                        _uiState.value =
                            _uiState.value.copy(
                                appSettings =
                                    settings
                            )
                    }
            }
    }

    private fun startApiProfileObservation() {
        apiProfilesJob?.cancel()

        apiProfilesJob =
            viewModelScope.launch {
                apiProfileRepository
                    .observeAll()
                    .collect { profiles ->
                        _uiState.value =
                            _uiState.value.copy(
                                apiProfiles =
                                    profiles,
                                activeApiProfile =
                                    profiles
                                        .firstOrNull {
                                            it.isActive
                                        }
                            )
                    }
            }
    }

    private fun startSearchProfileObservation() {
        searchProfilesJob?.cancel()

        searchProfilesJob =
            viewModelScope.launch {
                searchProfileRepository
                    .observeAll()
                    .collect { profiles ->
                        _uiState.value =
                            _uiState.value.copy(
                                searchProfiles =
                                    profiles,
                                activeSearchProfile =
                                    profiles
                                        .firstOrNull {
                                            it.isActive
                                        }
                            )
                    }
            }
    }

    private fun startConversationObservation() {
        conversationsJob?.cancel()

        conversationsJob =
            viewModelScope.launch {
                conversationRepository
                    .observeConversations()
                    .collect {
                        conversations ->

                        var currentId =
                            _uiState.value
                                .currentConversationId

                        if (
                            currentId == null ||
                            conversations.none {
                                it.id == currentId
                            }
                        ) {
                            currentId =
                                conversations
                                    .firstOrNull()
                                    ?.id
                        }

                        if (
                            currentId == null
                        ) {
                            val created =
                                conversationRepository
                                    .createConversation()

                            currentId =
                                created.id

                            /*
                             * Room 会自动重新触发 collect。
                             */
                            return@collect
                        }

                        val current =
                            conversations
                                .firstOrNull {
                                    it.id ==
                                        currentId
                                }

                        val changed =
                            currentId !=
                                _uiState
                                    .value
                                    .currentConversationId

                        _uiState.value =
                            _uiState.value.copy(
                                conversations =
                                    conversations,
                                currentConversationId =
                                    currentId,
                                currentConversation =
                                    current,
                                initialized = true
                            )

                        if (
                            changed ||
                            messagesJob == null
                        ) {
                            observeMessages(
                                currentId
                            )
                        }
                    }
            }
    }

    private fun observeMessages(
        conversationId: String
    ) {
        messagesJob?.cancel()

        messagesJob =
            viewModelScope.launch {
                conversationRepository
                    .observeMessages(
                        conversationId
                    )
                    .collect { items ->
                        _uiState.value =
                            _uiState.value.copy(
                                messages =
                                    items.map {
                                        item ->
                                        MessageUiModel(
                                            message =
                                                item.message,
                                            images =
                                                item.images,
                                            citations =
                                                MessageUiModel
                                                    .parseCitations(
                                                        item
                                                            .message
                                                            .citationsJson
                                                    )
                                        )
                                    }
                            )
                    }
            }
    }

    fun setSelectedRoute(
        route: RequestRoute
    ) {
        _uiState.value =
            _uiState.value.copy(
                selectedRoute = route,
                routePreview = null
            )
    }

    fun cycleSearchForNextRequest() {
        val current =
            _uiState.value

        _uiState.value =
            current.copy(
                forceSearchForNextRequest =
                    !current
                        .forceSearchForNextRequest
            )
    }

    fun setForceSearchForNextRequest(
        enabled: Boolean
    ) {
        _uiState.value =
            _uiState.value.copy(
                forceSearchForNextRequest =
                    enabled
            )
    }

    fun previewRoute(
        prompt: String
    ) {
        viewModelScope.launch {
            val state = _uiState.value
            val conversationId =
                state.currentConversationId
                    ?: return@launch

            val latestImage =
                conversationRepository
                    .getLatestGeneratedImagePath(
                        conversationId
                    )

            val decision =
                intentRouter.decideRoute(
                    prompt = prompt,
                    manuallySelectedRoute =
                        state.selectedRoute,
                    attachedImagePath =
                        state.attachedImagePath,
                    explicitlyReferencedImagePath =
                        state.referencedImagePath,
                    latestGeneratedImagePath =
                        latestImage,
                    settings =
                        state.appSettings
                )

            _uiState.value =
                _uiState.value.copy(
                    routePreview =
                        decision
                )
        }
    }

    fun selectAttachment(
        uri: Uri
    ) {
        viewModelScope.launch {
            try {
                val file =
                    withContext(
                        Dispatchers.IO
                    ) {
                        ImageFileUtils
                            .copyAttachment(
                                getApplication(),
                                uri
                            )
                    }

                _uiState.value =
                    _uiState.value.copy(
                        attachedImagePath =
                            file.absolutePath,
                        referencedImagePath =
                            null,
                        referencedImageId =
                            null,
                        globalError = null
                    )
            } catch (exception: Exception) {
                showError(
                    exception.message
                        ?: "无法读取选择的图片"
                )
            }
        }
    }

    fun clearAttachment() {
        _uiState.value =
            _uiState.value.copy(
                attachedImagePath = null
            )
    }

    fun continueEditingImage(
        image: MessageImageEntity
    ) {
        if (!File(image.localPath).exists()) {
            showError(
                "该图片的本地文件已经不存在"
            )
            return
        }

        _uiState.value =
            _uiState.value.copy(
                referencedImagePath =
                    image.localPath,
                referencedImageId =
                    image.id,
                attachedImagePath = null,
                selectedRoute =
                    RequestRoute.IMAGE_EDIT,
                selectedImageForViewer =
                    null,
                globalError = null
            )
    }

    fun clearReferencedImage() {
        _uiState.value =
            _uiState.value.copy(
                referencedImagePath = null,
                referencedImageId = null
            )
    }

    fun send(
        prompt: String
    ) {
        val cleanPrompt =
            prompt.trim()

        if (
            cleanPrompt.isBlank() ||
            _uiState.value.loading
        ) {
            return
        }

        viewModelScope.launch {
            prepareSend(cleanPrompt)
        }
    }

    private suspend fun prepareSend(
        prompt: String
    ) {
        val state = _uiState.value
        val conversationId =
            state.currentConversationId
                ?: return

        val activeApi =
            apiProfileRepository
                .getResolvedActive()

        if (activeApi == null) {
            showError(
                "没有可用的 API 配置"
            )
            return
        }

        if (
            activeApi.profile.baseUrl
                .isBlank()
        ) {
            showError(
                "请先设置 API Base URL"
            )
            return
        }

        if (
            activeApi.apiKey.isBlank() &&
            !activeApi.profile
                .authenticationMode
                .equals(
                    "NONE",
                    ignoreCase = true
                )
        ) {
            showError(
                "请先填写 API Key"
            )
            return
        }

        val latestImage =
            conversationRepository
                .getLatestGeneratedImagePath(
                    conversationId
                )

        val decision =
            intentRouter.decideRoute(
                prompt = prompt,
                manuallySelectedRoute =
                    state.selectedRoute,
                attachedImagePath =
                    state.attachedImagePath,
                explicitlyReferencedImagePath =
                    state.referencedImagePath,
                latestGeneratedImagePath =
                    latestImage,
                settings =
                    state.appSettings
            )

        if (
            decision.route ==
            RequestRoute.IMAGE_EDIT &&
            decision.sourceImagePath
                .isNullOrBlank()
        ) {
            _uiState.value =
                state.copy(
                    pendingRouteConfirmation =
                        PendingRouteConfirmation(
                            prompt = prompt,
                            attachedImagePath =
                                state
                                    .attachedImagePath,
                            explicitlyReferencedImagePath =
                                state
                                    .referencedImagePath,
                            forceSearch =
                                state
                                    .forceSearchForNextRequest,
                            decision =
                                decision
                        ),
                    globalError =
                        "识别到图片编辑指令，但没有找到源图片。请上传图片，或点击上文图片的“继续编辑”。"
                )

            return
        }

        if (
            decision.requiresConfirmation &&
            state.appSettings
                .imageIntent
                .askWhenAmbiguous &&
            decision.route ==
            RequestRoute.IMAGE_EDIT &&
            state.referencedImagePath == null &&
            state.attachedImagePath == null
        ) {
            _uiState.value =
                state.copy(
                    pendingRouteConfirmation =
                        PendingRouteConfirmation(
                            prompt = prompt,
                            attachedImagePath =
                                state
                                    .attachedImagePath,
                            explicitlyReferencedImagePath =
                                state
                                    .referencedImagePath,
                            forceSearch =
                                state
                                    .forceSearchForNextRequest,
                            decision =
                                decision
                        )
                )

            return
        }

        val shouldOptimize =
            (
                decision.route ==
                    RequestRoute
                        .IMAGE_GENERATION ||
                    decision.route ==
                    RequestRoute
                        .IMAGE_EDIT
                ) &&
                promptOptimizer
                    .shouldOfferOptimization(
                        prompt,
                        state.appSettings
                    )

        if (shouldOptimize) {
            _uiState.value =
                state.copy(
                    pendingOptimization =
                        PendingPromptOptimization(
                            originalPrompt =
                                prompt,
                            attachedImagePath =
                                state
                                    .attachedImagePath,
                            explicitlyReferencedImagePath =
                                state
                                    .referencedImagePath,
                            forceSearch =
                                state
                                    .forceSearchForNextRequest,
                            routeDecision =
                                decision
                        )
                )

            return
        }

        executeRequest(
            prompt = prompt,
            originalPrompt = prompt,
            decision = decision,
            forceSearch =
                state
                    .forceSearchForNextRequest
        )
    }

    fun confirmRouteDecision() {
        val pending =
            _uiState.value
                .pendingRouteConfirmation
                ?: return

        _uiState.value =
            _uiState.value.copy(
                pendingRouteConfirmation =
                    null
            )

        viewModelScope.launch {
            executeRequest(
                prompt = pending.prompt,
                originalPrompt =
                    pending.prompt,
                decision =
                    pending.decision,
                forceSearch =
                    pending.forceSearch
            )
        }
    }

    fun cancelRouteConfirmation() {
        _uiState.value =
            _uiState.value.copy(
                pendingRouteConfirmation =
                    null
            )
    }

    fun generateWithoutOptimization() {
        val pending =
            _uiState.value
                .pendingOptimization
                ?: return

        _uiState.value =
            _uiState.value.copy(
                pendingOptimization = null
            )

        viewModelScope.launch {
            executeRequest(
                prompt =
                    pending.originalPrompt,
                originalPrompt =
                    pending.originalPrompt,
                decision =
                    pending.routeDecision,
                forceSearch =
                    pending.forceSearch
            )
        }
    }

    fun optimizePendingPrompt() {
        val pending =
            _uiState.value
                .pendingOptimization
                ?: return

        if (pending.optimizing) {
            return
        }

        viewModelScope.launch {
            val profile =
                apiProfileRepository
                    .getResolvedActive()

            if (profile == null) {
                showError(
                    "没有可用的 API 配置"
                )
                return@launch
            }

            _uiState.value =
                _uiState.value.copy(
                    pendingOptimization =
                        pending.copy(
                            optimizing = true,
                            error = null
                        )
                )

            val outcome =
                promptOptimizer.optimize(
                    profile = profile,
                    settings =
                        _uiState.value
                            .appSettings,
                    originalPrompt =
                        pending.originalPrompt
                )

            when (outcome) {
                is ApiOutcome.Failure -> {
                    _uiState.value =
                        _uiState.value.copy(
                            pendingOptimization =
                                pending.copy(
                                    optimizing = false,
                                    error =
                                        outcome
                                            .error
                                            .message
                                )
                        )
                }

                is ApiOutcome.Success -> {
                    _uiState.value =
                        _uiState.value.copy(
                            pendingOptimization =
                                pending.copy(
                                    optimizing = false,
                                    optimizedPrompt =
                                        outcome.value
                                )
                        )
                }
            }
        }
    }

    fun useOptimizedPrompt() {
        val pending =
            _uiState.value
                .pendingOptimization
                ?: return

        val optimized =
            pending.optimizedPrompt
                ?.trim()
                ?.takeIf {
                    it.isNotBlank()
                }
                ?: return

        _uiState.value =
            _uiState.value.copy(
                pendingOptimization = null
            )

        viewModelScope.launch {
            executeRequest(
                prompt = optimized,
                originalPrompt =
                    pending.originalPrompt,
                decision =
                    pending.routeDecision,
                forceSearch =
                    pending.forceSearch
            )
        }
    }

    fun cancelPromptOptimization() {
        _uiState.value =
            _uiState.value.copy(
                pendingOptimization = null
            )
    }

    private suspend fun executeRequest(
        prompt: String,
        originalPrompt: String,
        decision: RouteDecision,
        forceSearch: Boolean
    ) {
        val state = _uiState.value
        val conversationId =
            state.currentConversationId
                ?: return

        val apiProfile =
            apiProfileRepository
                .getResolvedActive()

        if (apiProfile == null) {
            showError(
                "没有可用的 API 配置"
            )
            return
        }

        val route = decision.route

        val model = when (route) {
            RequestRoute
                .IMAGE_GENERATION,
            RequestRoute.IMAGE_EDIT -> {
                state.appSettings
                    .imageParameters
                    .modelOverride
                    .ifBlank {
                        apiProfile
                            .profile
                            .imageModel
                    }
            }

            RequestRoute.VISION_CHAT -> {
                apiProfile
                    .profile
                    .visionModel
                    .ifBlank {
                        apiProfile
                            .profile
                            .chatModel
                    }
            }

            else -> {
                apiProfile
                    .profile
                    .chatModel
            }
        }

        if (model.isBlank()) {
            showError(
                if (
                    route ==
                    RequestRoute
                        .IMAGE_GENERATION ||
                    route ==
                    RequestRoute
                        .IMAGE_EDIT
                ) {
                    "请先填写图片模型名称"
                } else {
                    "请先填写聊天模型名称"
                }
            )

            return
        }

        val userMessage =
            conversationRepository
                .addUserMessage(
                    conversationId =
                        conversationId,
                    text = originalPrompt,
                    route = route,
                    attachedImagePath =
                        state.attachedImagePath,
                    referencedImagePath =
                        decision.sourceImagePath,
                    apiProfileId =
                        apiProfile.profile.id
                )

        conversationRepository
            .autoTitleIfNeeded(
                conversationId =
                    conversationId,
                firstUserText =
                    originalPrompt,
                maximumCharacters =
                    state.appSettings
                        .conversationTitleMaximumCharacters
            )

        val assistant =
            conversationRepository
                .addAssistantPlaceholder(
                    conversationId =
                        conversationId,
                    route = route,
                    model = model,
                    apiProfileId =
                        apiProfile.profile.id,
                    initialText =
                        if (
                            route ==
                            RequestRoute
                                .IMAGE_GENERATION
                        ) {
                            "正在生成图片……"
                        } else if (
                            route ==
                            RequestRoute
                                .IMAGE_EDIT
                        ) {
                            "正在编辑图片……"
                        } else {
                            ""
                        }
                )

        if (prompt != originalPrompt) {
            conversationRepository
                .setOptimizedPrompt(
                    userMessage.id,
                    prompt
                )
        }

        _uiState.value =
            _uiState.value.copy(
                loading = true,
                activeAssistantMessageId =
                    assistant.id,
                statusText =
                    when (route) {
                        RequestRoute
                            .IMAGE_GENERATION ->
                            "正在生成图片……"

                        RequestRoute
                            .IMAGE_EDIT ->
                            "正在编辑图片……"

                        else ->
                            "正在请求模型……"
                    },
                globalError = null,
                attachedImagePath = null,
                referencedImagePath = null,
                referencedImageId = null,
                forceSearchForNextRequest =
                    false,
                routePreview = null
            )

        requestJob?.cancel()

        requestJob =
            viewModelScope.launch {
                try {
                    when (route) {
                        RequestRoute
                            .IMAGE_GENERATION,
                        RequestRoute
                            .IMAGE_EDIT -> {
                            executeImageRequest(
                                assistant =
                                    assistant,
                                prompt = prompt,
                                originalPrompt =
                                    originalPrompt,
                                sourceImagePath =
                                    decision
                                        .sourceImagePath,
                                model = model,
                                apiProfile =
                                    apiProfile,
                                settings =
                                    state.appSettings
                            )
                        }

                        else -> {
                            executeChatRequest(
                                conversationId =
                                    conversationId,
                                assistant =
                                    assistant,
                                prompt = prompt,
                                forceSearch =
                                    forceSearch,
                                apiProfile =
                                    apiProfile,
                                settings =
                                    state.appSettings
                            )
                        }
                    }
                } catch (
                    exception:
                        CancellationException
                ) {
                    conversationRepository
                        .cancelMessage(
                            assistant.id
                        )

                    throw exception
                } catch (exception: Exception) {
                    conversationRepository
                        .failMessage(
                            assistant.id,
                            RequestError(
                                code =
                                    "UNEXPECTED_ERROR",
                                message =
                                    exception.message
                                        ?: "发生未知错误",
                                retryable = true
                            )
                        )
                } finally {
                    _uiState.value =
                        _uiState.value.copy(
                            loading = false,
                            activeAssistantMessageId =
                                null,
                            statusText = ""
                        )
                }
            }
    }

    private suspend fun executeChatRequest(
        conversationId: String,
        assistant: MessageEntity,
        prompt: String,
        forceSearch: Boolean,
        apiProfile:
            com.example.chatimage
                .data.repository
                .ResolvedApiProfile,
        settings: AppSettings
    ) {
        val databaseMessages =
            conversationRepository
                .getMessages(
                    conversationId
                )

        val messages =
            contextManager
                .buildChatMessages(
                    databaseMessages =
                        databaseMessages,
                    settings = settings,
                    currentUserText = prompt
                )

        val searchProfile =
            searchProfileRepository
                .getResolvedActive()

        var accumulatedText = ""

        val outcome =
            aiRequestRepository.answerChat(
                apiProfile =
                    apiProfile,
                searchProfile =
                    searchProfile,
                settings = settings,
                messages = messages,
                userPrompt = prompt,
                forceSearchForThisRequest =
                    forceSearch,
                onStatus = { status ->
                    _uiState.value =
                        _uiState.value.copy(
                            statusText =
                                status
                        )
                },
                onDelta = { fragment ->
                    accumulatedText += fragment

                    conversationRepository
                        .updateMessageText(
                            messageId =
                                assistant.id,
                            text =
                                accumulatedText,
                            status =
                                MessageStatus
                                    .RUNNING
                        )
                }
            )

        when (outcome) {
            is ApiOutcome.Failure -> {
                conversationRepository
                    .failMessage(
                        messageId =
                            assistant.id,
                        error =
                            outcome.error,
                        diagnostics =
                            outcome.diagnostics
                    )
            }

            is ApiOutcome.Success -> {
                val finalText =
                    outcome.value.content
                        .ifBlank {
                            accumulatedText
                        }

                conversationRepository
                    .completeTextMessage(
                        messageId =
                            assistant.id,
                        text = finalText,
                        diagnostics =
                            outcome.diagnostics,
                        citations =
                            outcome
                                .value
                                .citations,
                        searchQueries =
                            outcome
                                .value
                                .searchQueries
                    )
            }
        }
    }

    private suspend fun executeImageRequest(
        assistant: MessageEntity,
        prompt: String,
        originalPrompt: String,
        sourceImagePath: String?,
        model: String,
        apiProfile:
            com.example.chatimage
                .data.repository
                .ResolvedApiProfile,
        settings: AppSettings
    ) {
        val parsed =
            imageParameterParser.parse(
                prompt = prompt,
                settings = settings,
                model = model,
                sourceImagePath =
                    sourceImagePath
            )

        val options: ImageRequestOptions =
            parsed.first

        val outcome =
            aiRequestRepository
                .generateImage(
                    apiProfile =
                        apiProfile,
                    settings = settings,
                    options = options
                )

        when (outcome) {
            is ApiOutcome.Failure -> {
                conversationRepository
                    .failMessage(
                        messageId =
                            assistant.id,
                        error =
                            outcome.error,
                        diagnostics =
                            outcome.diagnostics
                    )
            }

            is ApiOutcome.Success -> {
                val result =
                    outcome.value

                conversationRepository
                    .completeImageMessage(
                        messageId =
                            assistant.id,
                        text =
                            if (
                                sourceImagePath ==
                                null
                            ) {
                                result
                                    .revisedPrompt
                                    ?.takeIf {
                                        it.isNotBlank()
                                    }
                                    ?.let {
                                        "图片生成完成\n\n优化后的提示词：$it"
                                    }
                                    ?: "图片生成完成"
                            } else {
                                "图片编辑完成"
                            },
                        images =
                            result.images,
                        prompt =
                            originalPrompt,
                        model = options.model,
                        size = options.size,
                        quality =
                            options.quality,
                        diagnostics =
                            outcome.diagnostics,
                        sourceType =
                            if (
                                sourceImagePath ==
                                null
                            ) {
                                "GENERATED"
                            } else {
                                "EDITED"
                            }
                    )
            }
        }
    }

    fun stopGeneration() {
        val assistantId =
            _uiState.value
                .activeAssistantMessageId

        requestJob?.cancel()
        requestJob = null

        if (assistantId != null) {
            viewModelScope.launch {
                conversationRepository
                    .cancelMessage(
                        assistantId
                    )
            }
        }

        _uiState.value =
            _uiState.value.copy(
                loading = false,
                activeAssistantMessageId =
                    null,
                statusText = ""
            )
    }

    fun newConversation() {
        viewModelScope.launch {
            stopGeneration()

            val created =
                conversationRepository
                    .createConversation(
                        apiProfileId =
                            _uiState
                                .value
                                .activeApiProfile
                                ?.id,
                        preferredRoute =
                            _uiState
                                .value
                                .selectedRoute,
                        webSearchMode =
                            _uiState
                                .value
                                .appSettings
                                .search
                                .mode
                                .name
                    )

            selectConversation(
                created.id
            )
        }
    }

    fun selectConversation(
        conversationId: String
    ) {
        stopGeneration()

        val conversation =
            _uiState.value
                .conversations
                .firstOrNull {
                    it.id ==
                        conversationId
                }

        _uiState.value =
            _uiState.value.copy(
                currentConversationId =
                    conversationId,
                currentConversation =
                    conversation,
                attachedImagePath = null,
                referencedImagePath = null,
                referencedImageId = null,
                globalError = null,
                showHistory = false
            )

        observeMessages(
            conversationId
        )
    }

    fun renameConversation(
        conversationId: String,
        title: String
    ) {
        viewModelScope.launch {
            conversationRepository
                .renameConversation(
                    conversationId,
                    title
                )
        }
    }

    fun togglePinned(
        conversationId: String
    ) {
        val conversation =
            _uiState.value
                .conversations
                .firstOrNull {
                    it.id ==
                        conversationId
                } ?: return

        viewModelScope.launch {
            conversationRepository
                .setPinned(
                    conversationId,
                    !conversation.pinned
                )
        }
    }

    fun deleteConversation(
        conversationId: String
    ) {
        viewModelScope.launch {
            val wasCurrent =
                _uiState.value
                    .currentConversationId ==
                    conversationId

            conversationRepository
                .deleteConversation(
                    conversationId =
                        conversationId,
                    deleteLocalImages =
                        false
                )

            if (wasCurrent) {
                val next =
                    _uiState.value
                        .conversations
                        .firstOrNull {
                            it.id !=
                                conversationId
                        }

                if (next != null) {
                    selectConversation(
                        next.id
                    )
                }
            }
        }
    }

    fun deleteMessage(
        messageId: String
    ) {
        viewModelScope.launch {
            conversationRepository
                .deleteMessage(
                    messageId =
                        messageId,
                    deleteLocalImages =
                        false
                )
        }
    }

    fun editUserMessage(
        messageId: String,
        newText: String
    ) {
        viewModelScope.launch {
            conversationRepository
                .updateUserMessageText(
                    messageId,
                    newText
                )
        }
    }

    fun retryAssistantMessage(
        assistantMessageId: String
    ) {
        viewModelScope.launch {
            val state = _uiState.value

            val assistantIndex =
                state.messages.indexOfFirst {
                    it.message.id ==
                        assistantMessageId
                }

            if (assistantIndex <= 0) {
                showError(
                    "找不到对应的用户消息"
                )
                return@launch
            }

            val userMessage =
                state.messages
                    .subList(
                        0,
                        assistantIndex
                    )
                    .asReversed()
                    .firstOrNull {
                        it.isUser
                    }
                    ?.message

            if (userMessage == null) {
                showError(
                    "找不到对应的用户消息"
                )
                return@launch
            }

            val route = runCatching {
                RequestRoute.valueOf(
                    userMessage.route
                        ?: "AUTO"
                )
            }.getOrDefault(
                RequestRoute.AUTO
            )

            conversationRepository
                .deleteMessage(
                    assistantMessageId,
                    deleteLocalImages =
                        false
                )

            _uiState.value =
                _uiState.value.copy(
                    selectedRoute = route,
                    attachedImagePath =
                        userMessage
                            .attachedImagePath,
                    referencedImagePath =
                        userMessage
                            .referencedImagePath
                )

            prepareSend(
                userMessage
                    .optimizedPrompt
                    ?: userMessage.text
            )
        }
    }

    fun regenerateImage(
        image: MessageImageEntity
    ) {
        val prompt =
            image.prompt
                ?.takeIf {
                    it.isNotBlank()
                }

        if (prompt == null) {
            showError(
                "该图片没有保存原始提示词"
            )
            return
        }

        _uiState.value =
            _uiState.value.copy(
                selectedRoute =
                    RequestRoute
                        .IMAGE_GENERATION
            )

        send(prompt)
    }

    fun setImageViewer(
        image: MessageImageEntity?
    ) {
        _uiState.value =
            _uiState.value.copy(
                selectedImageForViewer =
                    image
            )
    }

    fun updateAppSettings(
        settings: AppSettings
    ) {
        viewModelScope.launch {
            settingsStore.save(settings)
        }
    }

    fun resetAppSettings() {
        viewModelScope.launch {
            settingsStore.reset()
        }
    }

    fun activateApiProfile(
        profileId: String
    ) {
        viewModelScope.launch {
            apiProfileRepository
                .setActive(profileId)
        }
    }

    fun saveApiProfile(
        profile: ApiProfileEntity,
        newApiKey: String?
    ) {
        viewModelScope.launch {
            runCatching {
                apiProfileRepository
                    .upsert(
                        profile,
                        newApiKey
                    )
            }.onFailure {
                showError(
                    it.message
                        ?: "保存 API 配置失败"
                )
            }
        }
    }

    fun createApiProfile(
        name: String,
        baseUrl: String,
        apiKey: String,
        chatModel: String,
        imageModel: String
    ) {
        viewModelScope.launch {
            runCatching {
                apiProfileRepository
                    .create(
                        name = name,
                        baseUrl = baseUrl,
                        apiKey = apiKey,
                        chatModel =
                            chatModel,
                        imageModel =
                            imageModel,
                        activate =
                            _uiState
                                .value
                                .apiProfiles
                                .isEmpty()
                    )
            }.onFailure {
                showError(
                    it.message
                        ?: "创建 API 配置失败"
                )
            }
        }
    }

    fun deleteApiProfile(
        profileId: String
    ) {
        viewModelScope.launch {
            apiProfileRepository
                .delete(profileId)

            apiProfileRepository
                .ensureDefaultProfile()
        }
    }

    fun activateSearchProfile(
        profileId: String
    ) {
        viewModelScope.launch {
            searchProfileRepository
                .setActive(profileId)
        }
    }

    fun saveSearchProfile(
        profile: SearchProfileEntity,
        newApiKey: String?
    ) {
        viewModelScope.launch {
            runCatching {
                searchProfileRepository
                    .upsert(
                        profile,
                        newApiKey
                    )
            }.onFailure {
                showError(
                    it.message
                        ?: "保存搜索配置失败"
                )
            }
        }
    }

    fun createSearchProfile(
        name: String,
        baseUrl: String,
        path: String,
        apiKey: String
    ) {
        viewModelScope.launch {
            runCatching {
                searchProfileRepository
                    .create(
                        name = name,
                        baseUrl = baseUrl,
                        path = path,
                        apiKey = apiKey,
                        activate =
                            _uiState
                                .value
                                .searchProfiles
                                .isEmpty()
                    )
            }.onFailure {
                showError(
                    it.message
                        ?: "创建搜索配置失败"
                )
            }
        }
    }

    fun deleteSearchProfile(
        profileId: String
    ) {
        viewModelScope.launch {
            searchProfileRepository
                .delete(profileId)
        }
    }

    fun setHistorySearchQuery(
        query: String
    ) {
        _uiState.value =
            _uiState.value.copy(
                historySearchQuery =
                    query
            )
    }

    fun setShowHistory(
        show: Boolean
    ) {
        _uiState.value =
            _uiState.value.copy(
                showHistory = show
            )
    }

    fun setShowSettings(
        show: Boolean
    ) {
        _uiState.value =
            _uiState.value.copy(
                showSettings = show
            )
    }

    fun setShowImageParameters(
        show: Boolean
    ) {
        _uiState.value =
            _uiState.value.copy(
                showImageParameters =
                    show
            )
    }

    fun setShowApiProfiles(
        show: Boolean
    ) {
        _uiState.value =
            _uiState.value.copy(
                showApiProfiles = show
            )
    }

    fun setShowSearchProfiles(
        show: Boolean
    ) {
        _uiState.value =
            _uiState.value.copy(
                showSearchProfiles = show
            )
    }

    fun clearError() {
        _uiState.value =
            _uiState.value.copy(
                globalError = null
            )
    }

    fun showError(
        message: String
    ) {
        _uiState.value =
            _uiState.value.copy(
                globalError = message
            )
    }

    fun exportCurrentMarkdown(
        onResult: (Result<File>) -> Unit
    ) {
        val conversationId =
            _uiState.value
                .currentConversationId
                ?: return

        viewModelScope.launch {
            val result =
                exportUtils
                    .exportConversationMarkdown(
                        conversationId =
                            conversationId,
                        includeLocalImagePaths =
                            _uiState
                                .value
                                .appSettings
                                .includeImagesInExport
                    )

            onResult(result)
        }
    }

    fun exportCurrentJson(
        onResult: (Result<File>) -> Unit
    ) {
        val conversationId =
            _uiState.value
                .currentConversationId
                ?: return

        viewModelScope.launch {
            val result =
                exportUtils
                    .exportConversationJson(
                        conversationId =
                            conversationId,
                        includeLocalImagePaths =
                            _uiState
                                .value
                                .appSettings
                                .includeImagesInExport
                    )

            onResult(result)
        }
    }
}
