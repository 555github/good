package com.example.chatimage.ui

import com.example.chatimage.data.database.ApiProfileEntity
import com.example.chatimage.data.database.ConversationEntity
import com.example.chatimage.data.database.MessageEntity
import com.example.chatimage.data.database.MessageImageEntity
import com.example.chatimage.data.database.SearchProfileEntity
import com.example.chatimage.data.model.AppSettings
import com.example.chatimage.data.model.Citation
import com.example.chatimage.data.model.RequestRoute
import com.example.chatimage.data.model.RouteDecision
import org.json.JSONArray
import org.json.JSONObject

data class MessageUiModel(
    val message: MessageEntity,
    val images: List<MessageImageEntity>,
    val citations: List<Citation>
) {
    val isUser: Boolean
        get() = message.role.equals(
            "user",
            ignoreCase = true
        )

    val isAssistant: Boolean
        get() = message.role.equals(
            "assistant",
            ignoreCase = true
        )

    val isRunning: Boolean
        get() = message.status == "RUNNING" ||
            message.status == "QUEUED"

    val isFailed: Boolean
        get() = message.status == "FAILED"

    val isCancelled: Boolean
        get() = message.status == "CANCELLED"

    val hasText: Boolean
        get() = message.text.isNotBlank()

    val hasImages: Boolean
        get() = images.isNotEmpty()

    companion object {

        fun parseCitations(
            raw: String?
        ): List<Citation> {
            if (raw.isNullOrBlank()) {
                return emptyList()
            }

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
                            Citation(
                                index =
                                    item.optInt(
                                        "index",
                                        index + 1
                                    ),
                                title =
                                    item.optString(
                                        "title",
                                        "来源 ${index + 1}"
                                    ),
                                url =
                                    item.optString(
                                        "url"
                                    ),
                                snippet =
                                    item.optString(
                                        "snippet"
                                    ),
                                publishedAt =
                                    item.optString(
                                        "publishedAt"
                                    ).takeIf {
                                        it.isNotBlank() &&
                                            it != "null"
                                    },
                                source =
                                    item.optString(
                                        "source"
                                    ).takeIf {
                                        it.isNotBlank() &&
                                            it != "null"
                                    }
                            )
                        )
                    }
                }
            } catch (_: Exception) {
                emptyList()
            }
        }
    }
}

data class PendingPromptOptimization(
    val originalPrompt: String,
    val attachedImagePath: String?,
    val explicitlyReferencedImagePath:
        String?,
    val forceSearch: Boolean,
    val routeDecision: RouteDecision,
    val optimizing: Boolean = false,
    val optimizedPrompt: String? = null,
    val error: String? = null
)

data class PendingRouteConfirmation(
    val prompt: String,
    val attachedImagePath: String?,
    val explicitlyReferencedImagePath:
        String?,
    val forceSearch: Boolean,
    val decision: RouteDecision
)

data class AppUiState(
    val initialized: Boolean = false,

    val appSettings: AppSettings =
        AppSettings(),

    val conversations:
        List<ConversationEntity> =
        emptyList(),

    val currentConversationId:
        String? = null,

    val currentConversation:
        ConversationEntity? = null,

    val messages:
        List<MessageUiModel> =
        emptyList(),

    val apiProfiles:
        List<ApiProfileEntity> =
        emptyList(),

    val searchProfiles:
        List<SearchProfileEntity> =
        emptyList(),

    val activeApiProfile:
        ApiProfileEntity? = null,

    val activeSearchProfile:
        SearchProfileEntity? = null,

    val selectedRoute:
        RequestRoute =
        RequestRoute.AUTO,

    val forceSearchForNextRequest:
        Boolean = false,

    val attachedImagePath:
        String? = null,

    val attachedFilePath:
        String? = null,

    val attachedFileName:
        String? = null,

    val attachedFileMimeType:
        String? = null,

    val referencedImagePath:
        String? = null,

    val referencedImageId:
        String? = null,

    val routePreview:
        RouteDecision? = null,

    val pendingOptimization:
        PendingPromptOptimization? = null,

    val pendingRouteConfirmation:
        PendingRouteConfirmation? = null,

    val loading: Boolean = false,

    val activeAssistantMessageId:
        String? = null,

    val statusText: String = "",

    val globalError: String? = null,

    val historySearchQuery:
        String = "",

    val showHistory: Boolean = false,

    val showSettings: Boolean = false,

    val showImageParameters: Boolean = false,

    val showApiProfiles: Boolean = false,

    val showSearchProfiles: Boolean = false,

    val selectedImageForViewer:
        MessageImageEntity? = null
)
