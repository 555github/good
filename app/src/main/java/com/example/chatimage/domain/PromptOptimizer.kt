package com.example.chatimage.domain

import com.example.chatimage.data.api.ApiOutcome
import com.example.chatimage.data.api.ChatApiClient
import com.example.chatimage.data.api.ChatWireMessage
import com.example.chatimage.data.api.ResponsesApiClient
import com.example.chatimage.data.model.AppSettings
import com.example.chatimage.data.repository.ResolvedApiProfile

class PromptOptimizer(
    private val chatApiClient: ChatApiClient,
    private val responsesApiClient: ResponsesApiClient
) {

    fun shouldOfferOptimization(
        prompt: String,
        settings: AppSettings
    ): Boolean {
        val optimization =
            settings.promptOptimization

        if (!optimization.enabled) {
            return false
        }

        if (
            !optimization
                .promptBeforeLongRequest
        ) {
            return false
        }

        return prompt.length >=
            optimization
                .triggerCharacterCount
                .coerceAtLeast(1)
    }

    suspend fun optimize(
        profile: ResolvedApiProfile,
        settings: AppSettings,
        originalPrompt: String
    ): ApiOutcome<String> {
        val optimization =
            settings.promptOptimization

        val optimizerModel =
            optimization
                .optimizerModelOverride
                .ifBlank {
                    profile
                        .profile
                        .optimizerModel
                }
                .ifBlank {
                    profile
                        .profile
                        .chatModel
                }

        val optimizerProfile =
            profile.copy(
                profile =
                    profile.profile.copy(
                        chatModel =
                            optimizerModel
                    )
            )

        val optimizerChatSettings =
            settings.chatParameters.copy(
                systemPrompt = "",
                streamEnabled = false,
                temperatureEnabled = true,
                temperature =
                    optimization.temperature,
                maxTokensEnabled = true,
                maxTokens =
                    optimization.maxTokens,
                responseFormatMode = "NONE"
            )

        val optimizerSettings =
            settings.copy(
                chatParameters =
                    optimizerChatSettings
            )

        val instruction =
            buildString {
                appendLine(
                    optimization.template
                )
                appendLine()
                appendLine(
                    "目标长度：不超过约 " +
                        optimization
                            .targetCharacterCount +
                        " 个汉字。"
                )
                appendLine()
                appendLine("原始提示词：")
                append(originalPrompt)
            }

        val outcome = if (
            optimizerProfile.profile.chatPath
                .trimEnd('/')
                .endsWith("/responses", ignoreCase = true)
        ) {
            responsesApiClient.complete(
                resolvedProfile = optimizerProfile,
                settings = optimizerSettings,
                messages = listOf(
                    ChatWireMessage(role = "user", content = instruction)
                )
            )
        } else {
            chatApiClient.complete(
                resolvedProfile =
                    optimizerProfile,
                appSettings =
                    optimizerSettings,
                messages = listOf(
                    ChatWireMessage(
                        role = "user",
                        content = instruction
                    )
                ),
                streamOverride = false
            )
        }

        return when (outcome) {
            is ApiOutcome.Failure -> {
                outcome
            }

            is ApiOutcome.Success -> {
                val optimized =
                    outcome.value.content
                        .trim()
                        .removeSurrounding(
                            "\""
                        )
                        .ifBlank {
                            originalPrompt
                        }

                ApiOutcome.Success(
                    value = optimized,
                    diagnostics =
                        outcome.diagnostics
                )
            }
        }
    }
}
