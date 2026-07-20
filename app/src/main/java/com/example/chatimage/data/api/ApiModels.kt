package com.example.chatimage.data.api

import com.example.chatimage.data.model.Citation
import com.example.chatimage.data.model.RequestDiagnostics
import com.example.chatimage.data.model.RequestError
import com.example.chatimage.data.model.SearchResult
import org.json.JSONArray
import org.json.JSONObject

sealed interface ApiOutcome<out T> {

    data class Success<T>(
        val value: T,
        val diagnostics: RequestDiagnostics
    ) : ApiOutcome<T>

    data class Failure(
        val error: RequestError,
        val diagnostics: RequestDiagnostics? = null
    ) : ApiOutcome<Nothing>
}

sealed interface ChatContentPart {

    fun toJson(): JSONObject

    data class Text(
        val text: String
    ) : ChatContentPart {

        override fun toJson(): JSONObject {
            return JSONObject()
                .put("type", "text")
                .put("text", text)
        }
    }

    data class ImageUrl(
        val url: String,
        val detail: String? = null
    ) : ChatContentPart {

        override fun toJson(): JSONObject {
            val imageUrl = JSONObject()
                .put("url", url)

            if (!detail.isNullOrBlank()) {
                imageUrl.put("detail", detail)
            }

            return JSONObject()
                .put("type", "image_url")
                .put("image_url", imageUrl)
        }
    }
}

data class ChatWireMessage(
    val role: String,
    val content: String? = null,
    val contentParts: List<ChatContentPart> =
        emptyList(),
    val name: String? = null,
    val toolCallId: String? = null,
    val toolCalls: List<ToolCall> = emptyList()
) {

    fun toJson(): JSONObject {
        val result = JSONObject()
            .put("role", role)

        if (contentParts.isNotEmpty()) {
            val array = JSONArray()

            contentParts.forEach { part ->
                array.put(part.toJson())
            }

            result.put("content", array)
        } else if (content != null) {
            result.put("content", content)
        } else if (toolCalls.isNotEmpty()) {
            result.put("content", JSONObject.NULL)
        }

        if (!name.isNullOrBlank()) {
            result.put("name", name)
        }

        if (!toolCallId.isNullOrBlank()) {
            result.put(
                "tool_call_id",
                toolCallId
            )
        }

        if (toolCalls.isNotEmpty()) {
            val array = JSONArray()

            toolCalls.forEach {
                array.put(it.toJson())
            }

            result.put(
                "tool_calls",
                array
            )
        }

        return result
    }
}

data class ToolCall(
    val id: String,
    val type: String = "function",
    val functionName: String,
    val argumentsJson: String
) {

    fun toJson(): JSONObject {
        return JSONObject()
            .put("id", id)
            .put("type", type)
            .put(
                "function",
                JSONObject()
                    .put(
                        "name",
                        functionName
                    )
                    .put(
                        "arguments",
                        argumentsJson
                    )
            )
    }
}

data class ToolDefinition(
    val name: String,
    val description: String,
    val queryParameterName: String,
    val countParameterName: String
) {

    fun toOpenAiJson(): JSONObject {
        return JSONObject()
            .put("type", "function")
            .put(
                "function",
                JSONObject()
                    .put("name", name)
                    .put(
                        "description",
                        description
                    )
                    .put(
                        "parameters",
                        JSONObject()
                            .put(
                                "type",
                                "object"
                            )
                            .put(
                                "properties",
                                JSONObject()
                                    .put(
                                        queryParameterName,
                                        JSONObject()
                                            .put(
                                                "type",
                                                "string"
                                            )
                                            .put(
                                                "description",
                                                "简洁明确的搜索关键词"
                                            )
                                    )
                                    .put(
                                        countParameterName,
                                        JSONObject()
                                            .put(
                                                "type",
                                                "integer"
                                            )
                                            .put(
                                                "minimum",
                                                1
                                            )
                                            .put(
                                                "maximum",
                                                10
                                            )
                                    )
                            )
                            .put(
                                "required",
                                JSONArray()
                                    .put(
                                        queryParameterName
                                    )
                            )
                    )
            )
    }
}

data class ChatCompletionResult(
    val content: String,
    val toolCalls: List<ToolCall> = emptyList(),
    val finishReason: String? = null,
    val rawResponsePreview: String? = null,
    val citations: List<Citation> = emptyList(),
    val searchQueries: List<String> = emptyList(),
    val usage: TokenUsage = TokenUsage()
)

data class TokenUsage(
    val inputTokens: Long? = null,
    val outputTokens: Long? = null,
    val totalTokens: Long? = null,
    val cachedInputTokens: Long? = null
)

data class SearchResponse(
    val query: String,
    val results: List<SearchResult>
)

data class ImageRequestOptions(
    val model: String,
    val prompt: String,
    val size: String,
    val quality: String,
    val count: Int,
    val responseFormat: String,
    val sourceImagePath: String? = null
)

data class SavedImageResult(
    val localPath: String,
    val originalUrl: String? = null,
    val mimeType: String = "image/png",
    val fileSize: Long = 0,
    val width: Int? = null,
    val height: Int? = null
)

data class ImageGenerationResult(
    val images: List<SavedImageResult>,
    val revisedPrompt: String? = null
)

data class ToolEngineResult(
    val content: String,
    val citations: List<Citation>,
    val searchQueries: List<String>,
    val usedToolCalls: Boolean,
    val usedFallback: Boolean,
    val diagnostics: RequestDiagnostics,
    val usage: TokenUsage = TokenUsage()
)
