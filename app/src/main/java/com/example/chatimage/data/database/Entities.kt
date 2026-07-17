package com.example.chatimage.data.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "conversations",
    indices = [
        Index(value = ["updatedAt"]),
        Index(value = ["pinned"])
    ]
)
data class ConversationEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),

    val title: String = "新对话",

    val createdAt: Long = System.currentTimeMillis(),

    val updatedAt: Long = System.currentTimeMillis(),

    val pinned: Boolean = false,

    val archived: Boolean = false,

    val apiProfileId: String? = null,

    val preferredRoute: String = "AUTO",

    val webSearchMode: String = "OFF"
)

@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversationId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["conversationId"]),
        Index(value = ["createdAt"]),
        Index(value = ["status"])
    ]
)
data class MessageEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),

    val conversationId: String,

    val role: String,

    val messageType: String = "TEXT",

    val text: String = "",

    val originalPrompt: String? = null,

    val optimizedPrompt: String? = null,

    val attachedImagePath: String? = null,

    val referencedImagePath: String? = null,

    val status: String = "COMPLETED",

    val errorCode: String? = null,

    val errorMessage: String? = null,

    val requestId: String? = null,

    val httpStatus: Int? = null,

    val durationMs: Long? = null,

    val model: String? = null,

    val apiProfileId: String? = null,

    val route: String? = null,

    val requestMetadataJson: String? = null,

    val citationsJson: String? = null,

    val searchQuery: String? = null,

    val createdAt: Long = System.currentTimeMillis(),

    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "message_images",
    foreignKeys = [
        ForeignKey(
            entity = MessageEntity::class,
            parentColumns = ["id"],
            childColumns = ["messageId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["messageId"]),
        Index(value = ["createdAt"])
    ]
)
data class MessageImageEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),

    val messageId: String,

    val localPath: String,

    val originalUrl: String? = null,

    val mimeType: String = "image/png",

    val width: Int? = null,

    val height: Int? = null,

    val fileSize: Long? = null,

    val sourceType: String = "GENERATED",

    val prompt: String? = null,

    val model: String? = null,

    val sizeParameter: String? = null,

    val qualityParameter: String? = null,

    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "api_profiles",
    indices = [
        Index(value = ["name"], unique = true),
        Index(value = ["isActive"])
    ]
)
data class ApiProfileEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),

    val name: String,

    val isActive: Boolean = false,

    val enabled: Boolean = true,

    val baseUrl: String,

    val encryptedApiKeyAlias: String,

    val chatModel: String,

    val imageModel: String,

    val visionModel: String = "",

    val optimizerModel: String = "",

    val chatPath: String = "/chat/completions",

    val imageGenerationPath: String = "/images/generations",

    val imageEditPath: String = "/images/edits",

    val modelsPath: String = "/models",

    val authenticationMode: String = "BEARER",

    val authorizationHeaderName: String = "Authorization",

    val authorizationPrefix: String = "Bearer ",

    val customHeadersJson: String = "{}",

    val createdAt: Long = System.currentTimeMillis(),

    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "search_profiles",
    indices = [
        Index(value = ["name"], unique = true),
        Index(value = ["isActive"])
    ]
)
data class SearchProfileEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),

    val name: String,

    val isActive: Boolean = false,

    val enabled: Boolean = true,

    val providerType: String = "CUSTOM_JSON",

    val baseUrl: String,

    val path: String = "",

    val requestMethod: String = "POST",

    val encryptedApiKeyAlias: String,

    val authenticationMode: String = "BEARER",

    val authorizationHeaderName: String = "Authorization",

    val authorizationPrefix: String = "Bearer ",

    val queryField: String = "query",

    val countField: String = "max_results",

    val languageField: String = "language",

    val regionField: String = "region",

    val resultArrayPath: String = "results",

    val resultTitlePath: String = "title",

    val resultUrlPath: String = "url",

    val resultSnippetPath: String = "snippet",

    val resultDatePath: String = "published_at",

    val resultSourcePath: String = "source",

    val customHeadersJson: String = "{}",

    val extraRequestJson: String = "{}",

    val createdAt: Long = System.currentTimeMillis(),

    val updatedAt: Long = System.currentTimeMillis()
)
