package com.example.chatimage

import android.app.Application
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import coil.compose.AsyncImage
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

data class ChatMessage(
    val role: String,
    val content: String
)

data class ChatSettings(
    val baseUrl: String = "https://api.example.com/v1",
    val apiKey: String = "",
    val chatModel: String = "gpt-4o",
    val imageModel: String = "image-2",
    val stream: Boolean = true,
    val temperature: Float = 0.7f,
    val maxTokens: Int = 2048,
    val contextMessages: Int = 20
)

data class ImageSettings(
    val size: String = "1024x1024",
    val quality: String = "standard",
    val count: Int = 1,
    val responseFormat: String = "url"
)

data class ImageResult(
    val url: String? = null,
    val base64: String? = null
)

data class UiState(
    val settings: ChatSettings = ChatSettings(),
    val messages: List<ChatMessage> = emptyList(),
    val images: List<ImageResult> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null
)

class SettingsStore(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val preferences = EncryptedSharedPreferences.create(
        context,
        "chatimage_secure_settings",
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
            .apply()
    }
}

class ApiClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .writeTimeout(180, TimeUnit.SECONDS)
        .build()

    private fun endpoint(baseUrl: String, path: String): String {
        return baseUrl.trimEnd('/') + "/" + path.trimStart('/')
    }

    suspend fun chat(
        settings: ChatSettings,
        messages: List<ChatMessage>,
        onFragment: suspend (String) -> Unit
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val messageArray = JSONArray()

            messages.forEach { message ->
                messageArray.put(
                    JSONObject()
                        .put("role", message.role)
                        .put("content", message.content)
                )
            }

            val requestJson = JSONObject()
                .put("model", settings.chatModel)
                .put("messages", messageArray)
                .put("temperature", settings.temperature.toDouble())
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
                .addHeader(
                    "Content-Type",
                    "application/json"
                )

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

            val request = requestBuilder
                .post(requestBody)
                .build()

            client.newCall(request).execute().use { response ->
                val responseBody = response.body
                    ?: return@withContext Result.failure(
                        Exception("接口返回内容为空")
                    )

                if (!response.isSuccessful) {
                    val errorText = responseBody.string().take(3000)

                    return@withContext Result.failure(
                        Exception(
                            "聊天请求失败：HTTP ${response.code}\n$errorText"
                        )
                    )
                }

                if (!settings.stream) {
                    val responseText = responseBody.string()
                    val root = JSONObject(responseText)

                    val content = root
                        .optJSONArray("choices")
                        ?.optJSONObject(0)
                        ?.optJSONObject("message")
                        ?.optString("content")
                        .orEmpty()

                    if (content.isBlank()) {
                        return@withContext Result.failure(
                            Exception(
                                "接口成功返回，但没有找到 choices[0].message.content"
                            )
                        )
                    }

                    withContext(Dispatchers.Main) {
                        onFragment(content)
                    }

                    return@withContext Result.success(Unit)
                }

                val source = responseBody.source()
                var receivedContent = false

                while (!source.exhausted()) {
                    val line = source.readUtf8Line() ?: continue
                    val trimmedLine = line.trim()

                    if (trimmedLine.isBlank()) {
                        continue
                    }

                    if (!trimmedLine.startsWith("data:")) {
                        continue
                    }

                    val data = trimmedLine
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

                        val deltaContent = choice
                            ?.optJSONObject("delta")
                            ?.optString("content")
                            .orEmpty()

                        val messageContent = choice
                            ?.optJSONObject("message")
                            ?.optString("content")
                            .orEmpty()

                        val text = when {
                            deltaContent.isNotEmpty() -> deltaContent
                            messageContent.isNotEmpty() -> messageContent
                            else -> ""
                        }

                        if (text.isNotEmpty()) {
                            receivedContent = true

                            withContext(Dispatchers.Main) {
                                onFragment(text)
                            }
                        }
                    } catch (_: Exception) {
                        // 忽略无法解析的 SSE 行
                    }
                }

                if (!receivedContent) {
                    return@withContext Result.failure(
                        Exception(
                            "接口返回了流式响应，但没有解析到文本。请尝试关闭“流式输出”。"
                        )
                    )
                }
            }

            Result.success(Unit)
        } catch (exception: Exception) {
            Result.failure(exception)
        }
    }

    suspend fun generateImage(
        settings: ChatSettings,
        imageSettings: ImageSettings,
        prompt: String
    ): Result<List<ImageResult>> = withContext(Dispatchers.IO) {
        try {
            val requestJson = JSONObject()
                .put("model", settings.imageModel)
                .put("prompt", prompt)
                .put("n", imageSettings.count)
                .put("size", imageSettings.size)
                .put("quality", imageSettings.quality)
                .put(
                    "response_format",
                    imageSettings.responseFormat
                )

            val requestBody = requestJson
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
                    "Content-Type",
                    "application/json"
                )
                .post(requestBody)
                .build()

            client.newCall(request).execute().use { response ->
                val responseText = response.body
                    ?.string()
                    .orEmpty()

                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        Exception(
                            "文生图请求失败：HTTP ${response.code}\n" +
                                responseText.take(3000)
                        )
                    )
                }

                val images = parseImageResults(responseText)

                if (images.isEmpty()) {
                    return@withContext Result.failure(
                        Exception("接口成功返回，但没有找到图片数据")
                    )
                }

                Result.success(images)
            }
        } catch (exception: Exception) {
            Result.failure(exception)
        }
    }

    suspend fun editImage(
        settings: ChatSettings,
        imageSettings: ImageSettings,
        prompt: String,
        imageFile: File
    ): Result<List<ImageResult>> = withContext(Dispatchers.IO) {
        try {
            val imageBody = imageFile.asRequestBody(
                "image/png".toMediaType()
            )

            val multipartBody = MultipartBody.Builder()
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
                    "size",
                    imageSettings.size
                )
                .addFormDataPart(
                    "quality",
                    imageSettings.quality
                )
                .addFormDataPart(
                    "n",
                    imageSettings.count.toString()
                )
                .addFormDataPart(
                    "response_format",
                    imageSettings.responseFormat
                )
                .addFormDataPart(
                    "image",
                    imageFile.name,
                    imageBody
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
                .post(multipartBody)
                .build()

            client.newCall(request).execute().use { response ->
                val responseText = response.body
                    ?.string()
                    .orEmpty()

                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        Exception(
                            "图生图请求失败：HTTP ${response.code}\n" +
                                responseText.take(3000)
                        )
                    )
                }

                val images = parseImageResults(responseText)

                if (images.isEmpty()) {
                    return@withContext Result.failure(
                        Exception("接口成功返回，但没有找到图片数据")
                    )
                }

                Result.success(images)
            }
        } catch (exception: Exception) {
            Result.failure(exception)
        }
    }

    private fun parseImageResults(
        responseText: String
    ): List<ImageResult> {
        val root = JSONObject(responseText)

        val data = root.optJSONArray("data")
            ?: throw Exception(
                "图片接口没有返回 data 数组：${responseText.take(1000)}"
            )

        return buildList {
            for (index in 0 until data.length()) {
                val item = data.optJSONObject(index)
                    ?: continue

                val imageUrl = item
                    .optString("url")
                    .takeIf { it.isNotBlank() }

                val base64Image = item
                    .optString("b64_json")
                    .takeIf { it.isNotBlank() }

                if (imageUrl != null || base64Image != null) {
                    add(
                        ImageResult(
                            url = imageUrl,
                            base64 = base64Image
                        )
                    )
                }
            }
        }
    }
}

class MainViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val settingsStore = SettingsStore(application)
    private val apiClient = ApiClient()

    private val _uiState = MutableStateFlow(
        UiState(
            settings = settingsStore.load()
        )
    )

    val uiState = _uiState.asStateFlow()

    private var requestJob: Job? = null

    fun saveSettings(settings: ChatSettings) {
        val normalizedSettings = settings.copy(
            baseUrl = settings.baseUrl.trimEnd('/'),
            apiKey = settings.apiKey.trim(),
            chatModel = settings.chatModel.trim(),
            imageModel = settings.imageModel.trim()
        )

        settingsStore.save(normalizedSettings)

        _uiState.value = _uiState.value.copy(
            settings = normalizedSettings
        )
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(
            error = null
        )
    }

    fun newConversation() {
        if (_uiState.value.loading) {
            stop()
        }

        _uiState.value = _uiState.value.copy(
            messages = emptyList(),
            error = null
        )
    }

    fun stop() {
        requestJob?.cancel()
        requestJob = null

        _uiState.value = _uiState.value.copy(
            loading = false
        )
    }

    fun sendMessage(text: String) {
        val prompt = text.trim()
        val initialState = _uiState.value

        if (prompt.isBlank() || initialState.loading) {
            return
        }

        if (initialState.settings.baseUrl.isBlank()) {
            _uiState.value = initialState.copy(
                error = "请先在设置中填写 API Base URL"
            )
            return
        }

        if (initialState.settings.apiKey.isBlank()) {
            _uiState.value = initialState.copy(
                error = "请先在设置中填写 API Key"
            )
            return
        }

        if (initialState.settings.chatModel.isBlank()) {
            _uiState.value = initialState.copy(
                error = "请先在设置中填写聊天模型名称"
            )
            return
        }

        val userMessage = ChatMessage(
            role = "user",
            content = prompt
        )

        val assistantMessage = ChatMessage(
            role = "assistant",
            content = ""
        )

        val updatedMessages = initialState.messages +
            userMessage +
            assistantMessage

        val requestMessages = (
            initialState.messages + userMessage
            )
            .takeLast(
                initialState.settings.contextMessages
            )

        _uiState.value = initialState.copy(
            messages = updatedMessages,
            loading = true,
            error = null
        )

        requestJob = viewModelScope.launch {
            val result = apiClient.chat(
                settings = initialState.settings,
                messages = requestMessages
            ) { fragment ->
                val currentMessages = _uiState
                    .value
                    .messages
                    .toMutableList()

                if (currentMessages.isNotEmpty()) {
                    val lastIndex = currentMessages.lastIndex
                    val lastMessage = currentMessages[lastIndex]

                    currentMessages[lastIndex] = lastMessage.copy(
                        content = lastMessage.content + fragment
                    )

                    _uiState.value = _uiState.value.copy(
                        messages = currentMessages
                    )
                }
            }

            result.onFailure { exception ->
                val currentMessages = _uiState
                    .value
                    .messages
                    .toMutableList()

                if (
                    currentMessages.isNotEmpty() &&
                    currentMessages.last().role == "assistant" &&
                    currentMessages.last().content.isBlank()
                ) {
                    currentMessages.removeAt(
                        currentMessages.lastIndex
                    )
                }

                _uiState.value = _uiState.value.copy(
                    messages = currentMessages,
                    error = exception.message ?: "聊天请求失败"
                )
            }

            _uiState.value = _uiState.value.copy(
                loading = false
            )
        }
    }

    fun generateImage(
        prompt: String,
        imageSettings: ImageSettings
    ) {
        val cleanPrompt = prompt.trim()
        val initialState = _uiState.value

        if (cleanPrompt.isBlank() || initialState.loading) {
            return
        }

        if (initialState.settings.baseUrl.isBlank()) {
            _uiState.value = initialState.copy(
                error = "请先在设置中填写 API Base URL"
            )
            return
        }

        if (initialState.settings.apiKey.isBlank()) {
            _uiState.value = initialState.copy(
                error = "请先在设置中填写 API Key"
            )
            return
        }

        if (initialState.settings.imageModel.isBlank()) {
            _uiState.value = initialState.copy(
                error = "请先在设置中填写生图模型名称"
            )
            return
        }

        _uiState.value = initialState.copy(
            images = emptyList(),
            loading = true,
            error = null
        )

        requestJob = viewModelScope.launch {
            val result = apiClient.generateImage(
                settings = initialState.settings,
                imageSettings = imageSettings,
                prompt = cleanPrompt
            )

            result.onSuccess { images ->
                _uiState.value = _uiState.value.copy(
                    images = images
                )
            }

            result.onFailure { exception ->
                _uiState.value = _uiState.value.copy(
                    error = exception.message ?: "文生图失败"
                )
            }

            _uiState.value = _uiState.value.copy(
                loading = false
            )
        }
    }

    fun editImage(
        prompt: String,
        imageSettings: ImageSettings,
        imageFile: File
    ) {
        val cleanPrompt = prompt.trim()
        val initialState = _uiState.value

        if (cleanPrompt.isBlank() || initialState.loading) {
            return
        }

        if (!imageFile.exists()) {
            _uiState.value = initialState.copy(
                error = "选择的图片文件不存在，请重新选择"
            )
            return
        }

        if (initialState.settings.baseUrl.isBlank()) {
            _uiState.value = initialState.copy(
                error = "请先在设置中填写 API Base URL"
            )
            return
        }

        if (initialState.settings.apiKey.isBlank()) {
            _uiState.value = initialState.copy(
                error = "请先在设置中填写 API Key"
            )
            return
        }

        if (initialState.settings.imageModel.isBlank()) {
            _uiState.value = initialState.copy(
                error = "请先在设置中填写生图模型名称"
            )
            return
        }

        _uiState.value = initialState.copy(
            images = emptyList(),
            loading = true,
            error = null
        )

        requestJob = viewModelScope.launch {
            val result = apiClient.editImage(
                settings = initialState.settings,
                imageSettings = imageSettings,
                prompt = cleanPrompt,
                imageFile = imageFile
            )

            result.onSuccess { images ->
                _uiState.value = _uiState.value.copy(
                    images = images
                )
            }

            result.onFailure { exception ->
                _uiState.value = _uiState.value.copy(
                    error = exception.message ?: "图生图失败"
                )
            }

            _uiState.value = _uiState.value.copy(
                loading = false
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatImageApp(
    viewModel: MainViewModel
) {
    val state by viewModel.uiState.collectAsState()

    var selectedTab by remember {
        mutableStateOf(0)
    }

    var showSettings by remember {
        mutableStateOf(false)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (selectedTab == 0) {
                            "ChatImage 对话"
                        } else {
                            "ChatImage 生图"
                        }
                    )
                },
                actions = {
                    if (selectedTab == 0) {
                        IconButton(
                            onClick = viewModel::newConversation
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "新建对话"
                            )
                        }
                    }

                    IconButton(
                        onClick = {
                            showSettings = true
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "设置"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            TabRow(
                selectedTabIndex = selectedTab
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = {
                        selectedTab = 0
                    },
                    text = {
                        Text("对话")
                    }
                )

                Tab(
                    selected = selectedTab == 1,
                    onClick = {
                        selectedTab = 1
                    },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Image,
                            contentDescription = null
                        )
                    },
                    text = {
                        Text("生图")
                    }
                )
            }

            if (selectedTab == 0) {
                ChatPage(
                    state = state,
                    viewModel = viewModel
                )
            } else {
                ImagePage(
                    state = state,
                    viewModel = viewModel
                )
            }
        }
    }

    if (showSettings) {
        SettingsDialog(
            settings = state.settings,
            onSave = { settings ->
                viewModel.saveSettings(settings)
                showSettings = false
            },
            onDismiss = {
                showSettings = false
            }
        )
    }

    state.error?.let { errorText ->
        AlertDialog(
            onDismissRequest = viewModel::clearError,
            title = {
                Text("请求错误")
            },
            text = {
                Text(errorText)
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
fun ChatPage(
    state: UiState,
    viewModel: MainViewModel
) {
    var inputText by remember {
        mutableStateOf("")
    }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        if (state.messages.isEmpty()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "开始新的对话",
                    style = MaterialTheme.typography.headlineSmall
                )

                Spacer(
                    modifier = Modifier.height(8.dp)
                )

                Text(
                    text = "请先点击右上角齿轮填写 API 地址、API Key 和模型名称。",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(state.messages) { message ->
                    MessageBubble(message)
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            OutlinedTextField(
                value = inputText,
                onValueChange = {
                    inputText = it
                },
                modifier = Modifier.weight(1f),
                placeholder = {
                    Text("输入消息")
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
                        imageVector = Icons.Default.Stop,
                        contentDescription = "停止生成"
                    )
                }
            } else {
                IconButton(
                    onClick = {
                        if (inputText.isNotBlank()) {
                            viewModel.sendMessage(inputText)
                            inputText = ""
                        }
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = "发送"
                    )
                }
            }
        }
    }
}

@Composable
fun MessageBubble(
    message: ChatMessage
) {
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
            modifier = Modifier.fillMaxWidth(0.90f),
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(
                modifier = Modifier.padding(12.dp)
            ) {
                Text(
                    text = if (isUser) {
                        "你"
                    } else {
                        "助手"
                    },
                    style = MaterialTheme.typography.labelMedium
                )

                Spacer(
                    modifier = Modifier.height(4.dp)
                )

                Text(
                    text = message.content.ifBlank {
                        if (isUser) {
                            ""
                        } else {
                            "正在生成……"
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun ImagePage(
    state: UiState,
    viewModel: MainViewModel
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var prompt by remember {
        mutableStateOf("")
    }

    var size by remember {
        mutableStateOf("1024x1024")
    }

    var quality by remember {
        mutableStateOf("standard")
    }

    var count by remember {
        mutableStateOf("1")
    }

    var responseFormat by remember {
        mutableStateOf("url")
    }

    var selectedFile by remember {
        mutableStateOf<File?>(null)
    }

    var selectedName by remember {
        mutableStateOf("")
    }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                try {
                    val copiedFile = withContext(Dispatchers.IO) {
                        copyUriToCache(
                            context = context,
                            uri = uri
                        )
                    }

                    selectedFile = copiedFile
                    selectedName = copiedFile.name
                } catch (exception: Exception) {
                    selectedFile = null
                    selectedName = ""
                }
            }
        }
    }

    val imageSettings = ImageSettings(
        size = size.trim().ifBlank {
            "1024x1024"
        },
        quality = quality.trim().ifBlank {
            "standard"
        },
        count = count
            .toIntOrNull()
            ?.coerceIn(1, 10)
            ?: 1,
        responseFormat = responseFormat.trim().ifBlank {
            "url"
        }
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(
                rememberScrollState()
            )
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "文生图 / 图生图",
            style = MaterialTheme.typography.titleLarge
        )

        Text(
            text = "图生图默认调用 /images/edits，并使用 multipart/form-data 上传图片。",
            style = MaterialTheme.typography.bodySmall
        )

        OutlinedTextField(
            value = prompt,
            onValueChange = {
                prompt = it
            },
            modifier = Modifier.fillMaxWidth(),
            label = {
                Text("提示词")
            },
            minLines = 4,
            maxLines = 10
        )

        OutlinedTextField(
            value = size,
            onValueChange = {
                size = it
            },
            modifier = Modifier.fillMaxWidth(),
            label = {
                Text("图片尺寸")
            },
            supportingText = {
                Text("例如：1024x1024")
            }
        )

        OutlinedTextField(
            value = quality,
            onValueChange = {
                quality = it
            },
            modifier = Modifier.fillMaxWidth(),
            label = {
                Text("图片质量")
            },
            supportingText = {
                Text("例如：standard 或 high")
            }
        )

        OutlinedTextField(
            value = count,
            onValueChange = {
                count = it.filter(Char::isDigit)
            },
            modifier = Modifier.fillMaxWidth(),
            label = {
                Text("生成数量")
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number
            )
        )

        OutlinedTextField(
            value = responseFormat,
            onValueChange = {
                responseFormat = it
            },
            modifier = Modifier.fillMaxWidth(),
            label = {
                Text("图片返回格式")
            },
            supportingText = {
                Text("一般填写 url，也可以填写 b64_json")
            }
        )

        OutlinedButton(
            onClick = {
                imagePicker.launch("image/*")
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = if (selectedName.isBlank()) {
                    "选择图片用于图生图"
                } else {
                    "已选择：$selectedName"
                }
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    viewModel.generateImage(
                        prompt = prompt,
                        imageSettings = imageSettings
                    )
                },
                modifier = Modifier.weight(1f),
                enabled = !state.loading &&
                    prompt.isNotBlank()
            ) {
                Text("文生图")
            }

            Button(
                onClick = {
                    selectedFile?.let { file ->
                        viewModel.editImage(
                            prompt = prompt,
                            imageSettings = imageSettings,
                            imageFile = file
                        )
                    }
                },
                modifier = Modifier.weight(1f),
                enabled = !state.loading &&
                    prompt.isNotBlank() &&
                    selectedFile != null
            ) {
                Text("图生图")
            }
        }

        if (state.loading) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "正在请求接口，请稍候……",
                    modifier = Modifier.weight(1f)
                )

                OutlinedButton(
                    onClick = viewModel::stop
                ) {
                    Text("停止")
                }
            }
        }

        state.images.forEach { imageResult ->
            GeneratedImage(imageResult)
        }
    }
}

private fun copyUriToCache(
    context: Context,
    uri: Uri
): File {
    val mimeType = context.contentResolver
        .getType(uri)
        .orEmpty()

    val extension = when {
        mimeType.contains("jpeg") -> "jpg"
        mimeType.contains("webp") -> "webp"
        else -> "png"
    }

    val outputFile = File(
        context.cacheDir,
        "source_${System.currentTimeMillis()}.$extension"
    )

    val inputStream = context.contentResolver
        .openInputStream(uri)
        ?: throw Exception("无法读取选择的图片")

    inputStream.use { input ->
        outputFile.outputStream().use { output ->
            input.copyTo(output)
        }
    }

    return outputFile
}

@Composable
fun GeneratedImage(
    imageResult: ImageResult
) {
    if (!imageResult.url.isNullOrBlank()) {
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            AsyncImage(
                model = imageResult.url,
                contentDescription = "生成的图片",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(320.dp),
                contentScale = ContentScale.Fit
            )
        }
    } else if (!imageResult.base64.isNullOrBlank()) {
        val bitmap = remember(imageResult.base64) {
            try {
                val cleanBase64 = imageResult.base64
                    .substringAfter(
                        "base64,",
                        imageResult.base64
                    )

                val bytes = Base64.decode(
                    cleanBase64,
                    Base64.DEFAULT
                )

                BitmapFactory.decodeByteArray(
                    bytes,
                    0,
                    bytes.size
                )
            } catch (_: Exception) {
                null
            }
        }

        if (bitmap != null) {
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "生成的图片",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(320.dp),
                    contentScale = ContentScale.Fit
                )
            }
        } else {
            Text("无法解析接口返回的 Base64 图片")
        }
    }
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
        mutableStateOf(
            settings.temperature.toString()
        )
    }

    var maxTokens by remember(settings) {
        mutableStateOf(
            settings.maxTokens.toString()
        )
    }

    var contextMessages by remember(settings) {
        mutableStateOf(
            settings.contextMessages.toString()
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("API 和模型设置")
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(520.dp)
                    .verticalScroll(
                        rememberScrollState()
                    ),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
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
                    visualTransformation = PasswordVisualTransformation()
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

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("流式输出")

                        Text(
                            text = if (stream) {
                                "逐段显示回复"
                            } else {
                                "等待完整回复后一次显示"
                            },
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
                    supportingText = {
                        Text("范围：0～2")
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
                        contextMessages = it.filter(Char::isDigit)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = {
                        Text("上下文消息数量")
                    },
                    supportingText = {
                        Text("发送最近多少条消息给模型")
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number
                    )
                )
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
                                ?: 20
                        )
                    )
                },
                enabled = baseUrl.isNotBlank() &&
                    chatModel.isNotBlank() &&
                    imageModel.isNotBlank()
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
