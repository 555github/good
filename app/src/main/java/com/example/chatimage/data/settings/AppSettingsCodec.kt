package com.example.chatimage.data.settings

import com.example.chatimage.data.model.AppSettings
import com.example.chatimage.data.model.AppearanceSettings
import com.example.chatimage.data.model.ChatParameterSettings
import com.example.chatimage.data.model.DiagnosticsSettings
import com.example.chatimage.data.model.ImageEditTransport
import com.example.chatimage.data.model.ImageIntentSettings
import com.example.chatimage.data.model.ImageParameterSettings
import com.example.chatimage.data.model.PromptOptimizationSettings
import com.example.chatimage.data.model.RetryMode
import com.example.chatimage.data.model.RetrySettings
import com.example.chatimage.data.model.SearchSettings
import com.example.chatimage.data.model.StreamProtocol
import com.example.chatimage.data.model.ThemeMode
import com.example.chatimage.data.model.TimeoutSettings
import com.example.chatimage.data.model.ToolCallMode
import com.example.chatimage.data.model.WebSearchMode
import com.example.chatimage.data.model.WebSearchProvider
import org.json.JSONArray
import org.json.JSONObject

object AppSettingsCodec {

    fun encode(
        settings: AppSettings
    ): String {
        return JSONObject()
            .put(
                "chatParameters",
                encodeChatParameters(
                    settings.chatParameters
                )
            )
            .put(
                "imageParameters",
                encodeImageParameters(
                    settings.imageParameters
                )
            )
            .put(
                "imageIntent",
                encodeImageIntent(
                    settings.imageIntent
                )
            )
            .put(
                "promptOptimization",
                encodePromptOptimization(
                    settings.promptOptimization
                )
            )
            .put(
                "search",
                encodeSearch(
                    settings.search
                )
            )
            .put(
                "timeouts",
                encodeTimeouts(
                    settings.timeouts
                )
            )
            .put(
                "retry",
                encodeRetry(
                    settings.retry
                )
            )
            .put(
                "diagnostics",
                encodeDiagnostics(
                    settings.diagnostics
                )
            )
            .put(
                "appearance",
                encodeAppearance(
                    settings.appearance
                )
            )
            .put(
                "automaticConversationSaving",
                settings.automaticConversationSaving
            )
            .put(
                "conversationTitleMaximumCharacters",
                settings.conversationTitleMaximumCharacters
            )
            .put(
                "includeImagesInExport",
                settings.includeImagesInExport
            )
            .put(
                "maximumImageCacheMegabytes",
                settings.maximumImageCacheMegabytes
            )
            .toString()
    }

    fun decode(
        value: String
    ): AppSettings {
        if (value.isBlank()) {
            return AppSettings()
        }

        return try {
            val root = JSONObject(value)
            val defaults = AppSettings()

            AppSettings(
                chatParameters = decodeChatParameters(
                    root.optJSONObject(
                        "chatParameters"
                    ),
                    defaults.chatParameters
                ),
                imageParameters = decodeImageParameters(
                    root.optJSONObject(
                        "imageParameters"
                    ),
                    defaults.imageParameters
                ),
                imageIntent = decodeImageIntent(
                    root.optJSONObject(
                        "imageIntent"
                    ),
                    defaults.imageIntent
                ),
                promptOptimization =
                    decodePromptOptimization(
                        root.optJSONObject(
                            "promptOptimization"
                        ),
                        defaults.promptOptimization
                    ),
                search = decodeSearch(
                    root.optJSONObject("search"),
                    defaults.search
                ),
                timeouts = decodeTimeouts(
                    root.optJSONObject("timeouts"),
                    defaults.timeouts
                ),
                retry = decodeRetry(
                    root.optJSONObject("retry"),
                    defaults.retry
                ),
                diagnostics = decodeDiagnostics(
                    root.optJSONObject(
                        "diagnostics"
                    ),
                    defaults.diagnostics
                ),
                appearance = decodeAppearance(
                    root.optJSONObject(
                        "appearance"
                    ),
                    defaults.appearance
                ),
                automaticConversationSaving =
                    root.optBoolean(
                        "automaticConversationSaving",
                        defaults.automaticConversationSaving
                    ),
                conversationTitleMaximumCharacters =
                    root.optInt(
                        "conversationTitleMaximumCharacters",
                        defaults
                            .conversationTitleMaximumCharacters
                    ),
                includeImagesInExport =
                    root.optBoolean(
                        "includeImagesInExport",
                        defaults.includeImagesInExport
                    ),
                maximumImageCacheMegabytes =
                    root.optInt(
                        "maximumImageCacheMegabytes",
                        defaults.maximumImageCacheMegabytes
                    )
            )
        } catch (_: Exception) {
            AppSettings()
        }
    }

    private fun encodeChatParameters(
        value: ChatParameterSettings
    ): JSONObject {
        return JSONObject()
            .put("systemPrompt", value.systemPrompt)
            .put(
                "streamEnabled",
                value.streamEnabled
            )
            .put(
                "streamProtocol",
                value.streamProtocol.name
            )
            .put(
                "sseDataPrefix",
                value.sseDataPrefix
            )
            .put(
                "sseDoneMarker",
                value.sseDoneMarker
            )
            .put(
                "streamTextPath",
                value.streamTextPath
            )
            .put(
                "nonStreamTextPath",
                value.nonStreamTextPath
            )
            .put(
                "temperatureEnabled",
                value.temperatureEnabled
            )
            .put(
                "temperature",
                value.temperature
            )
            .put(
                "topPEnabled",
                value.topPEnabled
            )
            .put("topP", value.topP)
            .put(
                "maxTokensEnabled",
                value.maxTokensEnabled
            )
            .put(
                "maxTokensFieldName",
                value.maxTokensFieldName
            )
            .put(
                "maxTokens",
                value.maxTokens
            )
            .put(
                "contextMessageLimit",
                value.contextMessageLimit
            )
            .put(
                "approximateContextTokenLimit",
                value.approximateContextTokenLimit
            )
            .put(
                "frequencyPenaltyEnabled",
                value.frequencyPenaltyEnabled
            )
            .put(
                "frequencyPenalty",
                value.frequencyPenalty
            )
            .put(
                "presencePenaltyEnabled",
                value.presencePenaltyEnabled
            )
            .put(
                "presencePenalty",
                value.presencePenalty
            )
            .put(
                "seedEnabled",
                value.seedEnabled
            )
            .put("seed", value.seed)
            .put(
                "stopEnabled",
                value.stopEnabled
            )
            .put(
                "stopSequences",
                stringListToJson(
                    value.stopSequences
                )
            )
            .put(
                "responseFormatMode",
                value.responseFormatMode
            )
            .put(
                "responseFormatJson",
                value.responseFormatJson
            )
            .put(
                "reasoningEnabled",
                value.reasoningEnabled
            )
            .put(
                "reasoningFieldPath",
                value.reasoningFieldPath
            )
            .put(
                "reasoningValue",
                value.reasoningValue
            )
            .put("requestUsage", value.requestUsage)
            .put(
                "extraRequestJson",
                value.extraRequestJson
            )
    }

    private fun decodeChatParameters(
        json: JSONObject?,
        defaults: ChatParameterSettings
    ): ChatParameterSettings {
        if (json == null) {
            return defaults
        }

        return ChatParameterSettings(
            systemPrompt = json.optString(
                "systemPrompt",
                defaults.systemPrompt
            ),
            streamEnabled = json.optBoolean(
                "streamEnabled",
                defaults.streamEnabled
            ),
            streamProtocol = enumValue(
                json.optString(
                    "streamProtocol",
                    defaults.streamProtocol.name
                ),
                defaults.streamProtocol
            ),
            sseDataPrefix = json.optString(
                "sseDataPrefix",
                defaults.sseDataPrefix
            ),
            sseDoneMarker = json.optString(
                "sseDoneMarker",
                defaults.sseDoneMarker
            ),
            streamTextPath = json.optString(
                "streamTextPath",
                defaults.streamTextPath
            ),
            nonStreamTextPath = json.optString(
                "nonStreamTextPath",
                defaults.nonStreamTextPath
            ),
            temperatureEnabled = json.optBoolean(
                "temperatureEnabled",
                defaults.temperatureEnabled
            ),
            temperature = json.optDouble(
                "temperature",
                defaults.temperature
            ),
            topPEnabled = json.optBoolean(
                "topPEnabled",
                defaults.topPEnabled
            ),
            topP = json.optDouble(
                "topP",
                defaults.topP
            ),
            maxTokensEnabled = json.optBoolean(
                "maxTokensEnabled",
                defaults.maxTokensEnabled
            ),
            maxTokensFieldName = json.optString(
                "maxTokensFieldName",
                defaults.maxTokensFieldName
            ),
            maxTokens = json.optInt(
                "maxTokens",
                defaults.maxTokens
            ),
            contextMessageLimit = json.optInt(
                "contextMessageLimit",
                defaults.contextMessageLimit
            ),
            approximateContextTokenLimit =
                json.optInt(
                    "approximateContextTokenLimit",
                    defaults
                        .approximateContextTokenLimit
                ),
            frequencyPenaltyEnabled =
                json.optBoolean(
                    "frequencyPenaltyEnabled",
                    defaults
                        .frequencyPenaltyEnabled
                ),
            frequencyPenalty = json.optDouble(
                "frequencyPenalty",
                defaults.frequencyPenalty
            ),
            presencePenaltyEnabled =
                json.optBoolean(
                    "presencePenaltyEnabled",
                    defaults
                        .presencePenaltyEnabled
                ),
            presencePenalty = json.optDouble(
                "presencePenalty",
                defaults.presencePenalty
            ),
            seedEnabled = json.optBoolean(
                "seedEnabled",
                defaults.seedEnabled
            ),
            seed = json.optLong(
                "seed",
                defaults.seed
            ),
            stopEnabled = json.optBoolean(
                "stopEnabled",
                defaults.stopEnabled
            ),
            stopSequences = jsonToStringList(
                json.optJSONArray(
                    "stopSequences"
                ),
                defaults.stopSequences
            ),
            responseFormatMode = json.optString(
                "responseFormatMode",
                defaults.responseFormatMode
            ),
            responseFormatJson = json.optString(
                "responseFormatJson",
                defaults.responseFormatJson
            ),
            reasoningEnabled = json.optBoolean(
                "reasoningEnabled",
                defaults.reasoningEnabled
            ),
            reasoningFieldPath = json.optString(
                "reasoningFieldPath",
                defaults.reasoningFieldPath
            ),
            reasoningValue = json.optString(
                "reasoningValue",
                defaults.reasoningValue
            ),
            requestUsage = json.optBoolean(
                "requestUsage",
                defaults.requestUsage
            ),
            extraRequestJson = json.optString(
                "extraRequestJson",
                defaults.extraRequestJson
            )
        )
    }

    private fun encodeImageParameters(
        value: ImageParameterSettings
    ): JSONObject {
        return JSONObject()
            .put(
                "modelOverride",
                value.modelOverride
            )
            .put(
                "sizeEnabled",
                value.sizeEnabled
            )
            .put(
                "sizeFieldName",
                value.sizeFieldName
            )
            .put("size", value.size)
            .put(
                "qualityEnabled",
                value.qualityEnabled
            )
            .put(
                "qualityFieldName",
                value.qualityFieldName
            )
            .put(
                "quality",
                value.quality
            )
            .put(
                "countEnabled",
                value.countEnabled
            )
            .put(
                "countFieldName",
                value.countFieldName
            )
            .put("count", value.count)
            .put(
                "responseFormatEnabled",
                value.responseFormatEnabled
            )
            .put(
                "responseFormatFieldName",
                value.responseFormatFieldName
            )
            .put(
                "responseFormat",
                value.responseFormat
            )
            .put(
                "modelFieldName",
                value.modelFieldName
            )
            .put(
                "promptFieldName",
                value.promptFieldName
            )
            .put(
                "imageFieldName",
                value.imageFieldName
            )
            .put(
                "imageEditTransport",
                value.imageEditTransport.name
            )
            .put(
                "includeDataUrlPrefix",
                value.includeDataUrlPrefix
            )
            .put(
                "extraGenerationJson",
                value.extraGenerationJson
            )
            .put(
                "extraEditJson",
                value.extraEditJson
            )
            .put(
                "imageArrayPath",
                value.imageArrayPath
            )
            .put(
                "imageUrlPaths",
                stringListToJson(
                    value.imageUrlPaths
                )
            )
            .put(
                "imageBase64Paths",
                stringListToJson(
                    value.imageBase64Paths
                )
            )
            .put(
                "relativeUrlBase",
                value.relativeUrlBase
            )
            .put(
                "downloadAuthenticationMode",
                value.downloadAuthenticationMode
            )
            .put(
                "generatedSizePresets",
                stringListToJson(
                    value.generatedSizePresets
                )
            )
            .put(
                "qualityPresets",
                stringListToJson(
                    value.qualityPresets
                )
            )
    }

    private fun decodeImageParameters(
        json: JSONObject?,
        defaults: ImageParameterSettings
    ): ImageParameterSettings {
        if (json == null) {
            return defaults
        }

        return ImageParameterSettings(
            modelOverride = json.optString(
                "modelOverride",
                defaults.modelOverride
            ),
            sizeEnabled = json.optBoolean(
                "sizeEnabled",
                defaults.sizeEnabled
            ),
            sizeFieldName = json.optString(
                "sizeFieldName",
                defaults.sizeFieldName
            ),
            size = json.optString(
                "size",
                defaults.size
            ),
            qualityEnabled = json.optBoolean(
                "qualityEnabled",
                defaults.qualityEnabled
            ),
            qualityFieldName = json.optString(
                "qualityFieldName",
                defaults.qualityFieldName
            ),
            quality = json.optString(
                "quality",
                defaults.quality
            ),
            countEnabled = json.optBoolean(
                "countEnabled",
                defaults.countEnabled
            ),
            countFieldName = json.optString(
                "countFieldName",
                defaults.countFieldName
            ),
            count = json.optInt(
                "count",
                defaults.count
            ),
            responseFormatEnabled =
                json.optBoolean(
                    "responseFormatEnabled",
                    defaults
                        .responseFormatEnabled
                ),
            responseFormatFieldName =
                json.optString(
                    "responseFormatFieldName",
                    defaults
                        .responseFormatFieldName
                ),
            responseFormat = json.optString(
                "responseFormat",
                defaults.responseFormat
            ),
            modelFieldName = json.optString(
                "modelFieldName",
                defaults.modelFieldName
            ),
            promptFieldName = json.optString(
                "promptFieldName",
                defaults.promptFieldName
            ),
            imageFieldName = json.optString(
                "imageFieldName",
                defaults.imageFieldName
            ),
            imageEditTransport = enumValue(
                json.optString(
                    "imageEditTransport",
                    defaults
                        .imageEditTransport
                        .name
                ),
                defaults.imageEditTransport
            ),
            includeDataUrlPrefix =
                json.optBoolean(
                    "includeDataUrlPrefix",
                    defaults.includeDataUrlPrefix
                ),
            extraGenerationJson =
                json.optString(
                    "extraGenerationJson",
                    defaults.extraGenerationJson
                ),
            extraEditJson = json.optString(
                "extraEditJson",
                defaults.extraEditJson
            ),
            imageArrayPath = json.optString(
                "imageArrayPath",
                defaults.imageArrayPath
            ),
            imageUrlPaths = jsonToStringList(
                json.optJSONArray(
                    "imageUrlPaths"
                ),
                defaults.imageUrlPaths
            ),
            imageBase64Paths =
                jsonToStringList(
                    json.optJSONArray(
                        "imageBase64Paths"
                    ),
                    defaults.imageBase64Paths
                ),
            relativeUrlBase = json.optString(
                "relativeUrlBase",
                defaults.relativeUrlBase
            ),
            downloadAuthenticationMode =
                json.optString(
                    "downloadAuthenticationMode",
                    defaults
                        .downloadAuthenticationMode
                ),
            generatedSizePresets =
                jsonToStringList(
                    json.optJSONArray(
                        "generatedSizePresets"
                    ),
                    defaults.generatedSizePresets
                ),
            qualityPresets = jsonToStringList(
                json.optJSONArray(
                    "qualityPresets"
                ),
                defaults.qualityPresets
            )
        )
    }

    private fun encodeImageIntent(
        value: ImageIntentSettings
    ): JSONObject {
        return JSONObject()
            .put("enabled", value.enabled)
            .put(
                "autoReferenceRecentImage",
                value.autoReferenceRecentImage
            )
            .put(
                "askWhenAmbiguous",
                value.askWhenAmbiguous
            )
            .put(
                "showSourceImageBeforeSend",
                value.showSourceImageBeforeSend
            )
            .put(
                "allowChatModeToImageEdit",
                value.allowChatModeToImageEdit
            )
            .put(
                "generationKeywords",
                stringListToJson(
                    value.generationKeywords
                )
            )
            .put(
                "editKeywords",
                stringListToJson(
                    value.editKeywords
                )
            )
            .put(
                "recentImageKeywords",
                stringListToJson(
                    value.recentImageKeywords
                )
            )
    }

    private fun decodeImageIntent(
        json: JSONObject?,
        defaults: ImageIntentSettings
    ): ImageIntentSettings {
        if (json == null) {
            return defaults
        }

        return ImageIntentSettings(
            enabled = json.optBoolean(
                "enabled",
                defaults.enabled
            ),
            autoReferenceRecentImage =
                json.optBoolean(
                    "autoReferenceRecentImage",
                    defaults
                        .autoReferenceRecentImage
                ),
            askWhenAmbiguous = json.optBoolean(
                "askWhenAmbiguous",
                defaults.askWhenAmbiguous
            ),
            showSourceImageBeforeSend =
                json.optBoolean(
                    "showSourceImageBeforeSend",
                    defaults
                        .showSourceImageBeforeSend
                ),
            allowChatModeToImageEdit =
                json.optBoolean(
                    "allowChatModeToImageEdit",
                    defaults
                        .allowChatModeToImageEdit
                ),
            generationKeywords =
                jsonToStringList(
                    json.optJSONArray(
                        "generationKeywords"
                    ),
                    defaults.generationKeywords
                ),
            editKeywords = jsonToStringList(
                json.optJSONArray(
                    "editKeywords"
                ),
                defaults.editKeywords
            ),
            recentImageKeywords =
                jsonToStringList(
                    json.optJSONArray(
                        "recentImageKeywords"
                    ),
                    defaults.recentImageKeywords
                )
        )
    }

    private fun encodePromptOptimization(
        value: PromptOptimizationSettings
    ): JSONObject {
        return JSONObject()
            .put("enabled", value.enabled)
            .put(
                "promptBeforeLongRequest",
                value.promptBeforeLongRequest
            )
            .put(
                "triggerCharacterCount",
                value.triggerCharacterCount
            )
            .put(
                "targetCharacterCount",
                value.targetCharacterCount
            )
            .put(
                "optimizerModelOverride",
                value.optimizerModelOverride
            )
            .put(
                "temperature",
                value.temperature
            )
            .put(
                "maxTokens",
                value.maxTokens
            )
            .put(
                "requireConfirmation",
                value.requireConfirmation
            )
            .put(
                "keepOriginalPrompt",
                value.keepOriginalPrompt
            )
            .put(
                "template",
                value.template
            )
    }

    private fun decodePromptOptimization(
        json: JSONObject?,
        defaults: PromptOptimizationSettings
    ): PromptOptimizationSettings {
        if (json == null) {
            return defaults
        }

        return PromptOptimizationSettings(
            enabled = json.optBoolean(
                "enabled",
                defaults.enabled
            ),
            promptBeforeLongRequest =
                json.optBoolean(
                    "promptBeforeLongRequest",
                    defaults
                        .promptBeforeLongRequest
                ),
            triggerCharacterCount =
                json.optInt(
                    "triggerCharacterCount",
                    defaults.triggerCharacterCount
                ),
            targetCharacterCount =
                json.optInt(
                    "targetCharacterCount",
                    defaults.targetCharacterCount
                ),
            optimizerModelOverride =
                json.optString(
                    "optimizerModelOverride",
                    defaults
                        .optimizerModelOverride
                ),
            temperature = json.optDouble(
                "temperature",
                defaults.temperature
            ),
            maxTokens = json.optInt(
                "maxTokens",
                defaults.maxTokens
            ),
            requireConfirmation =
                json.optBoolean(
                    "requireConfirmation",
                    defaults.requireConfirmation
                ),
            keepOriginalPrompt =
                json.optBoolean(
                    "keepOriginalPrompt",
                    defaults.keepOriginalPrompt
                ),
            template = json.optString(
                "template",
                defaults.template
            )
        )
    }

    private fun encodeSearch(
        value: SearchSettings
    ): JSONObject {
        return JSONObject()
            .put("mode", value.mode.name)
            .put("provider", value.provider.name)
            .put(
                "builtInToolType",
                value.builtInToolType
            )
            .put(
                "toolCallMode",
                value.toolCallMode.name
            )
            .put(
                "fallbackToInjectedSearch",
                value.fallbackToInjectedSearch
            )
            .put(
                "resultCount",
                value.resultCount
            )
            .put(
                "language",
                value.language
            )
            .put("region", value.region)
            .put(
                "searchKeywords",
                stringListToJson(
                    value.searchKeywords
                )
            )
            .put(
                "toolName",
                value.toolName
            )
            .put(
                "toolDescription",
                value.toolDescription
            )
            .put(
                "toolQueryParameterName",
                value.toolQueryParameterName
            )
            .put(
                "toolCountParameterName",
                value.toolCountParameterName
            )
            .put(
                "toolChoice",
                value.toolChoice
            )
            .put(
                "maximumToolRounds",
                value.maximumToolRounds
            )
            .put(
                "maximumCallsPerRound",
                value.maximumCallsPerRound
            )
            .put(
                "maximumQueryCharacters",
                value.maximumQueryCharacters
            )
            .put(
                "finalAnswerStreamEnabled",
                value.finalAnswerStreamEnabled
            )
            .put(
                "requireCitations",
                value.requireCitations
            )
            .put(
                "citationFormat",
                value.citationFormat
            )
            .put(
                "allowOfflineAnswerAfterFailure",
                value.allowOfflineAnswerAfterFailure
            )
            .put(
                "saveSearchResultsToHistory",
                value.saveSearchResultsToHistory
            )
            .put(
                "webContextTemplate",
                value.webContextTemplate
            )
    }

    private fun decodeSearch(
        json: JSONObject?,
        defaults: SearchSettings
    ): SearchSettings {
        if (json == null) {
            return defaults
        }

        return SearchSettings(
            mode = enumValue(
                json.optString(
                    "mode",
                    defaults.mode.name
                ),
                defaults.mode
            ),
            provider = enumValue(
                json.optString(
                    "provider",
                    defaults.provider.name
                ),
                defaults.provider
            ),
            builtInToolType = json.optString(
                "builtInToolType",
                defaults.builtInToolType
            ),
            toolCallMode = enumValue(
                json.optString(
                    "toolCallMode",
                    defaults.toolCallMode.name
                ),
                defaults.toolCallMode
            ),
            fallbackToInjectedSearch =
                json.optBoolean(
                    "fallbackToInjectedSearch",
                    defaults
                        .fallbackToInjectedSearch
                ),
            resultCount = json.optInt(
                "resultCount",
                defaults.resultCount
            ),
            language = json.optString(
                "language",
                defaults.language
            ),
            region = json.optString(
                "region",
                defaults.region
            ),
            searchKeywords =
                jsonToStringList(
                    json.optJSONArray(
                        "searchKeywords"
                    ),
                    defaults.searchKeywords
                ),
            toolName = json.optString(
                "toolName",
                defaults.toolName
            ),
            toolDescription = json.optString(
                "toolDescription",
                defaults.toolDescription
            ),
            toolQueryParameterName =
                json.optString(
                    "toolQueryParameterName",
                    defaults
                        .toolQueryParameterName
                ),
            toolCountParameterName =
                json.optString(
                    "toolCountParameterName",
                    defaults
                        .toolCountParameterName
                ),
            toolChoice = json.optString(
                "toolChoice",
                defaults.toolChoice
            ),
            maximumToolRounds = json.optInt(
                "maximumToolRounds",
                defaults.maximumToolRounds
            ),
            maximumCallsPerRound =
                json.optInt(
                    "maximumCallsPerRound",
                    defaults.maximumCallsPerRound
                ),
            maximumQueryCharacters =
                json.optInt(
                    "maximumQueryCharacters",
                    defaults
                        .maximumQueryCharacters
                ),
            finalAnswerStreamEnabled =
                json.optBoolean(
                    "finalAnswerStreamEnabled",
                    defaults
                        .finalAnswerStreamEnabled
                ),
            requireCitations =
                json.optBoolean(
                    "requireCitations",
                    defaults.requireCitations
                ),
            citationFormat = json.optString(
                "citationFormat",
                defaults.citationFormat
            ),
            allowOfflineAnswerAfterFailure =
                json.optBoolean(
                    "allowOfflineAnswerAfterFailure",
                    defaults
                        .allowOfflineAnswerAfterFailure
                ),
            saveSearchResultsToHistory =
                json.optBoolean(
                    "saveSearchResultsToHistory",
                    defaults
                        .saveSearchResultsToHistory
                ),
            webContextTemplate =
                json.optString(
                    "webContextTemplate",
                    defaults.webContextTemplate
                )
        )
    }

    private fun encodeTimeouts(
        value: TimeoutSettings
    ): JSONObject {
        return JSONObject()
            .put(
                "chatConnectSeconds",
                value.chatConnectSeconds
            )
            .put(
                "chatReadSeconds",
                value.chatReadSeconds
            )
            .put(
                "chatWriteSeconds",
                value.chatWriteSeconds
            )
            .put(
                "chatCallSeconds",
                value.chatCallSeconds
            )
            .put(
                "imageConnectSeconds",
                value.imageConnectSeconds
            )
            .put(
                "imageReadSeconds",
                value.imageReadSeconds
            )
            .put(
                "imageWriteSeconds",
                value.imageWriteSeconds
            )
            .put(
                "imageCallSeconds",
                value.imageCallSeconds
            )
            .put(
                "imageDownloadSeconds",
                value.imageDownloadSeconds
            )
            .put(
                "searchConnectSeconds",
                value.searchConnectSeconds
            )
            .put(
                "searchReadSeconds",
                value.searchReadSeconds
            )
            .put(
                "searchCallSeconds",
                value.searchCallSeconds
            )
            .put(
                "toolDecisionSeconds",
                value.toolDecisionSeconds
            )
    }

    private fun decodeTimeouts(
        json: JSONObject?,
        defaults: TimeoutSettings
    ): TimeoutSettings {
        if (json == null) {
            return defaults
        }

        return TimeoutSettings(
            chatConnectSeconds =
                json.optLong(
                    "chatConnectSeconds",
                    defaults.chatConnectSeconds
                ),
            chatReadSeconds =
                json.optLong(
                    "chatReadSeconds",
                    defaults.chatReadSeconds
                ),
            chatWriteSeconds =
                json.optLong(
                    "chatWriteSeconds",
                    defaults.chatWriteSeconds
                ),
            chatCallSeconds =
                json.optLong(
                    "chatCallSeconds",
                    defaults.chatCallSeconds
                ),
            imageConnectSeconds =
                json.optLong(
                    "imageConnectSeconds",
                    defaults.imageConnectSeconds
                ),
            imageReadSeconds =
                json.optLong(
                    "imageReadSeconds",
                    defaults.imageReadSeconds
                ),
            imageWriteSeconds =
                json.optLong(
                    "imageWriteSeconds",
                    defaults.imageWriteSeconds
                ),
            imageCallSeconds =
                json.optLong(
                    "imageCallSeconds",
                    defaults.imageCallSeconds
                ),
            imageDownloadSeconds =
                json.optLong(
                    "imageDownloadSeconds",
                    defaults.imageDownloadSeconds
                ),
            searchConnectSeconds =
                json.optLong(
                    "searchConnectSeconds",
                    defaults.searchConnectSeconds
                ),
            searchReadSeconds =
                json.optLong(
                    "searchReadSeconds",
                    defaults.searchReadSeconds
                ),
            searchCallSeconds =
                json.optLong(
                    "searchCallSeconds",
                    defaults.searchCallSeconds
                ),
            toolDecisionSeconds =
                json.optLong(
                    "toolDecisionSeconds",
                    defaults.toolDecisionSeconds
                )
        )
    }

    private fun encodeRetry(
        value: RetrySettings
    ): JSONObject {
        return JSONObject()
            .put("mode", value.mode.name)
            .put(
                "maximumAttempts",
                value.maximumAttempts
            )
            .put(
                "initialDelaySeconds",
                value.initialDelaySeconds
            )
            .put(
                "exponentialBackoff",
                value.exponentialBackoff
            )
            .put(
                "retryStatusCodes",
                intSetToJson(
                    value.retryStatusCodes
                )
            )
            .put(
                "retryNetworkErrors",
                value.retryNetworkErrors
            )
            .put(
                "retryImageRequestsAutomatically",
                value
                    .retryImageRequestsAutomatically
            )
    }

    private fun decodeRetry(
        json: JSONObject?,
        defaults: RetrySettings
    ): RetrySettings {
        if (json == null) {
            return defaults
        }

        return RetrySettings(
            mode = enumValue(
                json.optString(
                    "mode",
                    defaults.mode.name
                ),
                defaults.mode
            ),
            maximumAttempts = json.optInt(
                "maximumAttempts",
                defaults.maximumAttempts
            ),
            initialDelaySeconds =
                json.optLong(
                    "initialDelaySeconds",
                    defaults.initialDelaySeconds
                ),
            exponentialBackoff =
                json.optBoolean(
                    "exponentialBackoff",
                    defaults.exponentialBackoff
                ),
            retryStatusCodes =
                jsonToIntSet(
                    json.optJSONArray(
                        "retryStatusCodes"
                    ),
                    defaults.retryStatusCodes
                ),
            retryNetworkErrors =
                json.optBoolean(
                    "retryNetworkErrors",
                    defaults.retryNetworkErrors
                ),
            retryImageRequestsAutomatically =
                json.optBoolean(
                    "retryImageRequestsAutomatically",
                    defaults
                        .retryImageRequestsAutomatically
                )
        )
    }

    private fun encodeDiagnostics(
        value: DiagnosticsSettings
    ): JSONObject {
        return JSONObject()
            .put(
                "detailedErrors",
                value.detailedErrors
            )
            .put(
                "showDuration",
                value.showDuration
            )
            .put(
                "showHttpStatus",
                value.showHttpStatus
            )
            .put(
                "showContentType",
                value.showContentType
            )
            .put(
                "showServerName",
                value.showServerName
            )
            .put(
                "showRequestId",
                value.showRequestId
            )
            .put(
                "showEndpointPath",
                value.showEndpointPath
            )
            .put(
                "showModel",
                value.showModel
            )
            .put(
                "saveDiagnosticHistory",
                value.saveDiagnosticHistory
            )
            .put(
                "diagnosticRetentionDays",
                value.diagnosticRetentionDays
            )
            .put(
                "requestIdHeaderNames",
                stringListToJson(
                    value.requestIdHeaderNames
                )
            )
    }

    private fun decodeDiagnostics(
        json: JSONObject?,
        defaults: DiagnosticsSettings
    ): DiagnosticsSettings {
        if (json == null) {
            return defaults
        }

        return DiagnosticsSettings(
            detailedErrors =
                json.optBoolean(
                    "detailedErrors",
                    defaults.detailedErrors
                ),
            showDuration =
                json.optBoolean(
                    "showDuration",
                    defaults.showDuration
                ),
            showHttpStatus =
                json.optBoolean(
                    "showHttpStatus",
                    defaults.showHttpStatus
                ),
            showContentType =
                json.optBoolean(
                    "showContentType",
                    defaults.showContentType
                ),
            showServerName =
                json.optBoolean(
                    "showServerName",
                    defaults.showServerName
                ),
            showRequestId =
                json.optBoolean(
                    "showRequestId",
                    defaults.showRequestId
                ),
            showEndpointPath =
                json.optBoolean(
                    "showEndpointPath",
                    defaults.showEndpointPath
                ),
            showModel =
                json.optBoolean(
                    "showModel",
                    defaults.showModel
                ),
            saveDiagnosticHistory =
                json.optBoolean(
                    "saveDiagnosticHistory",
                    defaults.saveDiagnosticHistory
                ),
            diagnosticRetentionDays =
                json.optInt(
                    "diagnosticRetentionDays",
                    defaults
                        .diagnosticRetentionDays
                ),
            requestIdHeaderNames =
                jsonToStringList(
                    json.optJSONArray(
                        "requestIdHeaderNames"
                    ),
                    defaults.requestIdHeaderNames
                )
        )
    }

    private fun encodeAppearance(
        value: AppearanceSettings
    ): JSONObject {
        return JSONObject()
            .put(
                "themeMode",
                value.themeMode.name
            )
            .put(
                "fontScale",
                value.fontScale.toDouble()
            )
            .put(
                "messageSpacingDp",
                value.messageSpacingDp
            )
            .put(
                "messagePaddingDp",
                value.messagePaddingDp
            )
            .put(
                "messageWidthFraction",
                value.messageWidthFraction
                    .toDouble()
            )
            .put(
                "imagePreviewHeightDp",
                value.imagePreviewHeightDp
            )
            .put(
                "autoScroll",
                value.autoScroll
            )
            .put(
                "dismissKeyboardOnSend",
                value.dismissKeyboardOnSend
            )
            .put(
                "showModelInTopBar",
                value.showModelInTopBar
            )
            .put(
                "showProfileInTopBar",
                value.showProfileInTopBar
            )
            .put(
                "showCopyButton",
                value.showCopyButton
            )
            .put(
                "showShareButton",
                value.showShareButton
            )
            .put(
                "showDeleteButton",
                value.showDeleteButton
            )
            .put(
                "showRegenerateButton",
                value.showRegenerateButton
            )
    }

    private fun decodeAppearance(
        json: JSONObject?,
        defaults: AppearanceSettings
    ): AppearanceSettings {
        if (json == null) {
            return defaults
        }

        return AppearanceSettings(
            themeMode = enumValue(
                json.optString(
                    "themeMode",
                    defaults.themeMode.name
                ),
                defaults.themeMode
            ),
            fontScale = json.optDouble(
                "fontScale",
                defaults.fontScale.toDouble()
            ).toFloat(),
            messageSpacingDp = json.optInt(
                "messageSpacingDp",
                defaults.messageSpacingDp
            ),
            messagePaddingDp = json.optInt(
                "messagePaddingDp",
                defaults.messagePaddingDp
            ),
            messageWidthFraction =
                json.optDouble(
                    "messageWidthFraction",
                    defaults
                        .messageWidthFraction
                        .toDouble()
                ).toFloat(),
            imagePreviewHeightDp =
                json.optInt(
                    "imagePreviewHeightDp",
                    defaults.imagePreviewHeightDp
                ),
            autoScroll =
                json.optBoolean(
                    "autoScroll",
                    defaults.autoScroll
                ),
            dismissKeyboardOnSend =
                json.optBoolean(
                    "dismissKeyboardOnSend",
                    defaults.dismissKeyboardOnSend
                ),
            showModelInTopBar =
                json.optBoolean(
                    "showModelInTopBar",
                    defaults.showModelInTopBar
                ),
            showProfileInTopBar =
                json.optBoolean(
                    "showProfileInTopBar",
                    defaults.showProfileInTopBar
                ),
            showCopyButton =
                json.optBoolean(
                    "showCopyButton",
                    defaults.showCopyButton
                ),
            showShareButton =
                json.optBoolean(
                    "showShareButton",
                    defaults.showShareButton
                ),
            showDeleteButton =
                json.optBoolean(
                    "showDeleteButton",
                    defaults.showDeleteButton
                ),
            showRegenerateButton =
                json.optBoolean(
                    "showRegenerateButton",
                    defaults.showRegenerateButton
                )
        )
    }

    private fun stringListToJson(
        values: List<String>
    ): JSONArray {
        return JSONArray().apply {
            values.forEach {
                put(it)
            }
        }
    }

    private fun intSetToJson(
        values: Set<Int>
    ): JSONArray {
        return JSONArray().apply {
            values.sorted().forEach {
                put(it)
            }
        }
    }

    private fun jsonToStringList(
        array: JSONArray?,
        defaults: List<String>
    ): List<String> {
        if (array == null) {
            return defaults
        }

        return buildList {
            for (index in 0 until array.length()) {
                val value = array
                    .optString(index)
                    .trim()

                if (value.isNotEmpty()) {
                    add(value)
                }
            }
        }
    }

    private fun jsonToIntSet(
        array: JSONArray?,
        defaults: Set<Int>
    ): Set<Int> {
        if (array == null) {
            return defaults
        }

        return buildSet {
            for (index in 0 until array.length()) {
                val value = array.optInt(
                    index,
                    Int.MIN_VALUE
                )

                if (value != Int.MIN_VALUE) {
                    add(value)
                }
            }
        }
    }

    private inline fun <
        reified T : Enum<T>
        > enumValue(
        raw: String,
        default: T
    ): T {
        return enumValues<T>()
            .firstOrNull {
                it.name.equals(
                    raw,
                    ignoreCase = true
                )
            }
            ?: default
    }
}
