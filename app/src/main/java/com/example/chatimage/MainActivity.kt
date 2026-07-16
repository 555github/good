package com.example.chatimage

import android.app.Application
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import coil.compose.AsyncImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URI
import java.util.UUID
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                ChatImageApp(viewModel)
            }
        }
    }
}

enum class AppMode {
    CHAT,
    IMAGE
}

data class ChatSettings(
    val baseUrl: String = "https://api.example.com/v1",
    val apiKey: String = "",
    val chatModel: String = "gpt-4o",
    val imageModel: String = "image-2",
    val stream: Boolean = true,
    val temperature: Float = 0.7f,
    val maxTokens: Int = 2048,
    val contextMessages: Int = 20,
    val imageSize: String = "1024x1024",
    val imageQuality: String = "standard",
    val imageCount: Int = 1,
    val imageResponseFormat: String = "url",
    val smartImageParameters: Boolean = true
)

data class ConversationMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: String,
    val text: String = "",
    val imagePaths: List<String> = emptyList(),
    val attachedImagePath: String? = null,
    val generating: Boolean = false,
    val error: String? = null
)

data class Conversation(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "新对话",
    val messages: List<ConversationMessage> = emptyList(),
    val updatedAt: Long = System.currentTimeMillis()
)

data class UiState(
    val settings: ChatSettings = ChatSettings(),
    val mode: AppMode = AppMode.CHAT,
    val conversations: List<Conversation> = emptyList(),
    val currentConversationId: String = "",
    val loading: Boolean = false,
    val error: String? = null
) {
    val currentConversation: Conversation?
        get() = conversations.firstOrNull {
            it.id == currentConversationId
        }
}

data class ImageRequestOptions(
    val size: String,
    val quality: String,
    val count: Int,
    val responseFormat: String
)

class SettingsStore(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val preferences = EncryptedSharedPreferences.create(
        context,
        "chatimage_secure_settings_v2",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun load(): ChatSettings {
        return ChatSettings(
            baseUrl = preferences.getString(
                "base_url",
                "https://api.example.com/v1"
            ) ?: "https://api.example.com/v1",
            apiKey = preferences.getString(
                "api_key",
                ""
            ).orEmpty(),
            chatModel = preferences.getString(
                "chat_model",
                "gpt-4o"
            ) ?: "gpt-4o",
            imageModel = preferences.getString(
                "image_model",
                "image-2"
            ) ?: "image-2",
            stream = preferences.getBoolean(
                "stream",
                true
            ),
            temperature = preferences.getFloat(
                "temperature",
                0.7f
            ),
            maxTokens = preferences.getInt(
                "max_tokens",
                2048
            ),
            contextMessages = preferences.getInt(
                "context_messages",
                20
            ),
            imageSize = preferences.getString(
                "image_size",
                "1024x1024"
            ) ?: "1024x1024",
            imageQuality = preferences.getString(
                "image_quality",
                "standard"
            ) ?: "standard",
            imageCount = preferences.getInt(
                "image_count",
                1
            ),
            imageResponseFormat = preferences.getString(
                "image_response_format",
                "url"
            ) ?: "url",
            smartImageParameters = preferences.getBoolean(
                "smart_image_parameters",
                true
            )
        )
    }

    fun save(settings: ChatSettings) {
        preferences.edit()
            .putString("base_url", settings.baseUrl.trimEnd('/'))
            .putString("api_key", settings.apiKey.trim())
            .putString("chat_model", settings.chatModel.trim())
            .putString("image_model", settings.imageModel.trim())
            .putBoolean("stream", settings.stream)
            .putFloat("temperature", settings.temperature)
            .putInt("max_tokens", settings.maxTokens)
            .putInt("context_messages", settings.contextMessages)
            .putString("image_size", settings.imageSize.trim())
            .putString("image_quality", settings.imageQuality.trim())
            .putInt("image_count", settings.imageCount)
            .putString(
                "image_response_format",
                settings.imageResponseFormat.trim()
            )
            .putBoolean(
                "smart_image_parameters",
                settings.smartImageParameters
            )
            .apply()
    }
}

class ConversationStore(context: Context) {

    private val preferences = context.getSharedPreferences(
        "chatimage_conversations_v2",
        Context.MODE_PRIVATE
    )

    fun load(): List<Conversation> {
        val raw = preferences.getString(
            "conversations",
            null
        ) ?: return emptyList()

        return try {
            val array = JSONArray(raw)

            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    add(conversationFromJson(item))
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun save(conversations: List<Conversation>) {
        val array = JSONArray()

        conversations.forEach { conversation ->
            array.put(conversationToJson(conversation))
        }

        preferences.edit()
            .putString("conversations", array.toString())
            .apply()
    }

    private fun conversationToJson(
        conversation: Conversation
    ): JSONObject {
        val messages = JSONArray()

        conversation.messages.forEach { message ->
            val images = JSONArray()

            message.imagePaths.forEach { path ->
                images.put(path)
            }

            messages.put(
                JSONObject()
                    .put("id", message.id)
                    .put("role", message.role)
                    .put("text", message.text)
                    .put("imagePaths", images)
                    .put(
                        "attachedImagePath",
                        message.attachedImagePath
                    )
                    .put("generating", false)
                    .put("error", message.error)
            )
        }

        return JSONObject()
            .put("id", conversation.id)
            .put("title", conversation.title)
            .put("messages", messages)
            .put("updatedAt", conversation.updatedAt)
    }

    private fun conversationFromJson(
        json: JSONObject
    ): Conversation {
        val messageArray = json.optJSONArray("messages")
            ?: JSONArray()

        val messages = buildList {
            for (index in 0 until messageArray.length()) {
                val item = messageArray.optJSONObject(index)
                    ?: continue

                val imageArray = item.optJSONArray("imagePaths")
                    ?: JSONArray()

                val imagePaths = buildList {
                    for (imageIndex in 0 until imageArray.length()) {
                        val path = imageArray.optString(imageIndex)

                        if (path.isNotBlank()) {
                            add(path)
                        }
                    }
                }

                add(
                    ConversationMessage(
                        id = item.optString(
                            "id",
                            UUID.randomUUID().toString()
                        ),
                        role = item.optString("role", "assistant"),
                        text = item.optString("text", ""),
                        imagePaths = imagePaths,
                        attachedImagePath = item
                            .optString("attachedImagePath")
                            .takeIf { it.isNotBlank() },
                        generating = false,
                        error = item
                            .optString("error")
                            .takeIf { it.isNotBlank() }
                    )
                )
            }
        }

        return Conversation(
            id = json.optString(
                "id",
                UUID.randomUUID().toString()
            ),
            title = json.optString("title", "新对话"),
            messages = messages,
            updatedAt = json.optLong(
                "updatedAt",
                System.currentTimeMillis()
            )
        )
    }
}

class ApiClient(
    private val context: Context
) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(300, TimeUnit.SECONDS)
        .writeTimeout(300, TimeUnit.SECONDS)
        .build()

    private fun endpoint(
        baseUrl: String,
        path: String
    ): String {
        return baseUrl.trimEnd('/') +
            "/" +
            path.trimStart('/')
    }

    suspend fun chat(
        settings: ChatSettings,
        messages: List<ConversationMessage>,
        onFragment: suspend (String) -> Unit
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val messageArray = JSONArray()

            messages.forEach { message ->
                if (
                    message.text.isNotBlank() &&
                    (
                        message.role == "user" ||
                            message.role == "assistant" ||
                            message.role == "system"
                        )
                ) {
                    messageArray.put(
                        JSONObject()
                            .put("role", message.role)
                            .put("content", message.text)
                    )
                }
            }

            val requestJson = JSONObject()
                .put("model", settings.chatModel)
                .put("messages", messageArray)
                .put(
                    "temperature",
                    settings.temperature.toDouble()
                )
                .put("max_tokens", settings.maxTokens)
                .put("stream", settings.stream)

            val requestBody = requestJson
                .toString()
                .toRequestBody(
                    "application/json; charset=utf-8".toMediaType()
                )

            val requestBuilder = Request.Builder()
                .url(
                    endpoint(
                        settings.baseUrl,
                        "chat/completions"
                    )
                )
                .addHeader(
                    "Authorization",
                    "Bearer ${settings.apiKey}"
                )
                .post(requestBody)

            if (settings.stream) {
                requestBuilder.addHeader(
                    "Accept",
                    "text/event-stream"
                )
            } else {
                requestBuilder.addHeader(
                    "Accept",
                    "application/json"
                )
            }

            client.newCall(
                requestBuilder.build()
            ).execute().use { response ->
                val responseBody = response.body
                    ?: return@withContext Result.failure(
                        Exception("聊天接口返回内容为空")
                    )

                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        Exception(
                            "聊天请求失败：HTTP ${response.code}\n" +
                                responseBody.string().take(3000)
                        )
                    )
                }

                if (!settings.stream) {
                    val root = JSONObject(responseBody.string())

                    val text = root
                        .optJSONArray("choices")
                        ?.optJSONObject(0)
                        ?.optJSONObject("message")
                        ?.optString("content")
                        .orEmpty()

                    if (text.isBlank()) {
                        return@withContext Result.failure(
                            Exception(
                                "接口返回成功，但没有找到回复文本"
                            )
                        )
                    }

                    withContext(Dispatchers.Main) {
                        onFragment(text)
                    }

                    return@withContext Result.success(Unit)
                }

                val source = responseBody.source()
                var received = false

                while (!source.exhausted()) {
                    val line = source.readUtf8Line()
                        ?.trim()
                        .orEmpty()

                    if (!line.startsWith("data:")) {
                        continue
                    }

                    val data = line
                        .removePrefix("data:")
                        .trim()

                    if (data == "[DONE]") {
                        break
                    }

                    if (data.isBlank()) {
                        continue
                    }

                    try {
                        val root = JSONObject(data)
                        val choice = root
                            .optJSONArray("choices")
                            ?.optJSONObject(0)

                        val text = choice
                            ?.optJSONObject("delta")
                            ?.optString("content")
                            .orEmpty()

                        if (text.isNotEmpty()) {
                            received = true

                            withContext(Dispatchers.Main) {
                                onFragment(text)
                            }
                        }
                    } catch (_: Exception) {
                        // 忽略非 JSON 的 SSE 内容
                    }
                }

                if (!received) {
                    return@withContext Result.failure(
                        Exception(
                            "没有解析到流式文本，请尝试关闭流式输出"
                        )
                    )
                }
            }

            Result.success(Unit)
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            Result.failure(exception)
        }
    }

    suspend fun generateImage(
        settings: ChatSettings,
        options: ImageRequestOptions,
        prompt: String,
        sourceImage: File?
    ): Result<List<String>> = withContext(Dispatchers.IO) {
        try {
            val responseText = if (sourceImage == null) {
                requestImageGeneration(
                    settings = settings,
                    options = options,
                    prompt = prompt
                )
            } else {
                requestImageEdit(
                    settings = settings,
                    options = options,
                    prompt = prompt,
                    sourceImage = sourceImage
                )
            }

            val paths = processImageResponse(
                responseText = responseText,
                settings = settings
            )

            if (paths.isEmpty()) {
                Result.failure(
                    Exception(
                        "图片接口返回成功，但没有解析到图片数据"
                    )
                )
            } else {
                Result.success(paths)
            }
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            Result.failure(exception)
        }
    }

    private fun requestImageGeneration(
        settings: ChatSettings,
        options: ImageRequestOptions,
        prompt: String
    ): String {
        val json = JSONObject()
            .put("model", settings.imageModel)
            .put("prompt", prompt)
            .put("n", options.count)
            .put("size", options.size)
            .put("quality", options.quality)
            .put(
                "response_format",
                options.responseFormat
            )

        /*
         * 图片接口始终是非流式。
         * 这里不会读取聊天设置中的 stream。
         */
        val body = json
            .toString()
            .toRequestBody(
                "application/json; charset=utf-8".toMediaType()
            )

        val request = Request.Builder()
            .url(
                endpoint(
                    settings.baseUrl,
                    "images/generations"
                )
            )
            .addHeader(
                "Authorization",
                "Bearer ${settings.apiKey}"
            )
            .addHeader(
                "Accept",
                "application/json"
            )
            .post(body)
            .build()

        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()

            if (!response.isSuccessful) {
                throw Exception(
                    "文生图失败：HTTP ${response.code}\n" +
                        text.take(3000)
                )
            }

            return text
        }
    }

    private fun requestImageEdit(
        settings: ChatSettings,
        options: ImageRequestOptions,
        prompt: String,
        sourceImage: File
    ): String {
        val mediaType = when (
            sourceImage.extension.lowercase()
        ) {
            "jpg", "jpeg" -> "image/jpeg"
            "webp" -> "image/webp"
            else -> "image/png"
        }.toMediaType()

        val multipart = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "model",
                settings.imageModel
            )
            .addFormDataPart(
                "prompt",
                prompt
            )
            .addFormDataPart(
                "n",
                options.count.toString()
            )
            .addFormDataPart(
                "size",
                options.size
            )
            .addFormDataPart(
                "quality",
                options.quality
            )
            .addFormDataPart(
                "response_format",
                options.responseFormat
            )
            .addFormDataPart(
                "image",
                sourceImage.name,
                sourceImage.asRequestBody(mediaType)
            )
            .build()

        val request = Request.Builder()
            .url(
                endpoint(
                    settings.baseUrl,
                    "images/edits"
                )
            )
            .addHeader(
                "Authorization",
                "Bearer ${settings.apiKey}"
            )
            .addHeader(
                "Accept",
                "application/json"
            )
            .post(multipart)
            .build()

        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()

            if (!response.isSuccessful) {
                throw Exception(
                    "图生图失败：HTTP ${response.code}\n" +
                        text.take(3000)
                )
            }

            return text
        }
    }

    private fun processImageResponse(
        responseText: String,
        settings: ChatSettings
    ): List<String> {
        val root = JSONObject(responseText)

        val data = root.optJSONArray("data")
            ?: root.optJSONArray("images")
            ?: throw Exception(
                "图片接口没有返回 data 或 images 数组：\n" +
                    responseText.take(1500)
            )

        val result = mutableListOf<String>()

        for (index in 0 until data.length()) {
            val item = data.optJSONObject(index)
                ?: continue

            val base64 = listOf(
                item.optString("b64_json"),
                item.optString("base64")
            ).firstOrNull {
                it.isNotBlank()
            }

            if (!base64.isNullOrBlank()) {
                result += saveBase64Image(base64)
                continue
            }

            val rawUrl = listOf(
                item.optString("url"),
                item.optString("image_url")
            ).firstOrNull {
                it.isNotBlank()
            }

            if (!rawUrl.isNullOrBlank()) {
                if (rawUrl.startsWith("data:image")) {
                    result += saveBase64Image(rawUrl)
                } else {
                    result += downloadImage(
                        rawUrl = rawUrl,
                        settings = settings
                    )
                }
            }
        }

        return result
    }

    private fun saveBase64Image(
        value: String
    ): String {
        val cleanValue = value.substringAfter(
            "base64,",
            value
        )

        val bytes = android.util.Base64.decode(
            cleanValue,
            android.util.Base64.DEFAULT
        )

        return saveGeneratedBytes(
            bytes = bytes,
            extension = "png"
        )
    }

    private fun downloadImage(
        rawUrl: String,
        settings: ChatSettings
    ): String {
        val absoluteUrl = if (
            rawUrl.startsWith("http://") ||
            rawUrl.startsWith("https://")
        ) {
            rawUrl
        } else {
            URI(
                settings.baseUrl.trimEnd('/') + "/"
            ).resolve(rawUrl).toString()
        }

        val request = Request.Builder()
            .url(absoluteUrl)
            .addHeader(
                "Authorization",
                "Bearer ${settings.apiKey}"
            )
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception(
                    "图片已生成，但下载失败：HTTP ${response.code}\n$absoluteUrl"
                )
            }

            val bytes = response.body?.bytes()
                ?: throw Exception("下载到的图片为空")

            val contentType = response
                .header("Content-Type")
                .orEmpty()

            val extension = when {
                contentType.contains("jpeg") -> "jpg"
                contentType.contains("webp") -> "webp"
                else -> "png"
            }

            return saveGeneratedBytes(
                bytes = bytes,
                extension = extension
            )
        }
    }

    private fun saveGeneratedBytes(
        bytes: ByteArray,
        extension: String
    ): String {
        val directory = File(
            context.filesDir,
            "generated"
        )

        directory.mkdirs()

        val file = File(
            directory,
            "image_${System.currentTimeMillis()}_" +
                "${UUID.randomUUID()}.$extension"
        )

        file.writeBytes(bytes)

        return file.absolutePath
    }
}

class MainViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val settingsStore = SettingsStore(application)
    private val conversationStore = ConversationStore(application)
    private val apiClient = ApiClient(application)

    private val initialConversations =
        conversationStore.load().ifEmpty {
            listOf(Conversation())
        }

    private val _uiState = MutableStateFlow(
        UiState(
            settings = settingsStore.load(),
            conversations = initialConversations,
            currentConversationId = initialConversations
                .first()
                .id
        )
    )

    val uiState = _uiState.asStateFlow()

    private var requestJob: Job? = null

    fun setMode(mode: AppMode) {
        _uiState.value = _uiState.value.copy(
            mode = mode
        )
    }

    fun saveSettings(settings: ChatSettings) {
        val normalized = settings.copy(
            baseUrl = settings.baseUrl
                .trim()
                .trimEnd('/'),
            apiKey = settings.apiKey.trim(),
            chatModel = settings.chatModel.trim(),
            imageModel = settings.imageModel.trim(),
            imageSize = settings.imageSize.trim(),
            imageQuality = settings.imageQuality.trim(),
            imageResponseFormat = settings
                .imageResponseFormat
                .trim()
        )

        settingsStore.save(normalized)

        _uiState.value = _uiState.value.copy(
            settings = normalized
        )
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(
            error = null
        )
    }

    fun newConversation() {
        stop()

        val conversation = Conversation()

        val updated = listOf(conversation) +
            _uiState.value.conversations

        _uiState.value = _uiState.value.copy(
            conversations = updated,
            currentConversationId = conversation.id,
            error = null
        )

        persist()
    }

    fun selectConversation(id: String) {
        stop()

        if (
            _uiState.value.conversations.any {
                it.id == id
            }
        ) {
            _uiState.value = _uiState.value.copy(
                currentConversationId = id,
                error = null
            )
        }
    }

    fun deleteConversation(id: String) {
        stop()

        var remaining = _uiState
            .value
            .conversations
            .filterNot {
                it.id == id
            }

        if (remaining.isEmpty()) {
            remaining = listOf(Conversation())
        }

        val newCurrentId = if (
            remaining.any {
                it.id == _uiState.value.currentConversationId
            }
        ) {
            _uiState.value.currentConversationId
        } else {
            remaining.first().id
        }

        _uiState.value = _uiState.value.copy(
            conversations = remaining,
            currentConversationId = newCurrentId
        )

        persist()
    }

    fun stop() {
        requestJob?.cancel()
        requestJob = null

        updateGeneratingMessageAfterStop()

        _uiState.value = _uiState.value.copy(
            loading = false
        )

        persist()
    }

    fun send(
        prompt: String,
        attachedImagePath: String?
    ) {
        val text = prompt.trim()
        val state = _uiState.value

        if (text.isBlank() || state.loading) {
            return
        }

        if (!validateSettings(state)) {
            return
        }

        if (
            state.mode == AppMode.IMAGE &&
            state.settings.imageModel.isBlank()
        ) {
            _uiState.value = state.copy(
                error = "请先设置生图模型名称"
            )
            return
        }

        val userMessage = ConversationMessage(
            role = "user",
            text = text,
            attachedImagePath = attachedImagePath
        )

        val assistantMessage = ConversationMessage(
            role = "assistant",
            text = if (state.mode == AppMode.IMAGE) {
                "正在生成图片……"
            } else {
                ""
            },
            generating = true
        )

        appendMessages(
            userMessage,
            assistantMessage
        )

        updateConversationTitleIfNeeded(text)

        if (state.mode == AppMode.CHAT) {
            sendChat(
                assistantMessageId = assistantMessage.id
            )
        } else {
            sendImage(
                prompt = text,
                sourceImagePath = attachedImagePath,
                assistantMessageId = assistantMessage.id
            )
        }
    }

    private fun sendChat(
        assistantMessageId: String
    ) {
        val state = _uiState.value
        val current = state.currentConversation
            ?: return

        val requestMessages = current.messages
            .filter {
                !it.generating &&
                    it.error == null &&
                    it.text.isNotBlank()
            }
            .takeLast(state.settings.contextMessages)

        _uiState.value = state.copy(
            loading = true,
            error = null
        )

        requestJob = viewModelScope.launch {
            try {
                val result = apiClient.chat(
                    settings = state.settings,
                    messages = requestMessages
                ) { fragment ->
                    updateMessage(
                        assistantMessageId
                    ) { old ->
                        old.copy(
                            text = old.text + fragment
                        )
                    }
                }

                result.onSuccess {
                    updateMessage(
                        assistantMessageId
                    ) {
                        it.copy(
                            generating = false
                        )
                    }
                }

                result.onFailure { exception ->
                    updateMessage(
                        assistantMessageId
                    ) {
                        it.copy(
                            generating = false,
                            error = exception.message
                                ?: "聊天请求失败"
                        )
                    }
                }
            } catch (_: CancellationException) {
                updateMessage(
                    assistantMessageId
                ) {
                    it.copy(
                        generating = false
                    )
                }
            } finally {
                _uiState.value = _uiState.value.copy(
                    loading = false
                )

                persist()
            }
        }
    }

    private fun sendImage(
        prompt: String,
        sourceImagePath: String?,
        assistantMessageId: String
    ) {
        val state = _uiState.value

        val options = parseImageOptions(
            prompt = prompt,
            settings = state.settings
        )

        val sourceFile = sourceImagePath
            ?.let(::File)
            ?.takeIf {
                it.exists()
            }

        _uiState.value = state.copy(
            loading = true,
            error = null
        )

        requestJob = viewModelScope.launch {
            try {
                val result = apiClient.generateImage(
                    settings = state.settings,
                    options = options,
                    prompt = prompt,
                    sourceImage = sourceFile
                )

                result.onSuccess { paths ->
                    updateMessage(
                        assistantMessageId
                    ) {
                        it.copy(
                            text = if (sourceFile == null) {
                                "图片生成完成"
                            } else {
                                "图片编辑完成"
                            },
                            imagePaths = paths,
                            generating = false,
                            error = null
                        )
                    }
                }

                result.onFailure { exception ->
                    updateMessage(
                        assistantMessageId
                    ) {
                        it.copy(
                            text = "",
                            generating = false,
                            error = exception.message
                                ?: "图片生成失败"
                        )
                    }
                }
            } catch (_: CancellationException) {
                updateMessage(
                    assistantMessageId
                ) {
                    it.copy(
                        generating = false,
                        text = "已停止生成"
                    )
                }
            } finally {
                _uiState.value = _uiState.value.copy(
                    loading = false
                )

                persist()
            }
        }
    }

    private fun parseImageOptions(
        prompt: String,
        settings: ChatSettings
    ): ImageRequestOptions {
        var size = settings.imageSize
        var quality = settings.imageQuality
        var count = settings.imageCount

        if (settings.smartImageParameters) {
            when {
                prompt.contains("16:9") ||
                    prompt.contains("横屏") ||
                    prompt.contains("宽屏") -> {
                    size = "1792x1024"
                }

                prompt.contains("9:16") ||
                    prompt.contains("竖屏") -> {
                    size = "1024x1792"
                }

                prompt.contains("1:1") ||
                    prompt.contains("正方形") -> {
                    size = "1024x1024"
                }
            }

            if (
                prompt.contains("高清") ||
                prompt.contains("高质量") ||
                prompt.contains("high", ignoreCase = true)
            ) {
                quality = "high"
            }

            val digitCount = Regex(
                """(\d+)\s*张"""
            ).find(prompt)
                ?.groupValues
                ?.getOrNull(1)
                ?.toIntOrNull()

            val chineseCount = when {
                prompt.contains("两张") ||
                    prompt.contains("二张") -> 2

                prompt.contains("三张") -> 3
                prompt.contains("四张") -> 4
                else -> null
            }

            count = (
                digitCount ?: chineseCount ?: count
                ).coerceIn(1, 10)
        }

        return ImageRequestOptions(
            size = size,
            quality = quality,
            count = count,
            responseFormat = settings.imageResponseFormat
        )
    }

    private fun validateSettings(
        state: UiState
    ): Boolean {
        if (state.settings.baseUrl.isBlank()) {
            _uiState.value = state.copy(
                error = "请先填写 API Base URL"
            )
            return false
        }

        if (state.settings.apiKey.isBlank()) {
            _uiState.value = state.copy(
                error = "请先填写 API Key"
            )
            return false
        }

        if (
            state.mode == AppMode.CHAT &&
            state.settings.chatModel.isBlank()
        ) {
            _uiState.value = state.copy(
                error = "请先填写聊天模型名称"
            )
            return false
        }

        return true
    }

    private fun appendMessages(
        vararg messages: ConversationMessage
    ) {
        updateCurrentConversation { conversation ->
            conversation.copy(
                messages = conversation.messages +
                    messages.toList(),
                updatedAt = System.currentTimeMillis()
            )
        }

        persist()
    }

    private fun updateMessage(
        messageId: String,
        update: (ConversationMessage) -> ConversationMessage
    ) {
        updateCurrentConversation { conversation ->
            conversation.copy(
                messages = conversation.messages.map {
                    if (it.id == messageId) {
                        update(it)
                    } else {
                        it
                    }
                },
                updatedAt = System.currentTimeMillis()
            )
        }
    }

    private fun updateConversationTitleIfNeeded(
        prompt: String
    ) {
        updateCurrentConversation { conversation ->
            if (
                conversation.title == "新对话" &&
                conversation.messages.size <= 2
            ) {
                conversation.copy(
                    title = prompt
                        .replace("\n", " ")
                        .take(28)
                )
            } else {
                conversation
            }
        }

        persist()
    }

    private fun updateCurrentConversation(
        update: (Conversation) -> Conversation
    ) {
        val state = _uiState.value

        val updated = state.conversations.map {
            if (it.id == state.currentConversationId) {
                update(it)
            } else {
                it
            }
        }.sortedByDescending {
            it.updatedAt
        }

        _uiState.value = state.copy(
            conversations = updated
        )
    }

    private fun updateGeneratingMessageAfterStop() {
        updateCurrentConversation { conversation ->
            conversation.copy(
                messages = conversation.messages.map {
                    if (it.generating) {
                        it.copy(
                            generating = false,
                            text = it.text.ifBlank {
                                "已停止生成"
                            }
                        )
                    } else {
                        it
                    }
                }
            )
        }
    }

    private fun persist() {
        conversationStore.save(
            _uiState.value.conversations
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatImageApp(
    viewModel: MainViewModel
) {
    val state by viewModel.uiState.collectAsState()

    var showSettings by remember {
        mutableStateOf(false)
    }

    var showHistory by remember {
        mutableStateOf(false)
    }

    var showModeMenu by remember {
        mutableStateOf(false)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            state.currentConversation
                                ?.title
                                ?: "ChatImage"
                        )

                        Text(
                            text = if (
                                state.mode == AppMode.CHAT
                            ) {
                                state.settings.chatModel
                            } else {
                                state.settings.imageModel
                            },
                            style = MaterialTheme
                                .typography
                                .bodySmall
                        )
                    }
                },
                actions = {
                    Box {
                        TextButton(
                            onClick = {
                                showModeMenu = true
                            }
                        ) {
                            Text(
                                if (
                                    state.mode == AppMode.CHAT
                                ) {
                                    "聊天"
                                } else {
                                    "生图"
                                }
                            )
                        }

                        DropdownMenu(
                            expanded = showModeMenu,
                            onDismissRequest = {
                                showModeMenu = false
                            }
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text("聊天模型")
                                        Text(
                                            state.settings.chatModel,
                                            style = MaterialTheme
                                                .typography
                                                .bodySmall
                                        )
                                    }
                                },
                                onClick = {
                                    viewModel.setMode(
                                        AppMode.CHAT
                                    )
                                    showModeMenu = false
                                }
                            )

                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text("生图模型")
                                        Text(
                                            state.settings.imageModel,
                                            style = MaterialTheme
                                                .typography
                                                .bodySmall
                                        )
                                    }
                                },
                                onClick = {
                                    viewModel.setMode(
                                        AppMode.IMAGE
                                    )
                                    showModeMenu = false
                                }
                            )
                        }
                    }

                    IconButton(
                        onClick = {
                            showHistory = true
                        }
                    ) {
                        Icon(
                            Icons.Default.History,
                            contentDescription = "历史对话"
                        )
                    }

                    IconButton(
                        onClick = viewModel::newConversation
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "新建对话"
                        )
                    }

                    IconButton(
                        onClick = {
                            showSettings = true
                        }
                    ) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = "设置"
                        )
                    }
                }
            )
        }
    ) { padding ->
        ConversationPage(
            state = state,
            viewModel = viewModel,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        )
    }

    if (showSettings) {
        SettingsDialog(
            settings = state.settings,
            onSave = {
                viewModel.saveSettings(it)
                showSettings = false
            },
            onDismiss = {
                showSettings = false
            }
        )
    }

    if (showHistory) {
        HistoryDialog(
            conversations = state.conversations,
            currentId = state.currentConversationId,
            onSelect = {
                viewModel.selectConversation(it)
                showHistory = false
            },
            onDelete = viewModel::deleteConversation,
            onDismiss = {
                showHistory = false
            }
        )
    }

    state.error?.let { message ->
        AlertDialog(
            onDismissRequest = viewModel::clearError,
            title = {
                Text("错误")
            },
            text = {
                Text(message)
            },
            confirmButton = {
                Button(
                    onClick = viewModel::clearError
                ) {
                    Text("确定")
                }
            }
        )
    }
}

@Composable
fun ConversationPage(
    state: UiState,
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    val messages = state.currentConversation
        ?.messages
        .orEmpty()

    var input by remember {
        mutableStateOf("")
    }

    var attachedImagePath by remember {
        mutableStateOf<String?>(null)
    }

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                try {
                    attachedImagePath = withContext(
                        Dispatchers.IO
                    ) {
                        copyAttachmentToFiles(
                            context,
                            uri
                        ).absolutePath
                    }
                } catch (exception: Exception) {
                    Toast.makeText(
                        context,
                        exception.message ?: "无法读取图片",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    LaunchedEffect(
        messages.size,
        messages.lastOrNull()?.text,
        messages.lastOrNull()?.imagePaths?.size
    ) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(
                messages.lastIndex
            )
        }
    }

    Column(
        modifier = modifier
    ) {
        if (messages.isEmpty()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Default.Image,
                    contentDescription = null
                )

                Spacer(
                    modifier = Modifier.height(12.dp)
                )

                Text(
                    "开始新的对话",
                    style = MaterialTheme.typography.headlineSmall
                )

                Spacer(
                    modifier = Modifier.height(8.dp)
                )

                Text(
                    if (state.mode == AppMode.CHAT) {
                        "当前为聊天模型，可在顶部切换为生图模型。"
                    } else {
                        "当前为生图模型，可以文生图，也可以附加图片进行图生图。"
                    }
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp),
                verticalArrangement = Arrangement.spacedBy(
                    10.dp
                )
            ) {
                items(
                    items = messages,
                    key = {
                        it.id
                    }
                ) { message ->
                    MessageCard(message)
                }
            }
        }

        if (state.mode == AppMode.IMAGE) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = 10.dp,
                        vertical = 4.dp
                    ),
                horizontalArrangement = Arrangement.spacedBy(
                    6.dp
                )
            ) {
                AssistChip(
                    onClick = {},
                    label = {
                        Text(state.settings.imageSize)
                    }
                )

                AssistChip(
                    onClick = {},
                    label = {
                        Text(state.settings.imageQuality)
                    }
                )

                AssistChip(
                    onClick = {},
                    label = {
                        Text("${state.settings.imageCount} 张")
                    }
                )
            }
        }

        attachedImagePath?.let { path ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp)
            ) {
                Row(
                    modifier = Modifier.padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AsyncImage(
                        model = File(path),
                        contentDescription = "待上传图片",
                        modifier = Modifier
                            .width(72.dp)
                            .height(72.dp),
                        contentScale = ContentScale.Crop
                    )

                    Spacer(
                        modifier = Modifier.width(8.dp)
                    )

                    Text(
                        "已附加图片，将使用图生图",
                        modifier = Modifier.weight(1f)
                    )

                    TextButton(
                        onClick = {
                            attachedImagePath = null
                        }
                    ) {
                        Text("移除")
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            if (state.mode == AppMode.IMAGE) {
                IconButton(
                    onClick = {
                        imagePicker.launch("image/*")
                    },
                    enabled = !state.loading
                ) {
                    Icon(
                        Icons.Default.AttachFile,
                        contentDescription = "选择图片"
                    )
                }
            }

            OutlinedTextField(
                value = input,
                onValueChange = {
                    input = it
                },
                modifier = Modifier.weight(1f),
                placeholder = {
                    Text(
                        if (state.mode == AppMode.CHAT) {
                            "输入消息"
                        } else if (
                            attachedImagePath == null
                        ) {
                            "描述想生成的图片"
                        } else {
                            "描述如何修改这张图片"
                        }
                    )
                },
                minLines = 1,
                maxLines = 5
            )

            Spacer(
                modifier = Modifier.width(6.dp)
            )

            if (state.loading) {
                IconButton(
                    onClick = viewModel::stop
                ) {
                    Icon(
                        Icons.Default.Stop,
                        contentDescription = "停止"
                    )
                }
            } else {
                IconButton(
                    onClick = {
                        if (input.isNotBlank()) {
                            viewModel.send(
                                prompt = input,
                                attachedImagePath =
                                    attachedImagePath
                            )

                            input = ""
                            attachedImagePath = null
                        }
                    }
                ) {
                    Icon(
                        Icons.Default.Send,
                        contentDescription = "发送"
                    )
                }
            }
        }
    }
}

@Composable
fun MessageCard(
    message: ConversationMessage
) {
    val context = LocalContext.current
    val isUser = message.role == "user"

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) {
            Arrangement.End
        } else {
            Arrangement.Start
        }
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(
                if (isUser) 0.88f else 0.96f
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(
                    8.dp
                )
            ) {
                Text(
                    if (isUser) "你" else "助手",
                    style = MaterialTheme.typography.labelMedium
                )

                message.attachedImagePath?.let { path ->
                    val file = File(path)

                    if (file.exists()) {
                        AsyncImage(
                            model = file,
                            contentDescription = "附加图片",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp),
                            contentScale = ContentScale.Fit
                        )
                    }
                }

                if (message.text.isNotBlank()) {
                    Text(message.text)
                }

                if (
                    message.generating &&
                    message.text.isBlank()
                ) {
                    Text("正在生成……")
                }

                message.imagePaths.forEach { path ->
                    val file = File(path)

                    if (file.exists()) {
                        AsyncImage(
                            model = file,
                            contentDescription = "生成图片",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(340.dp),
                            contentScale = ContentScale.Fit
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(
                                onClick = {
                                    val success = saveImageToGallery(
                                        context,
                                        file
                                    )

                                    Toast.makeText(
                                        context,
                                        if (success) {
                                            "图片已保存到相册"
                                        } else {
                                            "保存失败；Android 10 以下请使用分享功能"
                                        },
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            ) {
                                Icon(
                                    Icons.Default.Save,
                                    contentDescription = null
                                )

                                Spacer(
                                    modifier = Modifier.width(4.dp)
                                )

                                Text("保存")
                            }

                            TextButton(
                                onClick = {
                                    shareImage(
                                        context,
                                        file
                                    )
                                }
                            ) {
                                Icon(
                                    Icons.Default.Share,
                                    contentDescription = null
                                )

                                Spacer(
                                    modifier = Modifier.width(4.dp)
                                )

                                Text("分享")
                            }
                        }
                    } else {
                        Text("本地图片文件已不存在")
                    }
                }

                message.error?.let { error ->
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
fun HistoryDialog(
    conversations: List<Conversation>,
    currentId: String,
    onSelect: (String) -> Unit,
    onDelete: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("历史对话")
        },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(480.dp),
                verticalArrangement = Arrangement.spacedBy(
                    4.dp
                )
            ) {
                items(
                    items = conversations,
                    key = {
                        it.id
                    }
                ) { conversation ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = {
                                onSelect(conversation.id)
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    conversation.title,
                                    color = if (
                                        conversation.id == currentId
                                    ) {
                                        MaterialTheme
                                            .colorScheme
                                            .primary
                                    } else {
                                        MaterialTheme
                                            .colorScheme
                                            .onSurface
                                    }
                                )

                                Text(
                                    "${conversation.messages.size} 条消息",
                                    style = MaterialTheme
                                        .typography
                                        .bodySmall
                                )
                            }
                        }

                        IconButton(
                            onClick = {
                                onDelete(conversation.id)
                            }
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "删除"
                            )
                        }
                    }

                    HorizontalDivider()
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss
            ) {
                Text("关闭")
            }
        }
    )
}

@Composable
fun SettingsDialog(
    settings: ChatSettings,
    onSave: (ChatSettings) -> Unit,
    onDismiss: () -> Unit
) {
    var baseUrl by remember(settings) {
        mutableStateOf(settings.baseUrl)
    }

    var apiKey by remember(settings) {
        mutableStateOf(settings.apiKey)
    }

    var chatModel by remember(settings) {
        mutableStateOf(settings.chatModel)
    }

    var imageModel by remember(settings) {
        mutableStateOf(settings.imageModel)
    }

    var stream by remember(settings) {
        mutableStateOf(settings.stream)
    }

    var temperature by remember(settings) {
        mutableStateOf(settings.temperature.toString())
    }

    var maxTokens by remember(settings) {
        mutableStateOf(settings.maxTokens.toString())
    }

    var contextMessages by remember(settings) {
        mutableStateOf(settings.contextMessages.toString())
    }

    var imageSize by remember(settings) {
        mutableStateOf(settings.imageSize)
    }

    var imageQuality by remember(settings) {
        mutableStateOf(settings.imageQuality)
    }

    var imageCount by remember(settings) {
        mutableStateOf(settings.imageCount.toString())
    }

    var imageResponseFormat by remember(settings) {
        mutableStateOf(settings.imageResponseFormat)
    }

    var smartImageParameters by remember(settings) {
        mutableStateOf(settings.smartImageParameters)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("API、模型和参数设置")
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(560.dp)
                    .verticalScroll(
                        rememberScrollState()
                    ),
                verticalArrangement = Arrangement.spacedBy(
                    8.dp
                )
            ) {
                Text(
                    "API 设置",
                    style = MaterialTheme.typography.titleMedium
                )

                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = {
                        baseUrl = it
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = {
                        Text("API Base URL")
                    },
                    supportingText = {
                        Text("例如：https://example.com/v1")
                    }
                )

                OutlinedTextField(
                    value = apiKey,
                    onValueChange = {
                        apiKey = it
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = {
                        Text("API Key")
                    },
                    visualTransformation =
                        PasswordVisualTransformation()
                )

                OutlinedTextField(
                    value = chatModel,
                    onValueChange = {
                        chatModel = it
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = {
                        Text("聊天模型")
                    }
                )

                OutlinedTextField(
                    value = imageModel,
                    onValueChange = {
                        imageModel = it
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = {
                        Text("生图模型")
                    }
                )

                HorizontalDivider()

                Text(
                    "语言模型参数",
                    style = MaterialTheme.typography.titleMedium
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("聊天流式输出")
                        Text(
                            "该开关只影响聊天模型，不影响图片模型",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    Switch(
                        checked = stream,
                        onCheckedChange = {
                            stream = it
                        }
                    )
                }

                OutlinedTextField(
                    value = temperature,
                    onValueChange = {
                        temperature = it
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = {
                        Text("Temperature")
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal
                    )
                )

                OutlinedTextField(
                    value = maxTokens,
                    onValueChange = {
                        maxTokens = it.filter(Char::isDigit)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = {
                        Text("单次回复最大 Tokens")
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number
                    )
                )

                OutlinedTextField(
                    value = contextMessages,
                    onValueChange = {
                        contextMessages =
                            it.filter(Char::isDigit)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = {
                        Text("上下文消息数量")
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number
                    )
                )

                HorizontalDivider()

                Text(
                    "图片模型参数",
                    style = MaterialTheme.typography.titleMedium
                )

                OutlinedTextField(
                    value = imageSize,
                    onValueChange = {
                        imageSize = it
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = {
                        Text("默认尺寸")
                    },
                    supportingText = {
                        Text("例如：1024x1024")
                    }
                )

                OutlinedTextField(
                    value = imageQuality,
                    onValueChange = {
                        imageQuality = it
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = {
                        Text("默认质量")
                    },
                    supportingText = {
                        Text("例如：standard 或 high")
                    }
                )

                OutlinedTextField(
                    value = imageCount,
                    onValueChange = {
                        imageCount =
                            it.filter(Char::isDigit)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = {
                        Text("默认生成数量")
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number
                    )
                )

                OutlinedTextField(
                    value = imageResponseFormat,
                    onValueChange = {
                        imageResponseFormat = it
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = {
                        Text("图片返回格式")
                    },
                    supportingText = {
                        Text("填写 url 或 b64_json")
                    }
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("自然语言识别图片参数")
                        Text(
                            "识别“两张、16:9、竖屏、高清”等表达",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    Switch(
                        checked = smartImageParameters,
                        onCheckedChange = {
                            smartImageParameters = it
                        }
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        ChatSettings(
                            baseUrl = baseUrl
                                .trim()
                                .trimEnd('/'),
                            apiKey = apiKey.trim(),
                            chatModel = chatModel.trim(),
                            imageModel = imageModel.trim(),
                            stream = stream,
                            temperature = temperature
                                .toFloatOrNull()
                                ?.coerceIn(0f, 2f)
                                ?: 0.7f,
                            maxTokens = maxTokens
                                .toIntOrNull()
                                ?.coerceIn(1, 100000)
                                ?: 2048,
                            contextMessages = contextMessages
                                .toIntOrNull()
                                ?.coerceIn(1, 200)
                                ?: 20,
                            imageSize = imageSize
                                .trim()
                                .ifBlank {
                                    "1024x1024"
                                },
                            imageQuality = imageQuality
                                .trim()
                                .ifBlank {
                                    "standard"
                                },
                            imageCount = imageCount
                                .toIntOrNull()
                                ?.coerceIn(1, 10)
                                ?: 1,
                            imageResponseFormat =
                                imageResponseFormat
                                    .trim()
                                    .ifBlank {
                                        "url"
                                    },
                            smartImageParameters =
                                smartImageParameters
                        )
                    )
                }
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss
            ) {
                Text("取消")
            }
        }
    )
}

private fun copyAttachmentToFiles(
    context: Context,
    uri: Uri
): File {
    val mimeType = context
        .contentResolver
        .getType(uri)
        .orEmpty()

    val extension = when {
        mimeType.contains("jpeg") -> "jpg"
        mimeType.contains("webp") -> "webp"
        else -> "png"
    }

    val directory = File(
        context.filesDir,
        "attachments"
    )

    directory.mkdirs()

    val file = File(
        directory,
        "attachment_${System.currentTimeMillis()}_" +
            "${UUID.randomUUID()}.$extension"
    )

    val input = context.contentResolver
        .openInputStream(uri)
        ?: throw Exception("无法读取选择的图片")

    input.use { source ->
        file.outputStream().use { destination ->
            source.copyTo(destination)
        }
    }

    return file
}

private fun saveImageToGallery(
    context: Context,
    sourceFile: File
): Boolean {
    if (!sourceFile.exists()) {
        return false
    }

    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
        return false
    }

    return try {
        val mimeType = when (
            sourceFile.extension.lowercase()
        ) {
            "jpg", "jpeg" -> "image/jpeg"
            "webp" -> "image/webp"
            else -> "image/png"
        }

        val values = ContentValues().apply {
            put(
                MediaStore.Images.Media.DISPLAY_NAME,
                "ChatImage_${System.currentTimeMillis()}." +
                    sourceFile.extension
            )
            put(
                MediaStore.Images.Media.MIME_TYPE,
                mimeType
            )
            put(
                MediaStore.Images.Media.RELATIVE_PATH,
                "Pictures/ChatImage"
            )
            put(
                MediaStore.Images.Media.IS_PENDING,
                1
            )
        }

        val uri = context.contentResolver.insert(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            values
        ) ?: return false

        context.contentResolver
            .openOutputStream(uri)
            ?.use { output ->
                sourceFile.inputStream().use { input ->
                    input.copyTo(output)
                }
            } ?: return false

        values.clear()
        values.put(
            MediaStore.Images.Media.IS_PENDING,
            0
        )

        context.contentResolver.update(
            uri,
            values,
            null,
            null
        )

        true
    } catch (_: Exception) {
        false
    }
}

private fun shareImage(
    context: Context,
    file: File
) {
    if (!file.exists()) {
        Toast.makeText(
            context,
            "图片文件不存在",
            Toast.LENGTH_SHORT
        ).show()
        return
    }

    try {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val mimeType = when (
            file.extension.lowercase()
        ) {
            "jpg", "jpeg" -> "image/jpeg"
            "webp" -> "image/webp"
            else -> "image/png"
        }

        val intent = Intent(
            Intent.ACTION_SEND
        ).apply {
            type = mimeType
            putExtra(
                Intent.EXTRA_STREAM,
                uri
            )
            addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }

        context.startActivity(
            Intent.createChooser(
                intent,
                "分享图片"
            )
        )
    } catch (exception: Exception) {
        Toast.makeText(
            context,
            exception.message ?: "无法分享图片",
            Toast.LENGTH_LONG
        ).show()
    }
}
