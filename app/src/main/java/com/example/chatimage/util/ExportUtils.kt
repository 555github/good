package com.example.chatimage.util

import android.content.Context
import com.example.chatimage.data.database.ConversationEntity
import com.example.chatimage.data.database.MessageWithImages
import com.example.chatimage.data.repository.ConversationRepository
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

class ExportUtils(
    private val context: Context,
    private val conversationRepository:
        ConversationRepository
) {

    suspend fun exportConversationMarkdown(
        conversationId: String,
        includeLocalImagePaths: Boolean =
            false
    ): Result<File> {
        return runCatching {
            val conversation =
                conversationRepository
                    .getConversation(
                        conversationId
                    )
                    ?: throw
                        IllegalStateException(
                            "对话不存在"
                        )

            val messages =
                conversationRepository
                    .getMessages(
                        conversationId
                    )

            val content =
                buildMarkdown(
                    conversation =
                        conversation,
                    messages = messages,
                    includeLocalImagePaths =
                        includeLocalImagePaths
                )

            val directory = File(
                context.filesDir,
                "exports"
            ).apply {
                mkdirs()
            }

            val file = File(
                directory,
                safeFileName(
                    conversation.title
                ) +
                    "_" +
                    System.currentTimeMillis() +
                    ".md"
            )

            file.writeText(
                content,
                Charsets.UTF_8
            )

            file
        }
    }

    suspend fun exportConversationJson(
        conversationId: String,
        includeLocalImagePaths: Boolean =
            false
    ): Result<File> {
        return runCatching {
            val conversation =
                conversationRepository
                    .getConversation(
                        conversationId
                    )
                    ?: throw
                        IllegalStateException(
                            "对话不存在"
                        )

            val messages =
                conversationRepository
                    .getMessages(
                        conversationId
                    )

            val root = JSONObject()
                .put(
                    "format",
                    "chatimage-conversation"
                )
                .put("version", 1)
                .put(
                    "exportedAt",
                    System.currentTimeMillis()
                )
                .put(
                    "conversation",
                    conversationToJson(
                        conversation
                    )
                )
                .put(
                    "messages",
                    messagesToJson(
                        messages,
                        includeLocalImagePaths
                    )
                )

            val directory = File(
                context.filesDir,
                "exports"
            ).apply {
                mkdirs()
            }

            val file = File(
                directory,
                safeFileName(
                    conversation.title
                ) +
                    "_" +
                    System.currentTimeMillis() +
                    ".json"
            )

            file.writeText(
                root.toString(2),
                Charsets.UTF_8
            )

            file
        }
    }

    suspend fun exportAllConversationsJson(
        conversations:
            List<ConversationEntity>,
        includeLocalImagePaths: Boolean =
            false
    ): Result<File> {
        return runCatching {
            val conversationArray =
                JSONArray()

            conversations.forEach {
                conversation ->
                val messages =
                    conversationRepository
                        .getMessages(
                            conversation.id
                        )

                conversationArray.put(
                    JSONObject()
                        .put(
                            "conversation",
                            conversationToJson(
                                conversation
                            )
                        )
                        .put(
                            "messages",
                            messagesToJson(
                                messages,
                                includeLocalImagePaths
                            )
                        )
                )
            }

            val root = JSONObject()
                .put(
                    "format",
                    "chatimage-backup"
                )
                .put("version", 1)
                .put(
                    "exportedAt",
                    System.currentTimeMillis()
                )
                .put(
                    "conversations",
                    conversationArray
                )

            val directory = File(
                context.filesDir,
                "exports"
            ).apply {
                mkdirs()
            }

            val file = File(
                directory,
                "ChatImage_Backup_" +
                    System.currentTimeMillis() +
                    ".json"
            )

            file.writeText(
                root.toString(2),
                Charsets.UTF_8
            )

            file
        }
    }

    private fun buildMarkdown(
        conversation:
            ConversationEntity,
        messages:
            List<MessageWithImages>,
        includeLocalImagePaths: Boolean
    ): String {
        return buildString {
            appendLine(
                "# ${conversation.title}"
            )
            appendLine()

            appendLine(
                "- 创建时间：" +
                    conversation.createdAt
            )

            appendLine(
                "- 更新时间：" +
                    conversation.updatedAt
            )

            appendLine()

            messages.forEach { item ->
                val roleTitle =
                    when (
                        item.message
                            .role
                            .lowercase()
                    ) {
                        "user" -> "用户"
                        "assistant" -> "助手"
                        "system" -> "系统"
                        "tool" -> "工具"
                        else ->
                            item.message.role
                    }

                appendLine(
                    "## $roleTitle"
                )
                appendLine()

                if (
                    item.message.text
                        .isNotBlank()
                ) {
                    appendLine(
                        item.message.text
                    )
                    appendLine()
                }

                item.images.forEachIndexed {
                    index,
                    image ->

                    appendLine(
                        "图片 ${index + 1}"
                    )

                    if (
                        includeLocalImagePaths
                    ) {
                        appendLine(
                            "`${image.localPath}`"
                        )
                    }

                    if (
                        !image.prompt
                            .isNullOrBlank()
                    ) {
                        appendLine()
                        appendLine(
                            "提示词：${image.prompt}"
                        )
                    }

                    appendLine()
                }

                if (
                    !item.message
                        .errorMessage
                        .isNullOrBlank()
                ) {
                    appendLine(
                        "> 错误：" +
                            item.message
                                .errorMessage
                    )
                    appendLine()
                }

                if (
                    !item.message
                        .citationsJson
                        .isNullOrBlank()
                ) {
                    appendLine(
                        "### 来源"
                    )
                    appendLine()

                    parseCitations(
                        item.message
                            .citationsJson
                    ).forEach {
                        index,
                        title,
                        url ->

                        appendLine(
                            "- [$index] " +
                                if (
                                    url.isNotBlank()
                                ) {
                                    "[$title]($url)"
                                } else {
                                    title
                                }
                        )
                    }

                    appendLine()
                }

                appendLine("---")
                appendLine()
            }
        }
    }

    private fun conversationToJson(
        conversation:
            ConversationEntity
    ): JSONObject {
        return JSONObject()
            .put("id", conversation.id)
            .put(
                "title",
                conversation.title
            )
            .put(
                "createdAt",
                conversation.createdAt
            )
            .put(
                "updatedAt",
                conversation.updatedAt
            )
            .put(
                "pinned",
                conversation.pinned
            )
            .put(
                "archived",
                conversation.archived
            )
            .put(
                "apiProfileId",
                conversation.apiProfileId
                    ?: JSONObject.NULL
            )
            .put(
                "preferredRoute",
                conversation.preferredRoute
            )
            .put(
                "webSearchMode",
                conversation.webSearchMode
            )
    }

    private fun messagesToJson(
        messages: List<MessageWithImages>,
        includeLocalImagePaths: Boolean
    ): JSONArray {
        val array = JSONArray()

        messages.forEach { item ->
            val images = JSONArray()

            item.images.forEach { image ->
                images.put(
                    JSONObject()
                        .put("id", image.id)
                        .put(
                            "localPath",
                            if (
                                includeLocalImagePaths
                            ) {
                                image.localPath
                            } else {
                                JSONObject.NULL
                            }
                        )
                        .put(
                            "originalUrl",
                            image.originalUrl
                                ?: JSONObject.NULL
                        )
                        .put(
                            "mimeType",
                            image.mimeType
                        )
                        .put(
                            "width",
                            image.width
                                ?: JSONObject.NULL
                        )
                        .put(
                            "height",
                            image.height
                                ?: JSONObject.NULL
                        )
                        .put(
                            "fileSize",
                            image.fileSize
                                ?: JSONObject.NULL
                        )
                        .put(
                            "sourceType",
                            image.sourceType
                        )
                        .put(
                            "prompt",
                            image.prompt
                                ?: JSONObject.NULL
                        )
                        .put(
                            "model",
                            image.model
                                ?: JSONObject.NULL
                        )
                        .put(
                            "sizeParameter",
                            image.sizeParameter
                                ?: JSONObject.NULL
                        )
                        .put(
                            "qualityParameter",
                            image.qualityParameter
                                ?: JSONObject.NULL
                        )
                )
            }

            val message =
                item.message

            array.put(
                JSONObject()
                    .put("id", message.id)
                    .put(
                        "conversationId",
                        message.conversationId
                    )
                    .put(
                        "role",
                        message.role
                    )
                    .put(
                        "messageType",
                        message.messageType
                    )
                    .put(
                        "text",
                        message.text
                    )
                    .put(
                        "originalPrompt",
                        message.originalPrompt
                            ?: JSONObject.NULL
                    )
                    .put(
                        "optimizedPrompt",
                        message.optimizedPrompt
                            ?: JSONObject.NULL
                    )
                    .put(
                        "status",
                        message.status
                    )
                    .put(
                        "errorCode",
                        message.errorCode
                            ?: JSONObject.NULL
                    )
                    .put(
                        "errorMessage",
                        message.errorMessage
                            ?: JSONObject.NULL
                    )
                    .put(
                        "requestId",
                        message.requestId
                            ?: JSONObject.NULL
                    )
                    .put(
                        "httpStatus",
                        message.httpStatus
                            ?: JSONObject.NULL
                    )
                    .put(
                        "durationMs",
                        message.durationMs
                            ?: JSONObject.NULL
                    )
                    .put(
                        "model",
                        message.model
                            ?: JSONObject.NULL
                    )
                    .put(
                        "route",
                        message.route
                            ?: JSONObject.NULL
                    )
                    .put(
                        "citationsJson",
                        message.citationsJson
                            ?: JSONObject.NULL
                    )
                    .put(
                        "searchQuery",
                        message.searchQuery
                            ?: JSONObject.NULL
                    )
                    .put(
                        "createdAt",
                        message.createdAt
                    )
                    .put(
                        "updatedAt",
                        message.updatedAt
                    )
                    .put("images", images)
            )
        }

        return array
    }

    private fun parseCitations(
        raw: String
    ): List<Triple<Int, String, String>> {
        return try {
            val array = JSONArray(raw)

            buildList {
                for (
                    index in 0 until
                        array.length()
                ) {
                    val item =
                        array.optJSONObject(index)
                            ?: continue

                    add(
                        Triple(
                            item.optInt(
                                "index",
                                index + 1
                            ),
                            item.optString(
                                "title",
                                "来源 ${index + 1}"
                            ),
                            item.optString("url")
                        )
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun safeFileName(
        value: String
    ): String {
        return value
            .replace(
                Regex(
                    """[\\/:*?"<>|]"""
                ),
                "_"
            )
            .replace(
                Regex("""\s+"""),
                "_"
            )
            .trim('_')
            .take(60)
            .ifBlank {
                "ChatImage_Conversation"
            }
    }
}
