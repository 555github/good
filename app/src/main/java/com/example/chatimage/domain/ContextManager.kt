package com.example.chatimage.domain

import com.example.chatimage.data.api.ChatWireMessage
import com.example.chatimage.data.api.ChatContentPart
import com.example.chatimage.data.database.MessageWithImages
import com.example.chatimage.data.model.AppSettings
import com.example.chatimage.data.model.MessageStatus

class ContextManager {

    fun buildChatMessages(
        databaseMessages:
            List<MessageWithImages>,
        settings: AppSettings,
        currentUserText: String? = null,
        currentUserImageDataUrl: String? = null
    ): List<ChatWireMessage> {
        val chatSettings =
            settings.chatParameters

        val validMessages =
            databaseMessages
                .map {
                    it.message
                }
                .filter {
                    val validRole =
                        it.role.equals(
                            "user",
                            ignoreCase = true
                        ) ||
                            it.role.equals(
                                "assistant",
                                ignoreCase = true
                            ) ||
                            it.role.equals(
                                "system",
                                ignoreCase = true
                            )

                    val validStatus =
                        it.status ==
                            MessageStatus
                                .COMPLETED
                                .name ||
                            it.status ==
                            MessageStatus
                                .CANCELLED
                                .name

                    validRole &&
                        validStatus &&
                        it.text.isNotBlank()
                }
                .map {
                    ChatWireMessage(
                        role =
                            it.role.lowercase(),
                        content = it.text
                    )
                }
                .toMutableList()

        if (!currentUserText.isNullOrBlank()) {
            val last = validMessages
                .lastOrNull()

            val alreadyPresent =
                last?.role == "user" &&
                    last.content ==
                    currentUserText

            if (!alreadyPresent) {
                validMessages +=
                    ChatWireMessage(
                        role = "user",
                        content =
                            currentUserText
                    )
            }
        }

        if (!currentUserImageDataUrl.isNullOrBlank()) {
            val currentUserIndex = validMessages
                .indexOfLast {
                    it.role == "user" &&
                        it.content == currentUserText
                }

            if (currentUserIndex >= 0) {
                val currentUserMessage =
                    validMessages[currentUserIndex]

                validMessages[currentUserIndex] =
                    currentUserMessage.copy(
                        contentParts = listOf(
                            ChatContentPart.Text(
                                currentUserMessage
                                    .content
                                    .orEmpty()
                            ),
                            ChatContentPart.ImageUrl(
                                currentUserImageDataUrl
                            )
                        )
                    )
            }
        }

        val limitedByCount =
            validMessages.takeLast(
                chatSettings
                    .contextMessageLimit
                    .coerceIn(1, 1000)
            )

        val limitedByTokens =
            applyApproximateTokenLimit(
                messages = limitedByCount,
                tokenLimit =
                    chatSettings
                        .approximateContextTokenLimit
            )

        val result =
            mutableListOf<ChatWireMessage>()

        if (
            chatSettings.systemPrompt
                .isNotBlank()
        ) {
            result += ChatWireMessage(
                role = "system",
                content =
                    chatSettings.systemPrompt
            )
        }

        result += limitedByTokens

        return result
    }

    fun estimateTokens(
        text: String
    ): Int {
        if (text.isBlank()) {
            return 0
        }

        var approximateTokens = 0.0

        text.forEach { character ->
            approximateTokens += when {
                isCjk(character) -> 1.0

                character.isWhitespace() -> 0.1

                character.isLetterOrDigit() -> 0.25

                else -> 0.5
            }
        }

        return approximateTokens
            .toInt()
            .coerceAtLeast(1)
    }

    fun estimateMessagesTokens(
        messages: List<ChatWireMessage>
    ): Int {
        return messages.sumOf {
            estimateMessageTokens(it)
        }
    }

    private fun applyApproximateTokenLimit(
        messages: List<ChatWireMessage>,
        tokenLimit: Int
    ): List<ChatWireMessage> {
        if (tokenLimit <= 0) {
            return messages
        }

        val selected =
            mutableListOf<ChatWireMessage>()

        var usedTokens = 0

        for (message in messages.asReversed()) {
            val messageTokens =
                estimateMessageTokens(message)

            if (
                selected.isNotEmpty() &&
                usedTokens + messageTokens >
                tokenLimit
            ) {
                break
            }

            /*
             * 当前最后一条用户消息即使超过估算限制也会保留，
             * 否则会出现请求中缺少本次问题。
             */
            selected += message
            usedTokens += messageTokens
        }

        return selected
            .asReversed()
    }

    private fun estimateMessageTokens(
        message: ChatWireMessage
    ): Int {
        val imageTokens = message.contentParts
            .count {
                it is ChatContentPart.ImageUrl
            } * APPROXIMATE_IMAGE_TOKENS

        return estimateTokens(
            message.content.orEmpty()
        ) + imageTokens + 4
    }

    private fun isCjk(
        character: Char
    ): Boolean {
        val block =
            Character.UnicodeBlock.of(
                character
            )

        return block ==
            Character.UnicodeBlock
                .CJK_UNIFIED_IDEOGRAPHS ||
            block ==
            Character.UnicodeBlock
                .CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A ||
            block ==
            Character.UnicodeBlock
                .CJK_COMPATIBILITY_IDEOGRAPHS ||
            block ==
            Character.UnicodeBlock.HIRAGANA ||
            block ==
            Character.UnicodeBlock.KATAKANA ||
            block ==
            Character.UnicodeBlock.HANGUL_SYLLABLES
    }

    private companion object {
        const val APPROXIMATE_IMAGE_TOKENS = 1024
    }
}
