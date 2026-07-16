package com.example.chatimage

import android.app.Application
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
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

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "chatimage_settings",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun load(): ChatSettings {
        return ChatSettings(
            baseUrl = prefs.getString(
                "baseUrl",
                "https://api.example.com/v1"
            ).orEmpty(),
            apiKey = prefs.getString("apiKey", "").orEmpty(),
            chatModel = prefs.getString("chatModel", "gpt-4o").orEmpty(),
            imageModel = prefs.getString("imageModel", "image-2").orEmpty(),
            stream = prefs.getBoolean("stream", true),
            temperature = prefs.getFloat("temperature", 0.7f),
            maxTokens = prefs.getInt("maxTokens", 2048),
            contextMessages = prefs.getInt("contextMessages", 20)
        )
    }

    fun save(value: ChatSettings) {
        prefs.edit()
            .putString("baseUrl", value.baseUrl.trimEnd('/'))
            .putString("apiKey", value.apiKey.trim())
            .putString("chatModel", value.chatModel.trim())
            .putString("imageModel", value.imageModel.trim())
            .putBoolean("stream", value.stream)
            .putFloat("temperature", value.temperature)
            .putInt("maxTokens", value.maxTokens)
            .putInt("contextMessages", value.contextMessages)
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
            val array = JSONArray()

            messages.forEach {
                array.put(
                    JSONObject()
                        .put("role", it.role)
                        .put("content", it.content)
                )
            }

            val json = JSONObject()
                .put("model", settings.chatModel)
                .put("messages", array)
                .put("temperature", settings.temperature.toDouble())
                .put("max_tokens", settings.maxTokens)
                .put("stream", settings.stream)

            val body = json.toString()
                .toRequestBody("application/json; charset=utf-8".toMediaType())

            val request = Request.Builder()
                .url(endpoint(settings.baseUrl, "chat/completions"))
                .addHeader("Authorization", "Bearer ${settings.apiKey}")
                .post(body)
                .build()

            client.newCall(request).execute().use { response ->
                val responseBody = response.body
                    ?: return@withContext Result.failure(Exception("接口返回为空"))

                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        Exception(
                            "聊天请求失败：HTTP ${response.code}\n" +
                                responseBody.string().take(2000)
                        )
                    )
                }

                if (!settings.stream) {
                    val root = JSONObject(responseBody.string())
                    val content = root
                        .optJSONArray("choices")
                        ?.optJSONObject(0)
                        ?.optJSONObject("message")
                        ?.optString("content")
                        .orEmpty()

                    withContext(Dispatchers.Main) {
                        onFragment(content)
                    }

                    return@withContext Result.success(Unit)
                }

                val source = responseBody.source()

                while (!source.exhausted()) {
                    val line = source.readUtf8Line() ?: continue

                    if (!line.startsWith("data:")) continue

                    val data = line.removePrefix("data:").trim()

                    if (data == "[DONE]") break

                    try {
                        val root = JSONObject(data)
                        val content = root
                            .optJSONArray("choices")
                            ?.optJSONObject(0)
                            ?.optJSONObject("delta")
                            ?.optString("content")
                            .orEmpty()

                        if (content.isNotEmpty()) {
                            withContext(Dispatchers.Main) {
                                onFragment(content)
                            }
                        }
                    } catch (_: Exception) {
                        // 忽略空行、注释行或非 JSON 行
                    }
                }
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun generateImage(
        settings: ChatSettings,
        imageSettings: ImageSettings,
        prompt: String
    ): Result<List<ImageResult>> = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject()
                .put("model", settings.imageModel)
                .put("prompt", prompt)
                .put("n", imageSettings.count)
                .put("size", imageSettings.size)
                .put("quality", imageSettings.quality)
                .put("response_format", imageSettings.responseFormat)

            val body = json.toString()
                .toRequestBody("application/json; charset=utf-8".toMediaType())

            val request = Request.Builder()
                .url(endpoint(settings.baseUrl, "images/generations"))
                .addHeader("Authorization", "Bearer ${settings.apiKey}")
                .post(body)
                .build()

            client.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()

                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        Exception("文生图请求失败：HTTP ${response.code}\n$text")
                    )
                }

                Result.success(parseImageResults(text))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun editImage(
        settings: ChatSettings,
        imageSettings: ImageSettings,
        prompt: String,
        imageFile: File
    ): Result<List<ImageResult>> = withContext(Dispatchers.IO) {
        try {
            val imageBody = imageFile.asRequestBody("image/png".toMediaType())

            val multipart = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("model", settings.imageModel)
                .addFormDataPart("prompt", prompt)
                .addFormDataPart("size", imageSettings.size)
                .addFormDataPart("n", imageSettings.count.toString())
                .addFormDataPart("response_format", imageSettings.responseFormat)
                .addFormDataPart("image", imageFile.name, imageBody)
                .build()

            val request = Request.Builder()
                .url(endpoint(settings.baseUrl, "images/edits"))
                .addHeader("Authorization", "Bearer ${settings.apiKey}")
                .post(multipart)
                .build()

            client.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()

                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        Exception("图生图请求失败：HTTP ${response.code}\n$text")
                    )
                }

                Result.success(parseImageResults(text))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseImageResults(text: String): List<ImageResult> {
        val data = JSONObject(text).optJSONArray("data")
            ?: throw Exception("接口没有返回 data")

        return buildList {
            for (index in 0 until data.length()) {
                val item = data.optJSONObject(index) ?: continue

                val url = item.optString("url")
                    .takeIf { it.isNotBlank() }

                val base64 = item.optString("b64_json")
                    .takeIf { it.isNotBlank() }

                if (url != null || base64 != null) {
                    add(ImageResult(url, base64))
                }
            }
        }
    }
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val store = SettingsStore(application)
    private val api = ApiClient()

    private val _ui = MutableStateFlow(
        UiState(settings = store.load())
    )

    val ui = _ui.asStateFlow()

    private var requestJob: Job? = null

    fun saveSettings(settings: ChatSettings) {
        store.save(settings)
        _ui.value = _ui.value.copy(settings = settings)
    }

    fun clearError() {
        _ui.value = _ui.value.copy(error = null)
    }

    fun newConversation() {
        _ui.value = _ui.value.copy(messages = emptyList())
    }

    fun stop() {
        requestJob?.cancel()
        requestJob = null
        _ui.value = _ui.value.copy(loading = false)
    }

    fun sendMessage(text: String) {
        if (text.isBlank() || _ui.value.loading) return

        val state = _ui.value
        val userMessage = ChatMessage("user", text.trim())
        val newMessages = state.messages +
            userMessage +
            ChatMessage("assistant", "")

        val requestMessages = (state.messages + userMessage)
            .takeLast(state.settings.contextMessages)

        _ui.value = state.copy(
            messages = newMessages,
            loading = true,
            error = null
        )

        requestJob = viewModelScope.launch {
            val result = api.chat(
                settings = state.settings,
                messages = requestMessages
            ) { fragment ->
                val current = _ui.value.messages.toMutableList()

                if (current.isNotEmpty()) {
                    val last = current.last()
                    current[current.lastIndex] = last.copy(
                        content = last.content + fragment
                    )

                    _ui.value = _ui.value.copy(messages = current)
                }
            }

            if (result.isFailure) {
                _ui.value = _ui.value.copy(
                    error = result.exceptionOrNull()?.message ?: "请求失败"
                )
            }

            _ui.value = _ui.value.copy(loading = false)
        }
    }

    fun generateImage(prompt: String, imageSettings: ImageSettings) {
        if (prompt.isBlank() || _ui.value.loading) return

        val state = _ui.value

        _ui.value = state.copy(
            images = emptyList(),
            loading = true,
            error = null
        )

        requestJob = viewModelScope.launch {
            val result = api.generateImage(
                state.settings,
                imageSettings,
                prompt.trim()
            )

            result.onSuccess {
                _ui.value = _ui.value.copy(images = it)
            }.onFailure {
                _ui.value = _ui.value.copy(
                    error = it.message ?: "文生图失败"
                )
            }

            _ui.value = _ui.value.copy(loading = false)
        }
    }

    fun editImage(
        prompt: String,
        imageSettings: ImageSettings,
        file: File
    ) {
        if (prompt.isBlank() || _ui.value.loading) return

        val state = _ui.value

        _ui.value = state.copy(
            images = emptyList(),
            loading = true,
            error = null
        )

        requestJob = viewModelScope.launch {
            val result = api.editImage(
                state.settings,
                imageSettings,
                prompt.trim(),
                file
            )

            result.onSuccess {
                _ui.value = _ui.value.copy(images = it)
            }.onFailure {
                _ui.value = _ui.value.copy(
                    error = it.message ?: "图生图失败"
                )
            }

            _ui.value = _ui.value.copy(loading = false)
        }
    }
}

@Composable
fun ChatImageApp(vm: MainViewModel) {
    val state by vm.ui.collectAsState()
    var selectedTab by remember { mutableStateOf(0) }
    var showSettings by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(if (selectedTab == 0) "ChatImage" else "AI 图片")
                },
                actions = {
                    IconButton(onClick = vm::newConversation) {
                        Icon(Icons.Default.Add, contentDescription = "新建对话")
                    }

                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "设置")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("对话") }
                )

                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = {
                        Icon(Icons.Default.Image, contentDescription = null)
                    },
                    text = { Text("生图") }
                )
            }

            if (selectedTab == 0) {
                ChatPage(state, vm)
            } else {
                ImagePage(state, vm)
            }
        }
    }

    if (showSettings) {
        SettingsDialog(
            settings = state.settings,
            onSave = {
                vm.saveSettings(it)
                showSettings = false
            },
            onDismiss = { showSettings = false }
        )
    }

    state.error?.let { error ->
        AlertDialog(
            onDismissRequest = vm::clearError,
            title = { Text("请求错误") },
            text = { Text(error) },
            confirmButton = {
                Button(onClick = vm::clearError) {
                    Text("确定")
                }
            }
        )
    }
}

@Composable
fun ChatPage(state: UiState, vm: MainViewModel) {
    var input by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize()) {
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

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("输入消息") },
                maxLines = 5
            )

            Spacer(modifier = Modifier.width(6.dp))

            if (state.loading) {
                IconButton(onClick = vm::stop) {
                    Icon(Icons.Default.Stop, contentDescription = "停止生成")
                }
            } else {
                IconButton(
                    onClick = {
                        vm.sendMessage(input)
                        input = ""
                    }
                ) {
                    Icon(Icons.Default.Send, contentDescription = "发送")
                }
            }
        }
    }
}

@Composable
fun MessageBubble(message: ChatMessage) {
    val user = message.role == "user"

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (user) {
            Arrangement.End
        } else {
            Arrangement.Start
        }
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(0.9f),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text(
                text = message.content.ifBlank {
                    if (user) "" else "正在生成……"
                },
                modifier = Modifier.padding(12.dp)
            )
        }
    }
}

@Composable
fun ImagePage(state: UiState, vm: MainViewModel) {
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    var prompt by remember { mutableStateOf("") }
    var size by remember { mutableStateOf("1024x1024") }
    var quality by remember { mutableStateOf("standard") }
    var count by remember { mutableStateOf("1") }
    var selectedFile by remember { mutableStateOf<File?>(null) }
    var selectedName by remember { mutableStateOf("") }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult

        scope.launch {
            val file = withContext(Dispatchers.IO) {
                val output = File(
                    context.cacheDir,
                    "source_${System.currentTimeMillis()}.png"
                )

                context.contentResolver.openInputStream(uri)?.use { input ->
                    output.outputStream().use { destination ->
                        input.copyTo(destination)
                    }
                }

                output
            }

            selectedFile = file
            selectedName = file.name
        }
    }

    val imageSettings = ImageSettings(
        size = size,
        quality = quality,
        count = count.toIntOrNull()?.coerceIn(1, 10) ?: 1,
        responseFormat = "url"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            "文生图 / 图生图",
            style = MaterialTheme.typography.titleLarge
        )

        OutlinedTextField(
            value = prompt,
            onValueChange = { prompt = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("提示词") },
            minLines = 4
        )

        OutlinedTextField(
            value = size,
            onValueChange = { size = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("尺寸") }
        )

        OutlinedTextField(
            value = quality,
            onValueChange = { quality = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("质量") }
        )

        OutlinedTextField(
            value = count,
            onValueChange = {
                count = it.filter(Char::isDigit)
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("生成数量") },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number
            )
        )

        OutlinedButton(
            onClick = { picker.launch("image/*") },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                if (selectedName.isBlank()) {
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
                    vm.generateImage(prompt, imageSettings)
                },
                modifier = Modifier.weight(1f),
                enabled = !state.loading
            ) {
                Text("文生图")
            }

            Button(
                onClick = {
                    selectedFile?.let {
                        vm.editImage(prompt, imageSettings, it)
                    }
                },
                modifier = Modifier.weight(1f),
                enabled = !state.loading && selectedFile != null
            ) {
                Text("图生图")
            }
        }

        if (state.loading) {
            Text("正在请求，请稍候……")
        }

        state.images.forEach { result ->
            GeneratedImage(result)
        }
    }
}

@Composable
fun GeneratedImage(result: ImageResult) {
    if (!result.url.isNullOrBlank()) {
        AsyncImage(
            model = result.url,
            contentDescription = "生成图片",
            modifier = Modifier
                .fillMaxWidth()
                .height(320.dp),
            contentScale = ContentScale.Fit
        )
    }

    if (!result.base64.isNullOrBlank()) {
        val bitmap = remember(result.base64) {
            try {
                val bytes = Base64.decode(
                    result.base64,
                    Base64.DEFAULT
                )

                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            } catch (_: Exception) {
                null
            }
        }

        bitmap?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = "生成图片",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(320.dp),
                contentScale = ContentScale.Fit
            )
        }
    }
}

@Composable
fun SettingsDialog(
    settings: ChatSettings,
    onSave: (ChatSettings) -> Unit,
    onDismiss: () -> Unit
) {
    var baseUrl by remember { mutableStateOf(settings.baseUrl) }
    var apiKey by remember { mutableStateOf(settings.apiKey) }
    var chatModel by remember { mutableStateOf(settings.chatModel) }
    var imageModel by remember { mutableStateOf(settings.imageModel) }
    var stream by remember { mutableStateOf(settings.stream) }
    var temperature by remember {
        mutableStateOf(settings.temperature.toString())
    }
    var maxTokens by remember {
        mutableStateOf(settings.maxTokens.toString())
    }
    var contextMessages by remember {
        mutableStateOf(settings.contextMessages.toString())
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("API 和模型设置") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("API Base URL") },
                    supportingText = {
                        Text("例如：https://example.com/v1")
                    }
                )

                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("API Key") },
                    visualTransformation = PasswordVisualTransformation()
                )

                OutlinedTextField(
                    value = chatModel,
                    onValueChange = { chatModel = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("聊天模型") }
                )

                OutlinedTextField(
                    value = imageModel,
                    onValueChange = { imageModel = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("生图模型") }
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "流式输出",
                        modifier = Modifier.weight(1f)
                    )

                    androidx.compose.material3.Switch(
                        checked = stream,
                        onCheckedChange = { stream = it }
                    )
                }

                Text(
                    if (stream) {
                        "开启：逐段显示回复"
                    } else {
                        "关闭：等待完整回复后一次显示"
                    },
                    style = MaterialTheme.typography.bodySmall
                )

                OutlinedTextField(
                    value = temperature,
                    onValueChange = { temperature = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Temperature，0～2") },
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
                    label = { Text("单次回复最大 Tokens") },
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
                    label = { Text("上下文消息数量") },
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
                            baseUrl = baseUrl.trimEnd('/'),
                            apiKey = apiKey.trim(),
                            chatModel = chatModel.trim(),
                            imageModel = imageModel.trim(),
                            stream = stream,
                            temperature = temperature.toFloatOrNull()
                                ?.coerceIn(0f, 2f)
                                ?: 0.7f,
                            maxTokens = maxTokens.toIntOrNull()
                                ?.coerceIn(1, 100000)
                                ?: 2048,
                            contextMessages = contextMessages.toIntOrNull()
                                ?.coerceIn(1, 100)
                                ?: 20
                        )
                    )
                }
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
