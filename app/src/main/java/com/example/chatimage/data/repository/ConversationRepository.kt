package com.example.chatimage.data.repository

import kotlinx.coroutines.flow.first
import com.example.chatimage.data.api.SavedImageResult
import com.example.chatimage.data.database.ConversationDao
import com.example.chatimage.data.database.ConversationEntity
import com.example.chatimage.data.database.MessageDao
import com.example.chatimage.data.database.MessageEntity
import com.example.chatimage.data.database.MessageImageDao
import com.example.chatimage.data.database.MessageImageEntity
import com.example.chatimage.data.database.MessageWithImages
import com.example.chatimage.data.model.Citation
import com.example.chatimage.data.model.MessageRole
import com.example.chatimage.data.model.MessageStatus
import com.example.chatimage.data.model.MessageType
import com.example.chatimage.data.model.RequestDiagnostics
import com.example.chatimage.data.model.RequestError
import com.example.chatimage.data.model.RequestRoute
import com.example.chatimage.data.api.TokenUsage
import java.io.File
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray
import org.json.JSONObject

class ConversationRepository(
    private val conversationDao: ConversationDao,
    private val messageDao: MessageDao,
    private val messageImageDao: MessageImageDao
) {

    fun observeConversations():
        Flow<List<ConversationEntity>> {
        return conversationDao.observeAll()
    }

    fun searchConversations(
        query: String
    ): Flow<List<ConversationEntity>> {
        return conversationDao.search(
            query.trim()
        )
    }

    fun observeConversation(
        conversationId: String
    ): Flow<ConversationEntity?> {
        return conversationDao.observeById(
            conversationId
        )
    }

    fun observeMessages(
        conversationId: String
    ): Flow<List<MessageWithImages>> {
        return messageDao
            .observeForConversation(
                conversationId
            )
    }

    suspend fun getConversation(
        conversationId: String
    ): ConversationEntity? {
        return conversationDao.getById(
            conversationId
        )
    }

    suspend fun getMessages(
        conversationId: String
    ): List<MessageWithImages> {
        return messageDao
            .getForConversation(
                conversationId
            )
    }

    suspend fun createConversation(
        title: String = "新对话",
        apiProfileId: String? = null,
        preferredRoute: RequestRoute =
            RequestRoute.AUTO,
        webSearchMode: String = "OFF"
    ): ConversationEntity {
        val now = System.currentTimeMillis()

        val conversation =
            ConversationEntity(
                id = UUID.randomUUID().toString(),
                title = title
                    .trim()
                    .ifBlank {
                        "新对话"
                    },
                createdAt = now,
                updatedAt = now,
                apiProfileId = apiProfileId,
                preferredRoute =
                    preferredRoute.name,
                webSearchMode =
                    webSearchMode
            )

        conversationDao.insert(
            conversation
        )

        return conversation
    }

    suspend fun ensureConversation(
        preferredId: String? = null
    ): ConversationEntity {
        if (!preferredId.isNullOrBlank()) {
            val existing =
                conversationDao.getById(
                    preferredId
                )

            if (existing != null) {
                return existing
            }
        }

        return createConversation()
    }

    suspend fun renameConversation(
        conversationId: String,
        title: String
    ) {
        val cleanTitle = title
            .replace("\n", " ")
            .trim()
            .ifBlank {
                "新对话"
            }

        conversationDao.rename(
            conversationId =
                conversationId,
            title = cleanTitle
        )
    }

    suspend fun autoTitleIfNeeded(
        conversationId: String,
        firstUserText: String,
        maximumCharacters: Int
    ) {
        val conversation =
            conversationDao.getById(
                conversationId
            ) ?: return

        if (
            conversation.title != "新对话" &&
            conversation.title.isNotBlank()
        ) {
            return
        }

        val cleanTitle = firstUserText
            .replace(
                Regex("""\s+"""),
                " "
            )
            .trim()
            .take(
                maximumCharacters.coerceIn(
                    8,
                    100
                )
            )
            .ifBlank {
                "新对话"
            }

        conversationDao.rename(
            conversationId =
                conversationId,
            title = cleanTitle
        )
    }

    suspend fun setPinned(
        conversationId: String,
        pinned: Boolean
    ) {
        conversationDao.setPinned(
            conversationId,
            pinned
        )
    }

    suspend fun deleteConversation(
        conversationId: String,
        deleteLocalImages: Boolean = false
    ) {
        if (deleteLocalImages) {
            val messages = messageDao
                .getForConversation(
                    conversationId
                )

            messages.forEach { item ->
                item.images.forEach { image ->
                    runCatching {
                        File(
                            image.localPath
                        ).delete()
                    }
                }

                item.message
                    .attachedImagePath
                    ?.let { path ->
                        runCatching {
                            File(path).delete()
                        }
                    }
            }
        }

        conversationDao.deleteById(
            conversationId
        )
    }

    suspend fun deleteAllConversations(
        deleteLocalImages: Boolean = false
    ) {
        if (deleteLocalImages) {
            val conversations =
                conversationDao
                    .observeAll()
                    .first()

            conversations.forEach {
                deleteConversation(
                    conversationId = it.id,
                    deleteLocalImages = true
                )
            }

            return
        }

        conversationDao.deleteAll()
    }

    suspend fun addUserMessage(
        conversationId: String,
        text: String,
        route: RequestRoute,
        attachedImagePath: String? = null,
        referencedImagePath: String? = null,
        apiProfileId: String? = null,
        model: String? = null
    ): MessageEntity {
        val now = System.currentTimeMillis()

        val message = MessageEntity(
            id = UUID.randomUUID().toString(),
            conversationId =
                conversationId,
            role = MessageRole.USER.name
                .lowercase(),
            messageType = when (route) {
                RequestRoute.IMAGE_GENERATION,
                RequestRoute.IMAGE_EDIT -> {
                    MessageType
                        .IMAGE_REQUEST
                        .name
                }

                else -> {
                    MessageType.TEXT.name
                }
            },
            text = text,
            originalPrompt = text,
            attachedImagePath =
                attachedImagePath,
            referencedImagePath =
                referencedImagePath,
            status =
                MessageStatus.COMPLETED.name,
            apiProfileId = apiProfileId,
            model = model,
            route = route.name,
            createdAt = now,
            updatedAt = now
        )

        messageDao.insert(message)
        conversationDao.touch(
            conversationId,
            now
        )

        return message
    }

    suspend fun addAssistantPlaceholder(
        conversationId: String,
        route: RequestRoute,
        model: String?,
        apiProfileId: String?,
        initialText: String = ""
    ): MessageEntity {
        val now = System.currentTimeMillis()

        val messageType = when (route) {
            RequestRoute.IMAGE_GENERATION -> {
                MessageType.IMAGE_RESULT.name
            }

            RequestRoute.IMAGE_EDIT -> {
                MessageType
                    .IMAGE_EDIT_RESULT
                    .name
            }

            else -> {
                MessageType.TEXT.name
            }
        }

        val message = MessageEntity(
            id = UUID.randomUUID().toString(),
            conversationId =
                conversationId,
            role = MessageRole.ASSISTANT.name
                .lowercase(),
            messageType = messageType,
            text = initialText,
            status =
                MessageStatus.RUNNING.name,
            model = model,
            apiProfileId = apiProfileId,
            route = route.name,
            createdAt = now,
            updatedAt = now
        )

        messageDao.insert(message)
        conversationDao.touch(
            conversationId,
            now
        )

        return message
    }

    suspend fun updateMessageText(
        messageId: String,
        text: String,
        status: MessageStatus =
            MessageStatus.RUNNING
    ) {
        messageDao.updateTextAndStatus(
            messageId = messageId,
            text = text,
            status = status.name
        )
    }

    suspend fun appendMessageText(
        messageId: String,
        fragment: String
    ): String {
        val current = messageDao
            .getById(messageId)
            ?: return ""

        val updatedText =
            current.text + fragment

        messageDao.updateTextAndStatus(
            messageId = messageId,
            text = updatedText,
            status =
                MessageStatus.RUNNING.name
        )

        return updatedText
    }

    suspend fun completeTextMessage(
        messageId: String,
        text: String,
        diagnostics: RequestDiagnostics,
        usage: TokenUsage = TokenUsage(),
        citations: List<Citation> =
            emptyList(),
        searchQueries: List<String> =
            emptyList()
    ) {
        val current =
            messageDao.getById(messageId)
                ?: return

        val updated = current.copy(
            text = text,
            status =
                MessageStatus.COMPLETED.name,
            errorCode = null,
            errorMessage = null,
            requestId =
                diagnostics.requestId,
            httpStatus =
                diagnostics.httpStatus,
            durationMs =
                diagnostics.durationMs,
            inputTokens = usage.inputTokens,
            outputTokens = usage.outputTokens,
            totalTokens = usage.totalTokens,
            cachedInputTokens = usage.cachedInputTokens,
            requestMetadataJson =
                diagnosticsToJson(
                    diagnostics
                ).toString(),
            citationsJson =
                citationsToJson(
                    citations
                ).toString(),
            searchQuery =
                searchQueries
                    .distinct()
                    .joinToString("\n")
                    .takeIf {
                        it.isNotBlank()
                    },
            updatedAt =
                System.currentTimeMillis()
        )

        messageDao.update(updated)

        conversationDao.touch(
            current.conversationId
        )
    }

    suspend fun completeImageMessage(
        messageId: String,
        text: String,
        images: List<SavedImageResult>,
        prompt: String,
        model: String,
        size: String,
        quality: String,
        diagnostics: RequestDiagnostics,
        sourceType: String
    ) {
        val current =
            messageDao.getById(messageId)
                ?: return

        val updated = current.copy(
            text = text,
            status =
                MessageStatus.COMPLETED.name,
            errorCode = null,
            errorMessage = null,
            requestId =
                diagnostics.requestId,
            httpStatus =
                diagnostics.httpStatus,
            durationMs =
                diagnostics.durationMs,
            model = model,
            requestMetadataJson =
                diagnosticsToJson(
                    diagnostics
                ).toString(),
            updatedAt =
                System.currentTimeMillis()
        )

        messageDao.update(updated)

        val imageEntities =
            images.map { image ->
                MessageImageEntity(
                    id = UUID.randomUUID()
                        .toString(),
                    messageId = messageId,
                    localPath =
                        image.localPath,
                    originalUrl =
                        image.originalUrl,
                    mimeType =
                        image.mimeType,
                    width = image.width,
                    height = image.height,
                    fileSize =
                        image.fileSize,
                    sourceType =
                        sourceType,
                    prompt = prompt,
                    model = model,
                    sizeParameter =
                        size,
                    qualityParameter =
                        quality
                )
            }

        messageImageDao.insertAll(
            imageEntities
        )

        conversationDao.touch(
            current.conversationId
        )
    }

    suspend fun failMessage(
        messageId: String,
        error: RequestError,
        diagnostics: RequestDiagnostics? =
            null
    ) {
        val current =
            messageDao.getById(messageId)
                ?: return

        messageDao.updateFailure(
            messageId = messageId,
            errorCode = error.code,
            errorMessage = error.message,
            httpStatus =
                error.httpStatus
                    ?: diagnostics
                        ?.httpStatus,
            requestId =
                error.requestId
                    ?: diagnostics
                        ?.requestId,
            durationMs =
                error.durationMs
                    ?: diagnostics
                        ?.durationMs
        )

        conversationDao.touch(
            current.conversationId
        )
    }

    suspend fun cancelMessage(
        messageId: String,
        textIfEmpty: String = "已停止生成"
    ) {
        val current =
            messageDao.getById(messageId)
                ?: return

        val updatedText =
            current.text.ifBlank {
                textIfEmpty
            }

        messageDao.updateTextAndStatus(
            messageId = messageId,
            text = updatedText,
            status =
                MessageStatus.CANCELLED.name
        )

        conversationDao.touch(
            current.conversationId
        )
    }

    suspend fun deleteMessage(
        messageId: String,
        deleteLocalImages: Boolean = false
    ) {
        if (deleteLocalImages) {
            val images =
                messageImageDao.getForMessage(
                    messageId
                )

            images.forEach {
                runCatching {
                    File(it.localPath).delete()
                }
            }

            val message =
                messageDao.getById(messageId)

            message
                ?.attachedImagePath
                ?.let {
                    runCatching {
                        File(it).delete()
                    }
                }
        }

        messageDao.deleteById(messageId)
    }

    suspend fun getMessage(
        messageId: String
    ): MessageEntity? {
        return messageDao.getById(
            messageId
        )
    }

    suspend fun getLatestGeneratedImagePath(
        conversationId: String
    ): String? {
        val latestImageMessage =
            messageDao.getLatestImageMessage(
                conversationId
            ) ?: return null

        return messageImageDao
            .getForMessage(
                latestImageMessage.id
            )
            .lastOrNull {
                File(it.localPath).exists()
            }
            ?.localPath
    }

    suspend fun getAllGeneratedImagePaths(
        conversationId: String
    ): List<String> {
        return messageDao
            .getForConversation(
                conversationId
            )
            .flatMap {
                it.images
            }
            .map {
                it.localPath
            }
            .filter {
                File(it).exists()
            }
    }

    suspend fun getImage(
        imageId: String,
        conversationId: String
    ): MessageImageEntity? {
        return messageDao
            .getForConversation(
                conversationId
            )
            .flatMap {
                it.images
            }
            .firstOrNull {
                it.id == imageId
            }
    }

    suspend fun setOptimizedPrompt(
        messageId: String,
        optimizedPrompt: String
    ) {
        val current =
            messageDao.getById(messageId)
                ?: return

        messageDao.update(
            current.copy(
                optimizedPrompt =
                    optimizedPrompt,
                updatedAt =
                    System.currentTimeMillis()
            )
        )
    }

    suspend fun updateUserMessageText(
        messageId: String,
        newText: String
    ) {
        val current =
            messageDao.getById(messageId)
                ?: return

        if (
            !current.role.equals(
                MessageRole.USER.name,
                ignoreCase = true
            )
        ) {
            return
        }

        messageDao.update(
            current.copy(
                text = newText.trim(),
                originalPrompt =
                    newText.trim(),
                updatedAt =
                    System.currentTimeMillis()
            )
        )

        conversationDao.touch(
            current.conversationId
        )
    }

    suspend fun markRunningMessagesCancelled(
        conversationId: String
    ) {
        val messages =
            messageDao.getForConversation(
                conversationId
            )

        messages
            .map {
                it.message
            }
            .filter {
                it.status ==
                    MessageStatus.RUNNING.name ||
                    it.status ==
                    MessageStatus.QUEUED.name
            }
            .forEach {
                cancelMessage(it.id)
            }
    }

    private fun citationsToJson(
        citations: List<Citation>
    ): JSONArray {
        val array = JSONArray()

        citations.forEach {
            array.put(
                JSONObject()
                    .put("index", it.index)
                    .put("title", it.title)
                    .put("url", it.url)
                    .put(
                        "snippet",
                        it.snippet
                    )
                    .put(
                        "publishedAt",
                        it.publishedAt
                            ?: JSONObject.NULL
                    )
                    .put(
                        "source",
                        it.source
                            ?: JSONObject.NULL
                    )
            )
        }

        return array
    }

    private fun diagnosticsToJson(
        diagnostics: RequestDiagnostics
    ): JSONObject {
        return JSONObject()
            .put(
                "endpoint",
                diagnostics.endpoint
            )
            .put(
                "method",
                diagnostics.method
            )
            .put(
                "model",
                diagnostics.model
                    ?: JSONObject.NULL
            )
            .put(
                "httpStatus",
                diagnostics.httpStatus
                    ?: JSONObject.NULL
            )
            .put(
                "durationMs",
                diagnostics.durationMs
            )
            .put(
                "contentType",
                diagnostics.contentType
                    ?: JSONObject.NULL
            )
            .put(
                "server",
                diagnostics.server
                    ?: JSONObject.NULL
            )
            .put(
                "requestId",
                diagnostics.requestId
                    ?: JSONObject.NULL
            )
            .put(
                "readableError",
                diagnostics.readableError
                    ?: JSONObject.NULL
            )
    }
}
