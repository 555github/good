package com.example.chatimage.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.chatimage.data.database.ApiProfileEntity
import com.example.chatimage.data.database.SearchProfileEntity
import com.example.chatimage.data.model.AppSettings
import com.example.chatimage.data.model.AuthenticationMode
import com.example.chatimage.data.model.ImageEditTransport
import com.example.chatimage.data.model.RetryMode
import com.example.chatimage.data.model.StreamProtocol
import com.example.chatimage.data.model.ToolCallMode
import com.example.chatimage.data.model.ThemeMode
import com.example.chatimage.data.model.WebSearchMode
import com.example.chatimage.data.model.WebSearchProvider
import com.example.chatimage.ui.AppUiState
import com.example.chatimage.ui.AppViewModel
import org.json.JSONObject
import java.util.UUID
import kotlinx.coroutines.delay

private enum class SettingsSection(
    val displayName: String
) {
    GENERAL("常规"),
    CHAT("聊天"),
    IMAGE("图片"),
    IMAGE_INTENT("图片引用"),
    PROMPT("提示词优化"),
    SEARCH("联网与工具"),
    TIMEOUT("超时与重试"),
    DIAGNOSTICS("错误诊断")
}

@Composable
fun SettingsDialog(
    state: AppUiState,
    viewModel: AppViewModel,
    onDismiss: () -> Unit
) {
    var draft by remember(
        state.appSettings
    ) {
        mutableStateOf(
            state.appSettings
        )
    }

    var selectedSection by remember {
        mutableStateOf(
            SettingsSection.GENERAL
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("应用设置")
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(620.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(
                            rememberScrollState()
                        ),
                    horizontalArrangement =
                        Arrangement.spacedBy(6.dp)
                ) {
                    SettingsSection.entries.forEach {
                        section ->
                        AssistChip(
                            onClick = {
                                selectedSection =
                                    section
                            },
                            label = {
                                Text(
                                    section.displayName
                                )
                            }
                        )
                    }
                }

                HorizontalDivider()

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(
                            rememberScrollState()
                        )
                        .padding(
                            vertical = 8.dp
                        ),
                    verticalArrangement =
                        Arrangement.spacedBy(10.dp)
                ) {
                    when (selectedSection) {
                        SettingsSection.GENERAL -> {
                            GeneralSettings(
                                settings = draft,
                                onChange = {
                                    draft = it
                                },
                                onOpenApiProfiles = {
                                    viewModel
                                        .setShowApiProfiles(
                                            true
                                        )
                                },
                                onOpenSearchProfiles = {
                                    viewModel
                                        .setShowSearchProfiles(
                                            true
                                        )
                                }
                            )
                        }

                        SettingsSection.CHAT -> {
                            ChatSettingsSection(
                                settings = draft,
                                onChange = {
                                    draft = it
                                }
                            )
                        }

                        SettingsSection.IMAGE -> {
                            ImageSettingsSection(
                                settings = draft,
                                onChange = {
                                    draft = it
                                }
                            )
                        }

                        SettingsSection.IMAGE_INTENT -> {
                            ImageIntentSection(
                                settings = draft,
                                onChange = {
                                    draft = it
                                }
                            )
                        }

                        SettingsSection.PROMPT -> {
                            PromptOptimizationSection(
                                settings = draft,
                                onChange = {
                                    draft = it
                                }
                            )
                        }

                        SettingsSection.SEARCH -> {
                            SearchSettingsSection(
                                settings = draft,
                                onChange = {
                                    draft = it
                                }
                            )
                        }

                        SettingsSection.TIMEOUT -> {
                            TimeoutAndRetrySection(
                                settings = draft,
                                onChange = {
                                    draft = it
                                }
                            )
                        }

                        SettingsSection.DIAGNOSTICS -> {
                            DiagnosticsSection(
                                settings = draft,
                                onChange = {
                                    draft = it
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    viewModel.updateAppSettings(
                        draft
                    )

                    onDismiss()
                }
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            Row {
                TextButton(
                    onClick = {
                        viewModel.resetAppSettings()
                        onDismiss()
                    }
                ) {
                    Text("恢复默认")
                }

                TextButton(
                    onClick = onDismiss
                ) {
                    Text("取消")
                }
            }
        }
    )
}

@Composable
private fun GeneralSettings(
    settings: AppSettings,
    onChange: (AppSettings) -> Unit,
    onOpenApiProfiles: () -> Unit,
    onOpenSearchProfiles: () -> Unit
) {
    SectionTitle("线路与数据")

    Button(
        onClick = onOpenApiProfiles,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("管理聊天与图片 API 线路")
    }

    OutlinedButton(
        onClick = onOpenSearchProfiles,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("管理搜索 API 线路")
    }

    SwitchRow(
        title = "自动保存对话",
        description =
            "每条消息和图片自动写入 Room 数据库",
        checked =
            settings.automaticConversationSaving,
        onCheckedChange = {
            onChange(
                settings.copy(
                    automaticConversationSaving =
                        it
                )
            )
        }
    )

    NumberField(
        label = "自动标题最大字符数",
        value =
            settings
                .conversationTitleMaximumCharacters,
        onValueChange = {
            onChange(
                settings.copy(
                    conversationTitleMaximumCharacters =
                        it.coerceIn(
                            8,
                            100
                        )
                )
            )
        }
    )

    SwitchRow(
        title = "导出时包含本地图片路径",
        description =
            "默认关闭，避免导出文件泄露设备内部路径",
        checked =
            settings.includeImagesInExport,
        onCheckedChange = {
            onChange(
                settings.copy(
                    includeImagesInExport = it
                )
            )
        }
    )

    NumberField(
        label = "图片缓存上限（MB）",
        value =
            settings.maximumImageCacheMegabytes,
        onValueChange = {
            onChange(
                settings.copy(
                    maximumImageCacheMegabytes =
                        it.coerceIn(
                            100,
                            100000
                        )
                )
            )
        }
    )

    HorizontalDivider()
    SectionTitle("界面")

    EnumSelector(
        label = "主题",
        current = settings.appearance.themeMode.name,
        values = ThemeMode.entries.map { it.name },
        onSelect = { selected ->
            onChange(
                settings.copy(
                    appearance = settings.appearance.copy(
                        themeMode = ThemeMode.valueOf(selected)
                    )
                )
            )
        }
    )

    DecimalField(
        label = "字体缩放",
        supportingText = "推荐 1.0；支持 0.7 - 2.0",
        helpText = "同时缩放聊天消息、设置和按钮文字。系统字体大小仍会继续生效。",
        value =
            settings.appearance
                .fontScale
                .toDouble(),
        onValueChange = {
            onChange(
                settings.copy(
                    appearance =
                        settings.appearance.copy(
                            fontScale =
                                it.toFloat()
                                    .coerceIn(
                                        0.7f,
                                        2f
                                    )
                        )
                )
            )
        }
    )

    DecimalField(
        label = "用户消息宽度比例",
        supportingText = "推荐 0.94；支持 0.55 - 0.98",
        helpText = "控制用户消息气泡占聊天区域的最大宽度，助手消息保持接近全宽。",
        value =
            settings.appearance
                .messageWidthFraction
                .toDouble(),
        onValueChange = {
            onChange(
                settings.copy(
                    appearance =
                        settings.appearance.copy(
                            messageWidthFraction =
                                it.toFloat()
                                    .coerceIn(
                                        0.55f,
                                        0.98f
                                    )
                        )
                )
            )
        }
    )

    NumberField(
        label = "图片预览高度（dp）",
        supportingText = "推荐 340；支持 160 - 1000",
        helpText = "只影响聊天列表中的预览高度，不会改变原图分辨率。",
        value =
            settings.appearance
                .imagePreviewHeightDp,
        onValueChange = {
            onChange(
                settings.copy(
                    appearance =
                        settings.appearance.copy(
                            imagePreviewHeightDp =
                                it.coerceIn(
                                    160,
                                    1000
                                )
                        )
                )
            )
        }
    )

    NumberField(
        label = "消息间距（dp）",
        value = settings.appearance.messageSpacingDp,
        supportingText = "紧凑 4；推荐 8；宽松 14",
        helpText = "控制相邻两条消息之间的垂直距离。",
        onValueChange = {
            onChange(
                settings.copy(
                    appearance = settings.appearance.copy(
                        messageSpacingDp = it.coerceIn(2, 24)
                    )
                )
            )
        }
    )

    NumberField(
        label = "消息内边距（dp）",
        value = settings.appearance.messagePaddingDp,
        supportingText = "紧凑 6；推荐 10；宽松 16",
        helpText = "控制文字、图片和操作栏与消息气泡边缘的距离。",
        onValueChange = {
            onChange(
                settings.copy(
                    appearance = settings.appearance.copy(
                        messagePaddingDp = it.coerceIn(4, 24)
                    )
                )
            )
        }
    )

    SwitchRow(
        title = "流式回答自动滚动",
        checked =
            settings.appearance.autoScroll,
        onCheckedChange = {
            onChange(
                settings.copy(
                    appearance =
                        settings.appearance.copy(
                            autoScroll = it
                        )
                )
            )
        }
    )

    SwitchRow(
        title = "发送后收起键盘",
        checked =
            settings.appearance
                .dismissKeyboardOnSend,
        onCheckedChange = {
            onChange(
                settings.copy(
                    appearance =
                        settings.appearance.copy(
                            dismissKeyboardOnSend =
                                it
                        )
                )
            )
        }
    )

    SwitchRow(
        title = "顶部显示线路名",
        checked =
            settings.appearance
                .showProfileInTopBar,
        onCheckedChange = {
            onChange(
                settings.copy(
                    appearance =
                        settings.appearance.copy(
                            showProfileInTopBar =
                                it
                        )
                )
            )
        }
    )

    SwitchRow(
        title = "顶部显示模型名",
        checked =
            settings.appearance
                .showModelInTopBar,
        onCheckedChange = {
            onChange(
                settings.copy(
                    appearance =
                        settings.appearance.copy(
                            showModelInTopBar =
                                it
                        )
                )
            )
        }
    )

    HorizontalDivider()
    SectionTitle("消息操作栏")

    SwitchRow(
        title = "显示复制按钮",
        checked =
            settings.appearance
                .showCopyButton,
        onCheckedChange = {
            onChange(
                settings.copy(
                    appearance =
                        settings.appearance.copy(
                            showCopyButton = it
                        )
                )
            )
        }
    )

    SwitchRow(
        title = "显示分享按钮",
        checked =
            settings.appearance
                .showShareButton,
        onCheckedChange = {
            onChange(
                settings.copy(
                    appearance =
                        settings.appearance.copy(
                            showShareButton = it
                        )
                )
            )
        }
    )

    SwitchRow(
        title = "显示删除按钮",
        checked =
            settings.appearance
                .showDeleteButton,
        onCheckedChange = {
            onChange(
                settings.copy(
                    appearance =
                        settings.appearance.copy(
                            showDeleteButton = it
                        )
                )
            )
        }
    )

    SwitchRow(
        title = "显示重新生成按钮",
        checked =
            settings.appearance
                .showRegenerateButton,
        onCheckedChange = {
            onChange(
                settings.copy(
                    appearance =
                        settings.appearance.copy(
                            showRegenerateButton =
                                it
                        )
                )
            )
        }
    )
}

@Composable
private fun ChatSettingsSection(
    settings: AppSettings,
    onChange: (AppSettings) -> Unit
) {
    val chat = settings.chatParameters

    SectionTitle("基础参数")

    MultilineField(
        label = "System Prompt",
        value = chat.systemPrompt,
        onValueChange = {
            onChange(
                settings.copy(
                    chatParameters =
                        chat.copy(
                            systemPrompt = it
                        )
                )
            )
        }
    )

    SwitchRow(
        title = "聊天流式输出",
        description =
            "只影响语言模型，图片接口始终独立处理",
        checked = chat.streamEnabled,
        onCheckedChange = {
            onChange(
                settings.copy(
                    chatParameters =
                        chat.copy(
                            streamEnabled = it
                        )
                )
            )
        }
    )

    EnumSelector(
        label = "流式协议",
        current = chat.streamProtocol.name,
        values =
            StreamProtocol.entries.map {
                it.name
            },
        onSelect = {
            onChange(
                settings.copy(
                    chatParameters =
                        chat.copy(
                            streamProtocol =
                                enumValueOrDefault(
                                    it,
                                    chat.streamProtocol
                                )
                        )
                )
            )
        }
    )

    TextFieldSetting(
        label = "SSE 数据前缀",
        value = chat.sseDataPrefix,
        onValueChange = {
            onChange(
                settings.copy(
                    chatParameters =
                        chat.copy(
                            sseDataPrefix = it
                        )
                )
            )
        }
    )

    TextFieldSetting(
        label = "SSE 结束标记",
        value = chat.sseDoneMarker,
        onValueChange = {
            onChange(
                settings.copy(
                    chatParameters =
                        chat.copy(
                            sseDoneMarker = it
                        )
                )
            )
        }
    )

    TextFieldSetting(
        label = "流式文本字段路径",
        value = chat.streamTextPath,
        supportingText =
            "例如 choices[0].delta.content",
        onValueChange = {
            onChange(
                settings.copy(
                    chatParameters =
                        chat.copy(
                            streamTextPath = it
                        )
                )
            )
        }
    )

    TextFieldSetting(
        label = "非流式文本字段路径",
        value = chat.nonStreamTextPath,
        supportingText =
            "例如 choices[0].message.content",
        onValueChange = {
            onChange(
                settings.copy(
                    chatParameters =
                        chat.copy(
                            nonStreamTextPath =
                                it
                        )
                )
            )
        }
    )

    ParameterSwitchWithDecimal(
        title = "Temperature",
        enabled =
            chat.temperatureEnabled,
        value = chat.temperature,
        onEnabledChange = {
            onChange(
                settings.copy(
                    chatParameters =
                        chat.copy(
                            temperatureEnabled =
                                it
                        )
                )
            )
        },
        onValueChange = {
            onChange(
                settings.copy(
                    chatParameters =
                        chat.copy(
                            temperature =
                                it.coerceIn(
                                    0.0,
                                    2.0
                                )
                        )
                )
            )
        }
    )

    ParameterSwitchWithDecimal(
        title = "Top P",
        enabled = chat.topPEnabled,
        value = chat.topP,
        onEnabledChange = {
            onChange(
                settings.copy(
                    chatParameters =
                        chat.copy(
                            topPEnabled = it
                        )
                )
            )
        },
        onValueChange = {
            onChange(
                settings.copy(
                    chatParameters =
                        chat.copy(
                            topP =
                                it.coerceIn(
                                    0.0,
                                    1.0
                                )
                        )
                )
            )
        }
    )

    SwitchRow(
        title = "发送最大 Tokens 参数",
        checked =
            chat.maxTokensEnabled,
        onCheckedChange = {
            onChange(
                settings.copy(
                    chatParameters =
                        chat.copy(
                            maxTokensEnabled =
                                it
                        )
                )
            )
        }
    )

    TextFieldSetting(
        label = "最大 Tokens 字段名",
        value =
            chat.maxTokensFieldName,
        supportingText =
            "max_tokens 或 max_completion_tokens",
        onValueChange = {
            onChange(
                settings.copy(
                    chatParameters =
                        chat.copy(
                            maxTokensFieldName =
                                it
                        )
                )
            )
        }
    )

    NumberField(
        label = "单次回复最大 Tokens",
        value = chat.maxTokens,
        onValueChange = {
            onChange(
                settings.copy(
                    chatParameters =
                        chat.copy(
                            maxTokens =
                                it.coerceIn(
                                    1,
                                    1000000
                                )
                        )
                )
            )
        }
    )

    NumberField(
        label = "上下文消息数量",
        value =
            chat.contextMessageLimit,
        onValueChange = {
            onChange(
                settings.copy(
                    chatParameters =
                        chat.copy(
                            contextMessageLimit =
                                it.coerceIn(
                                    1,
                                    1000
                                )
                        )
                )
            )
        }
    )

    NumberField(
        label = "近似上下文 Token 上限",
        value =
            chat.approximateContextTokenLimit,
        supportingText =
            "填写 0 表示不按 Token 估算限制",
        onValueChange = {
            onChange(
                settings.copy(
                    chatParameters =
                        chat.copy(
                            approximateContextTokenLimit =
                                it.coerceAtLeast(
                                    0
                                )
                        )
                )
            )
        }
    )

    SwitchRow(
        title = "请求 Token 用量统计",
        description = "流式 Chat Completions 会发送 stream_options.include_usage",
        checked = chat.requestUsage,
        onCheckedChange = {
            onChange(
                settings.copy(
                    chatParameters = chat.copy(requestUsage = it)
                )
            )
        }
    )

    HorizontalDivider()
    SectionTitle("推理强度")

    SwitchRow(
        title = "发送推理强度参数",
        description = "不同模型支持的值不同，请按服务商文档填写",
        checked = chat.reasoningEnabled,
        onCheckedChange = {
            onChange(
                settings.copy(
                    chatParameters = chat.copy(reasoningEnabled = it)
                )
            )
        }
    )

    TextFieldSetting(
        label = "推理强度字段路径",
        value = chat.reasoningFieldPath,
        supportingText = "OpenAI Responses 推荐 reasoning.effort",
        helpText = "支持点分隔的嵌套字段路径，也可以填写中转站要求的自定义字段名。",
        onValueChange = {
            onChange(
                settings.copy(
                    chatParameters = chat.copy(reasoningFieldPath = it)
                )
            )
        }
    )

    TextFieldSetting(
        label = "推理强度值",
        value = chat.reasoningValue,
        supportingText = "常见值：none、low、medium、high、xhigh、max",
        helpText = "此处不限制枚举，方便兼容不同模型和厂商的自定义值。",
        onValueChange = {
            onChange(
                settings.copy(
                    chatParameters = chat.copy(reasoningValue = it)
                )
            )
        }
    )

    ParameterSwitchWithDecimal(
        title = "Frequency Penalty",
        enabled =
            chat.frequencyPenaltyEnabled,
        value =
            chat.frequencyPenalty,
        onEnabledChange = {
            onChange(
                settings.copy(
                    chatParameters =
                        chat.copy(
                            frequencyPenaltyEnabled =
                                it
                        )
                )
            )
        },
        onValueChange = {
            onChange(
                settings.copy(
                    chatParameters =
                        chat.copy(
                            frequencyPenalty =
                                it.coerceIn(
                                    -2.0,
                                    2.0
                                )
                        )
                )
            )
        }
    )

    ParameterSwitchWithDecimal(
        title = "Presence Penalty",
        enabled =
            chat.presencePenaltyEnabled,
        value =
            chat.presencePenalty,
        onEnabledChange = {
            onChange(
                settings.copy(
                    chatParameters =
                        chat.copy(
                            presencePenaltyEnabled =
                                it
                        )
                )
            )
        },
        onValueChange = {
            onChange(
                settings.copy(
                    chatParameters =
                        chat.copy(
                            presencePenalty =
                                it.coerceIn(
                                    -2.0,
                                    2.0
                                )
                        )
                )
            )
        }
    )

    SwitchRow(
        title = "发送 Seed",
        checked = chat.seedEnabled,
        onCheckedChange = {
            onChange(
                settings.copy(
                    chatParameters =
                        chat.copy(
                            seedEnabled = it
                        )
                )
            )
        }
    )

    LongField(
        label = "Seed",
        value = chat.seed,
        onValueChange = {
            onChange(
                settings.copy(
                    chatParameters =
                        chat.copy(seed = it)
                )
            )
        }
    )

    SwitchRow(
        title = "发送 Stop",
        checked = chat.stopEnabled,
        onCheckedChange = {
            onChange(
                settings.copy(
                    chatParameters =
                        chat.copy(
                            stopEnabled = it
                        )
                )
            )
        }
    )

    MultilineField(
        label = "Stop 序列（每行一个）",
        value =
            chat.stopSequences
                .joinToString("\n"),
        onValueChange = {
            onChange(
                settings.copy(
                    chatParameters =
                        chat.copy(
                            stopSequences =
                                linesToList(it)
                        )
                )
            )
        }
    )

    EnumSelector(
        label = "Response Format",
        current =
            chat.responseFormatMode,
        values = listOf(
            "NONE",
            "TEXT",
            "JSON_OBJECT",
            "CUSTOM_JSON"
        ),
        onSelect = {
            onChange(
                settings.copy(
                    chatParameters =
                        chat.copy(
                            responseFormatMode =
                                it
                        )
                )
            )
        }
    )

    MultilineField(
        label = "自定义 Response Format JSON",
        value =
            chat.responseFormatJson,
        onValueChange = {
            onChange(
                settings.copy(
                    chatParameters =
                        chat.copy(
                            responseFormatJson =
                                it
                        )
                )
            )
        }
    )

    MultilineField(
        label = "聊天请求附加 JSON",
        value =
            chat.extraRequestJson,
        supportingText =
            "会合并到最终请求；自定义 JSON 中的同名字段优先",
        onValueChange = {
            onChange(
                settings.copy(
                    chatParameters =
                        chat.copy(
                            extraRequestJson =
                                it
                        )
                )
            )
        }
    )
}

@Composable
private fun ImageSettingsSection(
    settings: AppSettings,
    onChange: (AppSettings) -> Unit
) {
    val image = settings.imageParameters

    SectionTitle("图片请求参数")

    TextFieldSetting(
        label = "单独覆盖图片模型",
        value = image.modelOverride,
        supportingText =
            "留空则使用当前 API 线路中的图片模型",
        onValueChange = {
            onChange(
                settings.copy(
                    imageParameters =
                        image.copy(
                            modelOverride = it
                        )
                )
            )
        }
    )

    SwitchRow(
        title = "发送尺寸参数",
        checked = image.sizeEnabled,
        onCheckedChange = {
            onChange(
                settings.copy(
                    imageParameters =
                        image.copy(
                            sizeEnabled = it
                        )
                )
            )
        }
    )

    TextFieldSetting(
        label = "尺寸字段名",
        value = image.sizeFieldName,
        onValueChange = {
            onChange(
                settings.copy(
                    imageParameters =
                        image.copy(
                            sizeFieldName = it
                        )
                )
            )
        }
    )

    TextFieldSetting(
        label = "默认尺寸",
        value = image.size,
        onValueChange = {
            onChange(
                settings.copy(
                    imageParameters =
                        image.copy(size = it)
                )
            )
        }
    )

    MultilineField(
        label = "尺寸预设（每行一个）",
        value =
            image.generatedSizePresets
                .joinToString("\n"),
        onValueChange = {
            onChange(
                settings.copy(
                    imageParameters =
                        image.copy(
                            generatedSizePresets =
                                linesToList(it)
                        )
                )
            )
        }
    )

    SwitchRow(
        title = "发送质量参数",
        checked =
            image.qualityEnabled,
        onCheckedChange = {
            onChange(
                settings.copy(
                    imageParameters =
                        image.copy(
                            qualityEnabled =
                                it
                        )
                )
            )
        }
    )

    TextFieldSetting(
        label = "质量字段名",
        value =
            image.qualityFieldName,
        onValueChange = {
            onChange(
                settings.copy(
                    imageParameters =
                        image.copy(
                            qualityFieldName =
                                it
                        )
                )
            )
        }
    )

    TextFieldSetting(
        label = "默认质量",
        value = image.quality,
        onValueChange = {
            onChange(
                settings.copy(
                    imageParameters =
                        image.copy(
                            quality = it
                        )
                )
            )
        }
    )

    MultilineField(
        label = "质量预设（每行一个）",
        value =
            image.qualityPresets
                .joinToString("\n"),
        onValueChange = {
            onChange(
                settings.copy(
                    imageParameters =
                        image.copy(
                            qualityPresets =
                                linesToList(it)
                        )
                )
            )
        }
    )

    SwitchRow(
        title = "发送生成数量",
        checked = image.countEnabled,
        onCheckedChange = {
            onChange(
                settings.copy(
                    imageParameters =
                        image.copy(
                            countEnabled = it
                        )
                )
            )
        }
    )

    TextFieldSetting(
        label = "数量字段名",
        value = image.countFieldName,
        onValueChange = {
            onChange(
                settings.copy(
                    imageParameters =
                        image.copy(
                            countFieldName = it
                        )
                )
            )
        }
    )

    NumberField(
        label = "默认生成数量",
        value = image.count,
        onValueChange = {
            onChange(
                settings.copy(
                    imageParameters =
                        image.copy(
                            count =
                                it.coerceIn(
                                    1,
                                    10
                                )
                        )
                )
            )
        }
    )

    SwitchRow(
        title = "发送返回格式",
        checked =
            image.responseFormatEnabled,
        onCheckedChange = {
            onChange(
                settings.copy(
                    imageParameters =
                        image.copy(
                            responseFormatEnabled =
                                it
                        )
                )
            )
        }
    )

    TextFieldSetting(
        label = "返回格式字段名",
        value =
            image.responseFormatFieldName,
        onValueChange = {
            onChange(
                settings.copy(
                    imageParameters =
                        image.copy(
                            responseFormatFieldName =
                                it
                        )
                )
            )
        }
    )

    TextFieldSetting(
        label = "默认返回格式",
        value = image.responseFormat,
        supportingText =
            "常用值：b64_json 或 url",
        onValueChange = {
            onChange(
                settings.copy(
                    imageParameters =
                        image.copy(
                            responseFormat = it
                        )
                )
            )
        }
    )

    HorizontalDivider()
    SectionTitle("请求字段")

    TextFieldSetting(
        label = "模型字段名",
        value = image.modelFieldName,
        onValueChange = {
            onChange(
                settings.copy(
                    imageParameters =
                        image.copy(
                            modelFieldName = it
                        )
                )
            )
        }
    )

    TextFieldSetting(
        label = "提示词字段名",
        value = image.promptFieldName,
        onValueChange = {
            onChange(
                settings.copy(
                    imageParameters =
                        image.copy(
                            promptFieldName = it
                        )
                )
            )
        }
    )

    TextFieldSetting(
        label = "源图片字段名",
        value = image.imageFieldName,
        onValueChange = {
            onChange(
                settings.copy(
                    imageParameters =
                        image.copy(
                            imageFieldName = it
                        )
                )
            )
        }
    )

    EnumSelector(
        label = "图生图上传方式",
        current =
            image.imageEditTransport.name,
        values =
            ImageEditTransport.entries
                .map {
                    it.name
                },
        onSelect = {
            onChange(
                settings.copy(
                    imageParameters =
                        image.copy(
                            imageEditTransport =
                                enumValueOrDefault(
                                    it,
                                    image
                                        .imageEditTransport
                                )
                        )
                )
            )
        }
    )

    SwitchRow(
        title = "Base64 包含 Data URL 前缀",
        checked =
            image.includeDataUrlPrefix,
        onCheckedChange = {
            onChange(
                settings.copy(
                    imageParameters =
                        image.copy(
                            includeDataUrlPrefix =
                                it
                        )
                )
            )
        }
    )

    MultilineField(
        label = "文生图附加 JSON",
        value =
            image.extraGenerationJson,
        onValueChange = {
            onChange(
                settings.copy(
                    imageParameters =
                        image.copy(
                            extraGenerationJson =
                                it
                        )
                )
            )
        }
    )

    MultilineField(
        label = "图生图附加 JSON",
        value =
            image.extraEditJson,
        onValueChange = {
            onChange(
                settings.copy(
                    imageParameters =
                        image.copy(
                            extraEditJson = it
                        )
                )
            )
        }
    )

    HorizontalDivider()
    SectionTitle("响应解析")

    TextFieldSetting(
        label = "图片数组路径",
        value = image.imageArrayPath,
        supportingText =
            "例如 data、images、result.outputs",
        onValueChange = {
            onChange(
                settings.copy(
                    imageParameters =
                        image.copy(
                            imageArrayPath = it
                        )
                )
            )
        }
    )

    MultilineField(
        label = "图片 URL 字段路径（每行一个）",
        value =
            image.imageUrlPaths
                .joinToString("\n"),
        onValueChange = {
            onChange(
                settings.copy(
                    imageParameters =
                        image.copy(
                            imageUrlPaths =
                                linesToList(it)
                        )
                )
            )
        }
    )

    MultilineField(
        label = "图片 Base64 字段路径（每行一个）",
        value =
            image.imageBase64Paths
                .joinToString("\n"),
        onValueChange = {
            onChange(
                settings.copy(
                    imageParameters =
                        image.copy(
                            imageBase64Paths =
                                linesToList(it)
                        )
                )
            )
        }
    )

    TextFieldSetting(
        label = "相对图片 URL 基准地址",
        value = image.relativeUrlBase,
        supportingText =
            "留空使用当前 API Base URL",
        onValueChange = {
            onChange(
                settings.copy(
                    imageParameters =
                        image.copy(
                            relativeUrlBase = it
                        )
                )
            )
        }
    )

    EnumSelector(
        label = "下载图片时的认证方式",
        current =
            image.downloadAuthenticationMode,
        values = listOf(
            "IMAGE_API",
            "CHAT_API",
            "NONE"
        ),
        onSelect = {
            onChange(
                settings.copy(
                    imageParameters =
                        image.copy(
                            downloadAuthenticationMode =
                                it
                        )
                )
            )
        }
    )
}

@Composable
private fun ImageIntentSection(
    settings: AppSettings,
    onChange: (AppSettings) -> Unit
) {
    val intent = settings.imageIntent

    SwitchRow(
        title = "自动识别图片生成与编辑意图",
        checked = intent.enabled,
        onCheckedChange = {
            onChange(
                settings.copy(
                    imageIntent =
                        intent.copy(
                            enabled = it
                        )
                )
            )
        }
    )

    SwitchRow(
        title = "自动引用最近生成图片",
        description =
            "识别“上图、上一张、刚才图片”等表达",
        checked =
            intent.autoReferenceRecentImage,
        onCheckedChange = {
            onChange(
                settings.copy(
                    imageIntent =
                        intent.copy(
                            autoReferenceRecentImage =
                                it
                        )
                )
            )
        }
    )

    SwitchRow(
        title = "图片来源有歧义时询问",
        checked =
            intent.askWhenAmbiguous,
        onCheckedChange = {
            onChange(
                settings.copy(
                    imageIntent =
                        intent.copy(
                            askWhenAmbiguous = it
                        )
                )
            )
        }
    )

    SwitchRow(
        title = "发送前显示源图片",
        checked =
            intent.showSourceImageBeforeSend,
        onCheckedChange = {
            onChange(
                settings.copy(
                    imageIntent =
                        intent.copy(
                            showSourceImageBeforeSend =
                                it
                        )
                )
            )
        }
    )

    SwitchRow(
        title = "聊天模式允许自动切换到图片编辑",
        checked =
            intent.allowChatModeToImageEdit,
        onCheckedChange = {
            onChange(
                settings.copy(
                    imageIntent =
                        intent.copy(
                            allowChatModeToImageEdit =
                                it
                        )
                )
            )
        }
    )

    MultilineField(
        label = "文生图关键词（每行一个）",
        value =
            intent.generationKeywords
                .joinToString("\n"),
        onValueChange = {
            onChange(
                settings.copy(
                    imageIntent =
                        intent.copy(
                            generationKeywords =
                                linesToList(it)
                        )
                )
            )
        }
    )

    MultilineField(
        label = "图片编辑关键词（每行一个）",
        value =
            intent.editKeywords
                .joinToString("\n"),
        onValueChange = {
            onChange(
                settings.copy(
                    imageIntent =
                        intent.copy(
                            editKeywords =
                                linesToList(it)
                        )
                )
            )
        }
    )

    MultilineField(
        label = "引用上文图片关键词（每行一个）",
        value =
            intent.recentImageKeywords
                .joinToString("\n"),
        onValueChange = {
            onChange(
                settings.copy(
                    imageIntent =
                        intent.copy(
                            recentImageKeywords =
                                linesToList(it)
                        )
                )
            )
        }
    )
}

@Composable
private fun PromptOptimizationSection(
    settings: AppSettings,
    onChange: (AppSettings) -> Unit
) {
    val prompt = settings.promptOptimization

    SwitchRow(
        title = "启用图片提示词优化",
        checked = prompt.enabled,
        onCheckedChange = {
            onChange(
                settings.copy(
                    promptOptimization =
                        prompt.copy(
                            enabled = it
                        )
                )
            )
        }
    )

    SwitchRow(
        title = "长提示词发送前提示优化",
        checked =
            prompt.promptBeforeLongRequest,
        onCheckedChange = {
            onChange(
                settings.copy(
                    promptOptimization =
                        prompt.copy(
                            promptBeforeLongRequest =
                                it
                        )
                )
            )
        }
    )

    NumberField(
        label = "触发优化的字符数",
        value =
            prompt.triggerCharacterCount,
        onValueChange = {
            onChange(
                settings.copy(
                    promptOptimization =
                        prompt.copy(
                            triggerCharacterCount =
                                it.coerceIn(
                                    1,
                                    100000
                                )
                        )
                )
            )
        }
    )

    NumberField(
        label = "优化目标字符数",
        value =
            prompt.targetCharacterCount,
        onValueChange = {
            onChange(
                settings.copy(
                    promptOptimization =
                        prompt.copy(
                            targetCharacterCount =
                                it.coerceIn(
                                    20,
                                    10000
                                )
                        )
                )
            )
        }
    )

    TextFieldSetting(
        label = "提示词优化模型覆盖",
        value =
            prompt.optimizerModelOverride,
        supportingText =
            "留空使用线路中的优化模型，再留空则使用聊天模型",
        onValueChange = {
            onChange(
                settings.copy(
                    promptOptimization =
                        prompt.copy(
                            optimizerModelOverride =
                                it
                        )
                )
            )
        }
    )

    DecimalField(
        label = "优化 Temperature",
        value = prompt.temperature,
        onValueChange = {
            onChange(
                settings.copy(
                    promptOptimization =
                        prompt.copy(
                            temperature =
                                it.coerceIn(
                                    0.0,
                                    2.0
                                )
                        )
                )
            )
        }
    )

    NumberField(
        label = "优化最大 Tokens",
        value = prompt.maxTokens,
        onValueChange = {
            onChange(
                settings.copy(
                    promptOptimization =
                        prompt.copy(
                            maxTokens =
                                it.coerceIn(
                                    1,
                                    100000
                                )
                        )
                )
            )
        }
    )

    SwitchRow(
        title = "优化后要求确认",
        checked =
            prompt.requireConfirmation,
        onCheckedChange = {
            onChange(
                settings.copy(
                    promptOptimization =
                        prompt.copy(
                            requireConfirmation =
                                it
                        )
                )
            )
        }
    )

    SwitchRow(
        title = "保留原始提示词",
        checked =
            prompt.keepOriginalPrompt,
        onCheckedChange = {
            onChange(
                settings.copy(
                    promptOptimization =
                        prompt.copy(
                            keepOriginalPrompt =
                                it
                        )
                )
            )
        }
    )

    MultilineField(
        label = "优化提示模板",
        value = prompt.template,
        onValueChange = {
            onChange(
                settings.copy(
                    promptOptimization =
                        prompt.copy(
                            template = it
                        )
                )
            )
        }
    )
}

@Composable
private fun SearchSettingsSection(
    settings: AppSettings,
    onChange: (AppSettings) -> Unit
) {
    val search = settings.search

    EnumSelector(
        label = "联网搜索模式",
        current = search.mode.name,
        values =
            WebSearchMode.entries.map {
                it.name
            },
        onSelect = {
            onChange(
                settings.copy(
                    search =
                        search.copy(
                            mode =
                                enumValueOrDefault(
                                    it,
                                    search.mode
                                )
                        )
                )
            )
        }
    )

    EnumSelector(
        label = "联网来源",
        current = search.provider.name,
        values = WebSearchProvider.entries.map { it.name },
        onSelect = {
            onChange(
                settings.copy(
                    search = search.copy(
                        provider = enumValueOrDefault(it, search.provider)
                    )
                )
            )
        }
    )

    TextFieldSetting(
        label = "模型内置联网工具类型",
        value = search.builtInToolType,
        supportingText = "OpenAI Responses 使用 web_search",
        helpText = "仅在联网来源选择 MODEL_BUILT_IN 且线路使用 /responses 时发送。",
        onValueChange = {
            onChange(
                settings.copy(
                    search = search.copy(builtInToolType = it)
                )
            )
        }
    )

    EnumSelector(
        label = "Tool Calls 模式",
        current =
            search.toolCallMode.name,
        values = listOf(
            ToolCallMode.DISABLED.name,
            ToolCallMode.OPENAI_TOOLS.name
        ),
        onSelect = {
            onChange(
                settings.copy(
                    search =
                        search.copy(
                            toolCallMode =
                                enumValueOrDefault(
                                    it,
                                    search.toolCallMode
                                )
                        )
                )
            )
        }
    )

    SwitchRow(
        title = "Tool Calls 失败时回退",
        description =
            "回退到搜索结果作为普通上下文注入",
        checked =
            search.fallbackToInjectedSearch,
        onCheckedChange = {
            onChange(
                settings.copy(
                    search =
                        search.copy(
                            fallbackToInjectedSearch =
                                it
                        )
                )
            )
        }
    )

    NumberField(
        label = "默认搜索结果数量",
        value = search.resultCount,
        onValueChange = {
            onChange(
                settings.copy(
                    search =
                        search.copy(
                            resultCount =
                                it.coerceIn(
                                    1,
                                    10
                                )
                        )
                )
            )
        }
    )

    TextFieldSetting(
        label = "搜索语言",
        value = search.language,
        onValueChange = {
            onChange(
                settings.copy(
                    search =
                        search.copy(
                            language = it
                        )
                )
            )
        }
    )

    TextFieldSetting(
        label = "搜索地区",
        value = search.region,
        onValueChange = {
            onChange(
                settings.copy(
                    search =
                        search.copy(
                            region = it
                        )
                )
            )
        }
    )

    MultilineField(
        label = "自动联网关键词（每行一个）",
        value =
            search.searchKeywords
                .joinToString("\n"),
        onValueChange = {
            onChange(
                settings.copy(
                    search =
                        search.copy(
                            searchKeywords =
                                linesToList(it)
                        )
                )
            )
        }
    )

    HorizontalDivider()
    SectionTitle("Tool Calls")

    TextFieldSetting(
        label = "工具函数名称",
        value = search.toolName,
        onValueChange = {
            onChange(
                settings.copy(
                    search =
                        search.copy(
                            toolName = it
                        )
                )
            )
        }
    )

    MultilineField(
        label = "工具描述",
        value =
            search.toolDescription,
        onValueChange = {
            onChange(
                settings.copy(
                    search =
                        search.copy(
                            toolDescription = it
                        )
                )
            )
        }
    )

    TextFieldSetting(
        label = "查询参数名称",
        value =
            search.toolQueryParameterName,
        onValueChange = {
            onChange(
                settings.copy(
                    search =
                        search.copy(
                            toolQueryParameterName =
                                it
                        )
                )
            )
        }
    )

    TextFieldSetting(
        label = "数量参数名称",
        value =
            search.toolCountParameterName,
        onValueChange = {
            onChange(
                settings.copy(
                    search =
                        search.copy(
                            toolCountParameterName =
                                it
                        )
                )
            )
        }
    )

    TextFieldSetting(
        label = "Tool Choice",
        value = search.toolChoice,
        supportingText =
            "auto、none、required 或中转站支持的值",
        onValueChange = {
            onChange(
                settings.copy(
                    search =
                        search.copy(
                            toolChoice = it
                        )
                )
            )
        }
    )

    NumberField(
        label = "最大工具循环轮数",
        value =
            search.maximumToolRounds,
        onValueChange = {
            onChange(
                settings.copy(
                    search =
                        search.copy(
                            maximumToolRounds =
                                it.coerceIn(
                                    1,
                                    10
                                )
                        )
                )
            )
        }
    )

    NumberField(
        label = "每轮最大工具调用数",
        value =
            search.maximumCallsPerRound,
        onValueChange = {
            onChange(
                settings.copy(
                    search =
                        search.copy(
                            maximumCallsPerRound =
                                it.coerceIn(
                                    1,
                                    10
                                )
                        )
                )
            )
        }
    )

    NumberField(
        label = "搜索词最大字符数",
        value =
            search.maximumQueryCharacters,
        onValueChange = {
            onChange(
                settings.copy(
                    search =
                        search.copy(
                            maximumQueryCharacters =
                                it.coerceIn(
                                    10,
                                    5000
                                )
                        )
                )
            )
        }
    )

    SwitchRow(
        title = "联网最终回答使用流式输出",
        checked =
            search.finalAnswerStreamEnabled,
        onCheckedChange = {
            onChange(
                settings.copy(
                    search =
                        search.copy(
                            finalAnswerStreamEnabled =
                                it
                        )
                )
            )
        }
    )

    SwitchRow(
        title = "回答必须带引用",
        checked =
            search.requireCitations,
        onCheckedChange = {
            onChange(
                settings.copy(
                    search =
                        search.copy(
                            requireCitations = it
                        )
                )
            )
        }
    )

    TextFieldSetting(
        label = "引用格式",
        value =
            search.citationFormat,
        onValueChange = {
            onChange(
                settings.copy(
                    search =
                        search.copy(
                            citationFormat = it
                        )
                )
            )
        }
    )

    SwitchRow(
        title = "搜索失败后允许离线回答",
        checked =
            search.allowOfflineAnswerAfterFailure,
        onCheckedChange = {
            onChange(
                settings.copy(
                    search =
                        search.copy(
                            allowOfflineAnswerAfterFailure =
                                it
                        )
                )
            )
        }
    )

    SwitchRow(
        title = "搜索结果保存到历史",
        checked =
            search.saveSearchResultsToHistory,
        onCheckedChange = {
            onChange(
                settings.copy(
                    search =
                        search.copy(
                            saveSearchResultsToHistory =
                                it
                        )
                )
            )
        }
    )

    MultilineField(
        label = "联网资料系统提示模板",
        value =
            search.webContextTemplate,
        onValueChange = {
            onChange(
                settings.copy(
                    search =
                        search.copy(
                            webContextTemplate =
                                it
                        )
                )
            )
        }
    )
}

@Composable
private fun TimeoutAndRetrySection(
    settings: AppSettings,
    onChange: (AppSettings) -> Unit
) {
    val timeout = settings.timeouts
    val retry = settings.retry

    SectionTitle("聊天超时（秒）")

    LongField(
        label = "聊天连接超时",
        value =
            timeout.chatConnectSeconds,
        onValueChange = {
            onChange(
                settings.copy(
                    timeouts =
                        timeout.copy(
                            chatConnectSeconds =
                                it.coerceAtLeast(
                                    1
                                )
                        )
                )
            )
        }
    )

    LongField(
        label = "聊天读取超时",
        value =
            timeout.chatReadSeconds,
        onValueChange = {
            onChange(
                settings.copy(
                    timeouts =
                        timeout.copy(
                            chatReadSeconds =
                                it.coerceAtLeast(
                                    1
                                )
                        )
                )
            )
        }
    )

    LongField(
        label = "聊天写入超时",
        value =
            timeout.chatWriteSeconds,
        onValueChange = {
            onChange(
                settings.copy(
                    timeouts =
                        timeout.copy(
                            chatWriteSeconds =
                                it.coerceAtLeast(
                                    1
                                )
                        )
                )
            )
        }
    )

    LongField(
        label = "聊天总超时",
        value =
            timeout.chatCallSeconds,
        supportingText =
            "填写 0 表示不设置总超时",
        onValueChange = {
            onChange(
                settings.copy(
                    timeouts =
                        timeout.copy(
                            chatCallSeconds =
                                it.coerceAtLeast(
                                    0
                                )
                        )
                )
            )
        }
    )

    HorizontalDivider()
    SectionTitle("图片超时（秒）")

    LongField(
        label = "图片连接超时",
        value =
            timeout.imageConnectSeconds,
        onValueChange = {
            onChange(
                settings.copy(
                    timeouts =
                        timeout.copy(
                            imageConnectSeconds =
                                it.coerceAtLeast(
                                    1
                                )
                        )
                )
            )
        }
    )

    LongField(
        label = "图片生成读取超时",
        value =
            timeout.imageReadSeconds,
        onValueChange = {
            onChange(
                settings.copy(
                    timeouts =
                        timeout.copy(
                            imageReadSeconds =
                                it.coerceAtLeast(
                                    1
                                )
                        )
                )
            )
        }
    )

    LongField(
        label = "图生图上传超时",
        value =
            timeout.imageWriteSeconds,
        onValueChange = {
            onChange(
                settings.copy(
                    timeouts =
                        timeout.copy(
                            imageWriteSeconds =
                                it.coerceAtLeast(
                                    1
                                )
                        )
                )
            )
        }
    )

    LongField(
        label = "图片请求总超时",
        value =
            timeout.imageCallSeconds,
        supportingText =
            "默认 720 秒。不能延长中转站自身的 502/504 网关超时。",
        onValueChange = {
            onChange(
                settings.copy(
                    timeouts =
                        timeout.copy(
                            imageCallSeconds =
                                it.coerceAtLeast(
                                    0
                                )
                        )
                )
            )
        }
    )

    LongField(
        label = "图片下载超时",
        value =
            timeout.imageDownloadSeconds,
        onValueChange = {
            onChange(
                settings.copy(
                    timeouts =
                        timeout.copy(
                            imageDownloadSeconds =
                                it.coerceAtLeast(
                                    1
                                )
                        )
                )
            )
        }
    )

    HorizontalDivider()
    SectionTitle("搜索与工具超时（秒）")

    LongField(
        label = "搜索连接超时",
        value =
            timeout.searchConnectSeconds,
        onValueChange = {
            onChange(
                settings.copy(
                    timeouts =
                        timeout.copy(
                            searchConnectSeconds =
                                it.coerceAtLeast(
                                    1
                                )
                        )
                )
            )
        }
    )

    LongField(
        label = "搜索读取超时",
        value =
            timeout.searchReadSeconds,
        onValueChange = {
            onChange(
                settings.copy(
                    timeouts =
                        timeout.copy(
                            searchReadSeconds =
                                it.coerceAtLeast(
                                    1
                                )
                        )
                )
            )
        }
    )

    LongField(
        label = "搜索总超时",
        value =
            timeout.searchCallSeconds,
        onValueChange = {
            onChange(
                settings.copy(
                    timeouts =
                        timeout.copy(
                            searchCallSeconds =
                                it.coerceAtLeast(
                                    1
                                )
                        )
                )
            )
        }
    )

    LongField(
        label = "Tool Calls 决策超时",
        value =
            timeout.toolDecisionSeconds,
        onValueChange = {
            onChange(
                settings.copy(
                    timeouts =
                        timeout.copy(
                            toolDecisionSeconds =
                                it.coerceAtLeast(
                                    1
                                )
                        )
                )
            )
        }
    )

    HorizontalDivider()
    SectionTitle("重试")

    EnumSelector(
        label = "重试模式",
        current = retry.mode.name,
        values =
            RetryMode.entries.map {
                it.name
            },
        onSelect = {
            onChange(
                settings.copy(
                    retry =
                        retry.copy(
                            mode =
                                enumValueOrDefault(
                                    it,
                                    retry.mode
                                )
                        )
                )
            )
        }
    )

    NumberField(
        label = "最大尝试次数",
        value =
            retry.maximumAttempts,
        onValueChange = {
            onChange(
                settings.copy(
                    retry =
                        retry.copy(
                            maximumAttempts =
                                it.coerceIn(
                                    1,
                                    10
                                )
                        )
                )
            )
        }
    )

    LongField(
        label = "首次重试等待秒数",
        value =
            retry.initialDelaySeconds,
        onValueChange = {
            onChange(
                settings.copy(
                    retry =
                        retry.copy(
                            initialDelaySeconds =
                                it.coerceAtLeast(
                                    0
                                )
                        )
                )
            )
        }
    )

    SwitchRow(
        title = "指数退避",
        checked =
            retry.exponentialBackoff,
        onCheckedChange = {
            onChange(
                settings.copy(
                    retry =
                        retry.copy(
                            exponentialBackoff =
                                it
                        )
                )
            )
        }
    )

    TextFieldSetting(
        label = "允许重试的 HTTP 状态码",
        value =
            retry.retryStatusCodes
                .sorted()
                .joinToString(","),
        supportingText =
            "例如 429,502,503,504",
        onValueChange = {
            onChange(
                settings.copy(
                    retry =
                        retry.copy(
                            retryStatusCodes =
                                parseIntegerSet(
                                    it
                                )
                        )
                )
            )
        }
    )

    SwitchRow(
        title = "网络错误允许重试",
        checked =
            retry.retryNetworkErrors,
        onCheckedChange = {
            onChange(
                settings.copy(
                    retry =
                        retry.copy(
                            retryNetworkErrors =
                                it
                        )
                )
            )
        }
    )

    SwitchRow(
        title = "图片请求自动重试",
        description =
            "可能重复生成和扣费，建议关闭并手动重试",
        checked =
            retry.retryImageRequestsAutomatically,
        onCheckedChange = {
            onChange(
                settings.copy(
                    retry =
                        retry.copy(
                            retryImageRequestsAutomatically =
                                it
                        )
                )
            )
        }
    )
}

@Composable
private fun DiagnosticsSection(
    settings: AppSettings,
    onChange: (AppSettings) -> Unit
) {
    val diagnostics =
        settings.diagnostics

    SwitchRow(
        title = "显示详细错误",
        checked =
            diagnostics.detailedErrors,
        onCheckedChange = {
            onChange(
                settings.copy(
                    diagnostics =
                        diagnostics.copy(
                            detailedErrors = it
                        )
                )
            )
        }
    )

    SwitchRow(
        title = "显示请求耗时",
        checked =
            diagnostics.showDuration,
        onCheckedChange = {
            onChange(
                settings.copy(
                    diagnostics =
                        diagnostics.copy(
                            showDuration = it
                        )
                )
            )
        }
    )

    SwitchRow(
        title = "显示 HTTP 状态码",
        checked =
            diagnostics.showHttpStatus,
        onCheckedChange = {
            onChange(
                settings.copy(
                    diagnostics =
                        diagnostics.copy(
                            showHttpStatus = it
                        )
                )
            )
        }
    )

    SwitchRow(
        title = "显示 Content-Type",
        checked =
            diagnostics.showContentType,
        onCheckedChange = {
            onChange(
                settings.copy(
                    diagnostics =
                        diagnostics.copy(
                            showContentType = it
                        )
                )
            )
        }
    )

    SwitchRow(
        title = "显示服务器名称",
        checked =
            diagnostics.showServerName,
        onCheckedChange = {
            onChange(
                settings.copy(
                    diagnostics =
                        diagnostics.copy(
                            showServerName = it
                        )
                )
            )
        }
    )

    SwitchRow(
        title = "显示 Request ID",
        checked =
            diagnostics.showRequestId,
        onCheckedChange = {
            onChange(
                settings.copy(
                    diagnostics =
                        diagnostics.copy(
                            showRequestId = it
                        )
                )
            )
        }
    )

    SwitchRow(
        title = "显示接口路径",
        checked =
            diagnostics.showEndpointPath,
        onCheckedChange = {
            onChange(
                settings.copy(
                    diagnostics =
                        diagnostics.copy(
                            showEndpointPath = it
                        )
                )
            )
        }
    )

    SwitchRow(
        title = "显示模型名称",
        checked =
            diagnostics.showModel,
        onCheckedChange = {
            onChange(
                settings.copy(
                    diagnostics =
                        diagnostics.copy(
                            showModel = it
                        )
                )
            )
        }
    )

    SwitchRow(
        title = "保存诊断记录",
        checked =
            diagnostics.saveDiagnosticHistory,
        onCheckedChange = {
            onChange(
                settings.copy(
                    diagnostics =
                        diagnostics.copy(
                            saveDiagnosticHistory =
                                it
                        )
                )
            )
        }
    )

    NumberField(
        label = "诊断记录保留天数",
        value =
            diagnostics.diagnosticRetentionDays,
        onValueChange = {
            onChange(
                settings.copy(
                    diagnostics =
                        diagnostics.copy(
                            diagnosticRetentionDays =
                                it.coerceIn(
                                    1,
                                    3650
                                )
                        )
                )
            )
        }
    )

    MultilineField(
        label = "Request ID 响应头名称",
        value =
            diagnostics.requestIdHeaderNames
                .joinToString("\n"),
        onValueChange = {
            onChange(
                settings.copy(
                    diagnostics =
                        diagnostics.copy(
                            requestIdHeaderNames =
                                linesToList(it)
                        )
                )
            )
        }
    )
}

@Composable
fun ImageParametersDialog(
    settings: AppSettings,
    onSave: (AppSettings) -> Unit,
    onDismiss: () -> Unit
) {
    var size by remember {
        mutableStateOf(
            settings.imageParameters.size
        )
    }

    var quality by remember {
        mutableStateOf(
            settings.imageParameters.quality
        )
    }

    var count by remember {
        mutableStateOf(
            settings.imageParameters.count
                .toString()
        )
    }

    var responseFormat by remember {
        mutableStateOf(
            settings.imageParameters
                .responseFormat
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("图片参数")
        },
        text = {
            Column(
                verticalArrangement =
                    Arrangement.spacedBy(10.dp)
            ) {
                TextFieldSetting(
                    label = "尺寸",
                    value = size,
                    supportingText =
                        settings.imageParameters
                            .generatedSizePresets
                            .joinToString("、"),
                    onValueChange = {
                        size = it
                    }
                )

                TextFieldSetting(
                    label = "质量",
                    value = quality,
                    supportingText =
                        settings.imageParameters
                            .qualityPresets
                            .joinToString("、"),
                    onValueChange = {
                        quality = it
                    }
                )

                TextFieldSetting(
                    label = "数量",
                    value = count,
                    onValueChange = {
                        count =
                            it.filter(
                                Char::isDigit
                            )
                    }
                )

                TextFieldSetting(
                    label = "返回格式",
                    value = responseFormat,
                    supportingText =
                        "url 或 b64_json",
                    onValueChange = {
                        responseFormat = it
                    }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        settings.copy(
                            imageParameters =
                                settings
                                    .imageParameters
                                    .copy(
                                        size =
                                            size.trim()
                                                .ifBlank {
                                                    "1024x1024"
                                                },
                                        quality =
                                            quality.trim()
                                                .ifBlank {
                                                    "standard"
                                                },
                                        count =
                                            count.toIntOrNull()
                                                ?.coerceIn(
                                                    1,
                                                    10
                                                )
                                                ?: 1,
                                        responseFormat =
                                            responseFormat
                                                .trim()
                                                .ifBlank {
                                                    "b64_json"
                                                }
                                    )
                        )
                    )

                    onDismiss()
                }
            ) {
                Text("应用")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss
            ) {
                Text("取消")
            }
        }
    )
}

@Composable
fun ApiProfilesDialog(
    state: AppUiState,
    viewModel: AppViewModel,
    onDismiss: () -> Unit
) {
    var editingProfile by remember {
        mutableStateOf<ApiProfileEntity?>(
            null
        )
    }

    var creating by remember {
        mutableStateOf(false)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("聊天与图片 API 线路")
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(560.dp)
                    .verticalScroll(
                        rememberScrollState()
                    ),
                verticalArrangement =
                    Arrangement.spacedBy(6.dp)
            ) {
                Button(
                    onClick = {
                        creating = true
                    },
                    modifier =
                        Modifier.fillMaxWidth()
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = null
                    )

                    Text("新增线路")
                }

                state.apiProfiles.forEach {
                    profile ->
                    ProfileRow(
                        title = profile.name,
                        subtitle =
                            "${profile.baseUrl}\n" +
                                "聊天：${profile.chatModel} · 图片：${profile.imageModel}",
                        active = profile.isActive,
                        onActivate = {
                            viewModel
                                .activateApiProfile(
                                    profile.id
                                )
                        },
                        onEdit = {
                            editingProfile =
                                profile
                        },
                        onDelete = {
                            viewModel
                                .deleteApiProfile(
                                    profile.id
                                )
                        }
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("关闭")
            }
        }
    )

    if (creating) {
        ApiProfileEditor(
            profile = null,
            onFetchModels = viewModel::fetchModels,
            onSave = { profile, key ->
                /*
                 * 使用 saveApiProfile 保存整个实体，
                 * 确保高级接口路径、认证和请求头不会丢失。
                 */
                viewModel.saveApiProfile(
                    profile = profile,
                    newApiKey = key
                )

                creating = false
            },
            onDismiss = {
                creating = false
            }
        )
    }

    editingProfile?.let { profile ->
        ApiProfileEditor(
            profile = profile,
            onFetchModels = viewModel::fetchModels,
            onSave = { updated, key ->
                viewModel.saveApiProfile(
                    updated,
                    key
                )

                editingProfile = null
            },
            onDismiss = {
                editingProfile = null
            }
        )
    }
}

@Composable
private fun ApiProfileEditor(
    profile: ApiProfileEntity?,
    onFetchModels: (
        String,
        (Result<List<String>>) -> Unit
    ) -> Unit,
    onSave: (
        ApiProfileEntity,
        String?
    ) -> Unit,
    onDismiss: () -> Unit
) {
    val base = profile
        ?: ApiProfileEntity(
            id = UUID.randomUUID().toString(),
            name = "新线路",
            baseUrl = "",
            encryptedApiKeyAlias = "",
            chatModel = "",
            imageModel = ""
        )

    var draft by remember(base) {
        mutableStateOf(base)
    }

    var apiKey by remember {
        mutableStateOf("")
    }

    var availableModels by remember(base.id) {
        mutableStateOf<List<String>>(emptyList())
    }

    var fetchingModels by remember { mutableStateOf(false) }
    var modelFetchError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (profile == null) {
                    "新增 API 线路"
                } else {
                    "编辑 API 线路"
                }
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(600.dp)
                    .verticalScroll(
                        rememberScrollState()
                    ),
                verticalArrangement =
                    Arrangement.spacedBy(8.dp)
            ) {
                TextFieldSetting(
                    label = "线路名称",
                    value = draft.name,
                    onValueChange = {
                        draft =
                            draft.copy(name = it)
                    }
                )

                TextFieldSetting(
                    label = "Base URL",
                    value = draft.baseUrl,
                    supportingText =
                        "例如 https://example.com/v1",
                    onValueChange = {
                        draft =
                            draft.copy(
                                baseUrl = it
                            )
                    }
                )

                OutlinedTextField(
                    value = apiKey,
                    onValueChange = {
                        apiKey = it
                    },
                    modifier =
                        Modifier.fillMaxWidth(),
                    label = {
                        Text(
                            if (profile == null) {
                                "API Key"
                            } else {
                                "新 API Key（留空保持不变）"
                            }
                        )
                    },
                    visualTransformation =
                        PasswordVisualTransformation(),
                    singleLine = true
                )

                OutlinedButton(
                    onClick = {
                        fetchingModels = true
                        modelFetchError = null
                        onFetchModels(base.id) { result ->
                            fetchingModels = false
                            result.onSuccess { availableModels = it }
                                .onFailure {
                                    modelFetchError = it.message ?: "获取模型列表失败"
                                }
                        }
                    },
                    enabled = profile != null && !fetchingModels,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        when {
                            fetchingModels -> "正在获取模型..."
                            availableModels.isNotEmpty() ->
                                "已获取 ${availableModels.size} 个模型，点击刷新"
                            profile == null -> "保存线路后可获取模型列表"
                            else -> "一键获取模型列表"
                        }
                    )
                }

                modelFetchError?.let {
                    Text(it, color = MaterialTheme.colorScheme.error)
                }

                if (availableModels.isNotEmpty()) {
                    EnumSelector(
                        label = "从列表选择聊天模型",
                        current = draft.chatModel,
                        values = availableModels,
                        onSelect = { draft = draft.copy(chatModel = it) }
                    )
                    EnumSelector(
                        label = "从列表选择视觉模型",
                        current = draft.visionModel,
                        values = availableModels,
                        onSelect = { draft = draft.copy(visionModel = it) }
                    )
                    EnumSelector(
                        label = "从列表选择图片模型",
                        current = draft.imageModel,
                        values = availableModels,
                        onSelect = { draft = draft.copy(imageModel = it) }
                    )
                }

                TextFieldSetting(
                    label = "聊天模型",
                    value = draft.chatModel,
                    onValueChange = {
                        draft =
                            draft.copy(
                                chatModel = it
                            )
                    }
                )

                TextFieldSetting(
                    label = "图片模型",
                    value = draft.imageModel,
                    onValueChange = {
                        draft =
                            draft.copy(
                                imageModel = it
                            )
                    }
                )

                TextFieldSetting(
                    label = "视觉模型",
                    value = draft.visionModel,
                    onValueChange = {
                        draft =
                            draft.copy(
                                visionModel = it
                            )
                    }
                )

                TextFieldSetting(
                    label = "提示词优化模型",
                    value =
                        draft.optimizerModel,
                    onValueChange = {
                        draft =
                            draft.copy(
                                optimizerModel =
                                    it
                            )
                    }
                )

                HorizontalDivider()
                SectionTitle("接口路径")

                EnumSelector(
                    label = "聊天请求格式",
                    current = if (
                        draft.chatPath.trimEnd('/').endsWith("/responses", true)
                    ) {
                        "RESPONSES"
                    } else {
                        "CHAT_COMPLETIONS"
                    },
                    values = listOf("CHAT_COMPLETIONS", "RESPONSES"),
                    onSelect = { format ->
                        draft = draft.copy(
                            chatPath = if (format == "RESPONSES") {
                                "/responses"
                            } else {
                                "/chat/completions"
                            }
                        )
                    }
                )

                TextFieldSetting(
                    label = "聊天接口",
                    value = draft.chatPath,
                    supportingText = "支持 /chat/completions 或 /responses，也可填写完整地址",
                    helpText = "Responses 使用独立请求和流式事件解析，并支持模型内置工具。",
                    onValueChange = {
                        draft =
                            draft.copy(
                                chatPath = it
                            )
                    }
                )

                TextFieldSetting(
                    label = "文生图接口",
                    value =
                        draft.imageGenerationPath,
                    onValueChange = {
                        draft =
                            draft.copy(
                                imageGenerationPath =
                                    it
                            )
                    }
                )

                TextFieldSetting(
                    label = "图生图接口",
                    value =
                        draft.imageEditPath,
                    onValueChange = {
                        draft =
                            draft.copy(
                                imageEditPath = it
                            )
                    }
                )

                TextFieldSetting(
                    label = "模型列表接口",
                    value = draft.modelsPath,
                    onValueChange = {
                        draft =
                            draft.copy(
                                modelsPath = it
                            )
                    }
                )

                HorizontalDivider()
                SectionTitle("认证")

                EnumSelector(
                    label = "认证方式",
                    current =
                        draft.authenticationMode,
                    values =
                        AuthenticationMode.entries
                            .map {
                                it.name
                            },
                    onSelect = {
                        draft =
                            draft.copy(
                                authenticationMode =
                                    it
                            )
                    }
                )

                TextFieldSetting(
                    label = "认证请求头名称",
                    value =
                        draft.authorizationHeaderName,
                    onValueChange = {
                        draft =
                            draft.copy(
                                authorizationHeaderName =
                                    it
                            )
                    }
                )

                TextFieldSetting(
                    label = "认证值前缀",
                    value =
                        draft.authorizationPrefix,
                    supportingText =
                        "例如 Bearer，末尾空格会保留",
                    onValueChange = {
                        draft =
                            draft.copy(
                                authorizationPrefix =
                                    it
                            )
                    }
                )

                MultilineField(
                    label = "自定义请求头 JSON",
                    value =
                        draft.customHeadersJson,
                    supportingText =
                        """例如 {"X-API-Version":"2026-01"}""",
                    onValueChange = {
                        draft =
                            draft.copy(
                                customHeadersJson =
                                    normalizeHeaderJson(
                                        it
                                    )
                            )
                    }
                )

                SwitchRow(
                    title = "启用该线路",
                    checked = draft.enabled,
                    onCheckedChange = {
                        draft =
                            draft.copy(
                                enabled = it
                            )
                    }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        draft.copy(
                            name =
                                draft.name.trim(),
                            baseUrl =
                                draft.baseUrl
                                    .trim()
                                    .trimEnd('/'),
                            updatedAt =
                                System
                                    .currentTimeMillis()
                        ),
                        apiKey.takeIf {
                            it.isNotBlank()
                        }
                    )
                },
                enabled =
                    draft.name.isNotBlank() &&
                        draft.baseUrl
                            .isNotBlank()
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

@Composable
fun SearchProfilesDialog(
    state: AppUiState,
    viewModel: AppViewModel,
    onDismiss: () -> Unit
) {
    var editingProfile by remember {
        mutableStateOf<SearchProfileEntity?>(
            null
        )
    }

    var creating by remember {
        mutableStateOf(false)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("搜索 API 线路")
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(560.dp)
                    .verticalScroll(
                        rememberScrollState()
                    ),
                verticalArrangement =
                    Arrangement.spacedBy(6.dp)
            ) {
                Button(
                    onClick = {
                        creating = true
                    },
                    modifier =
                        Modifier.fillMaxWidth()
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = null
                    )

                    Text("新增搜索线路")
                }

                if (state.searchProfiles.isEmpty()) {
                    Text(
                        "尚未配置搜索 API。Tool Calls 只负责请求调用搜索工具，仍需要实际搜索 API。"
                    )
                }

                state.searchProfiles.forEach {
                    profile ->
                    ProfileRow(
                        title = profile.name,
                        subtitle =
                            "${profile.requestMethod} ${profile.baseUrl}${profile.path}",
                        active = profile.isActive,
                        onActivate = {
                            viewModel
                                .activateSearchProfile(
                                    profile.id
                                )
                        },
                        onEdit = {
                            editingProfile =
                                profile
                        },
                        onDelete = {
                            viewModel
                                .deleteSearchProfile(
                                    profile.id
                                )
                        }
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("关闭")
            }
        }
    )

    if (creating) {
        SearchProfileEditor(
            profile = null,
            onSave = { profile, key ->
                /*
                 * 保存完整搜索配置，保留请求字段、
                 * 响应字段路径、认证方式和附加 JSON。
                 */
                viewModel.saveSearchProfile(
                    profile = profile,
                    newApiKey = key
                )

                creating = false
            },
            onDismiss = {
                creating = false
            }
        )
    }

    editingProfile?.let { profile ->
        SearchProfileEditor(
            profile = profile,
            onSave = { updated, key ->
                viewModel
                    .saveSearchProfile(
                        updated,
                        key
                    )

                editingProfile = null
            },
            onDismiss = {
                editingProfile = null
            }
        )
    }
}

@Composable
private fun SearchProfileEditor(
    profile: SearchProfileEntity?,
    onSave: (
        SearchProfileEntity,
        String?
    ) -> Unit,
    onDismiss: () -> Unit
) {
    val base = profile
        ?: SearchProfileEntity(
            id = UUID.randomUUID().toString(),
            name = "新搜索线路",
            baseUrl = "",
            path = "/search",
            encryptedApiKeyAlias = ""
        )

    var draft by remember(base) {
        mutableStateOf(base)
    }

    var apiKey by remember {
        mutableStateOf("")
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (profile == null) {
                    "新增搜索线路"
                } else {
                    "编辑搜索线路"
                }
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(620.dp)
                    .verticalScroll(
                        rememberScrollState()
                    ),
                verticalArrangement =
                    Arrangement.spacedBy(8.dp)
            ) {
                TextFieldSetting(
                    label = "线路名称",
                    value = draft.name,
                    onValueChange = {
                        draft =
                            draft.copy(name = it)
                    }
                )

                TextFieldSetting(
                    label = "搜索 Base URL",
                    value = draft.baseUrl,
                    onValueChange = {
                        draft =
                            draft.copy(
                                baseUrl = it
                            )
                    }
                )

                TextFieldSetting(
                    label = "搜索接口路径",
                    value = draft.path,
                    onValueChange = {
                        draft =
                            draft.copy(path = it)
                    }
                )

                OutlinedTextField(
                    value = apiKey,
                    onValueChange = {
                        apiKey = it
                    },
                    modifier =
                        Modifier.fillMaxWidth(),
                    label = {
                        Text(
                            if (profile == null) {
                                "搜索 API Key"
                            } else {
                                "新搜索 Key（留空保持不变）"
                            }
                        )
                    },
                    visualTransformation =
                        PasswordVisualTransformation(),
                    singleLine = true
                )

                TextFieldSetting(
                    label = "服务类型",
                    value =
                        draft.providerType,
                    onValueChange = {
                        draft =
                            draft.copy(
                                providerType = it
                            )
                    }
                )

                EnumSelector(
                    label = "请求方法",
                    current =
                        draft.requestMethod,
                    values =
                        listOf("GET", "POST"),
                    onSelect = {
                        draft =
                            draft.copy(
                                requestMethod = it
                            )
                    }
                )

                EnumSelector(
                    label = "认证方式",
                    current =
                        draft.authenticationMode,
                    values =
                        AuthenticationMode.entries
                            .map {
                                it.name
                            },
                    onSelect = {
                        draft =
                            draft.copy(
                                authenticationMode =
                                    it
                            )
                    }
                )

                TextFieldSetting(
                    label = "认证请求头",
                    value =
                        draft.authorizationHeaderName,
                    onValueChange = {
                        draft =
                            draft.copy(
                                authorizationHeaderName =
                                    it
                            )
                    }
                )

                TextFieldSetting(
                    label = "认证值前缀",
                    value =
                        draft.authorizationPrefix,
                    onValueChange = {
                        draft =
                            draft.copy(
                                authorizationPrefix =
                                    it
                            )
                    }
                )

                HorizontalDivider()
                SectionTitle("请求字段")

                TextFieldSetting(
                    label = "查询字段名",
                    value = draft.queryField,
                    onValueChange = {
                        draft =
                            draft.copy(
                                queryField = it
                            )
                    }
                )

                TextFieldSetting(
                    label = "结果数量字段名",
                    value = draft.countField,
                    onValueChange = {
                        draft =
                            draft.copy(
                                countField = it
                            )
                    }
                )

                TextFieldSetting(
                    label = "语言字段名",
                    value =
                        draft.languageField,
                    onValueChange = {
                        draft =
                            draft.copy(
                                languageField = it
                            )
                    }
                )

                TextFieldSetting(
                    label = "地区字段名",
                    value = draft.regionField,
                    onValueChange = {
                        draft =
                            draft.copy(
                                regionField = it
                            )
                    }
                )

                MultilineField(
                    label = "自定义请求头 JSON",
                    value =
                        draft.customHeadersJson,
                    onValueChange = {
                        draft =
                            draft.copy(
                                customHeadersJson =
                                    normalizeHeaderJson(
                                        it
                                    )
                            )
                    }
                )

                MultilineField(
                    label = "附加请求 JSON",
                    value =
                        draft.extraRequestJson,
                    onValueChange = {
                        draft =
                            draft.copy(
                                extraRequestJson =
                                    it
                            )
                    }
                )

                HorizontalDivider()
                SectionTitle("响应字段路径")

                TextFieldSetting(
                    label = "结果数组路径",
                    value =
                        draft.resultArrayPath,
                    onValueChange = {
                        draft =
                            draft.copy(
                                resultArrayPath = it
                            )
                    }
                )

                TextFieldSetting(
                    label = "标题字段",
                    value =
                        draft.resultTitlePath,
                    onValueChange = {
                        draft =
                            draft.copy(
                                resultTitlePath = it
                            )
                    }
                )

                TextFieldSetting(
                    label = "URL 字段",
                    value =
                        draft.resultUrlPath,
                    onValueChange = {
                        draft =
                            draft.copy(
                                resultUrlPath = it
                            )
                    }
                )

                TextFieldSetting(
                    label = "摘要字段",
                    value =
                        draft.resultSnippetPath,
                    onValueChange = {
                        draft =
                            draft.copy(
                                resultSnippetPath =
                                    it
                            )
                    }
                )

                TextFieldSetting(
                    label = "日期字段",
                    value =
                        draft.resultDatePath,
                    onValueChange = {
                        draft =
                            draft.copy(
                                resultDatePath = it
                            )
                    }
                )

                TextFieldSetting(
                    label = "来源字段",
                    value =
                        draft.resultSourcePath,
                    onValueChange = {
                        draft =
                            draft.copy(
                                resultSourcePath = it
                            )
                    }
                )

                SwitchRow(
                    title = "启用该搜索线路",
                    checked = draft.enabled,
                    onCheckedChange = {
                        draft =
                            draft.copy(
                                enabled = it
                            )
                    }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        draft.copy(
                            name =
                                draft.name.trim(),
                            baseUrl =
                                draft.baseUrl
                                    .trim()
                                    .trimEnd('/'),
                            updatedAt =
                                System
                                    .currentTimeMillis()
                        ),
                        apiKey.takeIf {
                            it.isNotBlank()
                        }
                    )
                },
                enabled =
                    draft.name.isNotBlank() &&
                        draft.baseUrl
                            .isNotBlank()
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

@Composable
private fun ProfileRow(
    title: String,
    subtitle: String,
    active: Boolean,
    onActivate: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment =
                Alignment.CenterVertically
        ) {
            TextButton(
                onClick = onActivate,
                modifier = Modifier.weight(1f)
            ) {
                Column(
                    modifier =
                        Modifier.fillMaxWidth()
                ) {
                    Text(
                        text =
                            if (active) {
                                "● $title"
                            } else {
                                title
                            },
                        color =
                            if (active) {
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
                        text = subtitle,
                        style = MaterialTheme
                            .typography
                            .bodySmall
                    )
                }
            }

            IconButton(onClick = onEdit) {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = "编辑"
                )
            }

            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "删除"
                )
            }
        }

        HorizontalDivider()
    }
}

@Composable
private fun SectionTitle(
    text: String
) {
    Text(
        text = text,
        style = MaterialTheme
            .typography
            .titleMedium
    )
}

@Composable
private fun SwitchRow(
    title: String,
    description: String = "",
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment =
            Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(title)

            if (description.isNotBlank()) {
                Text(
                    text = description,
                    style = MaterialTheme
                        .typography
                        .bodySmall
                )
            }
        }

        Switch(
            checked = checked,
            onCheckedChange =
                onCheckedChange
        )
    }
}

@Composable
private fun TextFieldSetting(
    label: String,
    value: String,
    supportingText: String = "",
    helpText: String = "",
    onValueChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange =
            onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = {
            Text(label)
        },
        trailingIcon = helpText.takeIf(String::isNotBlank)?.let {
            { ParameterHelpButton(label, it) }
        },
        supportingText =
            if (supportingText.isBlank()) {
                null
            } else {
                {
                    Text(supportingText)
                }
            },
        singleLine = true
    )
}

@Composable
private fun MultilineField(
    label: String,
    value: String,
    supportingText: String = "",
    onValueChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange =
            onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = {
            Text(label)
        },
        supportingText =
            if (supportingText.isBlank()) {
                null
            } else {
                {
                    Text(supportingText)
                }
            },
        minLines = 3,
        maxLines = 12
    )
}

@Composable
private fun NumberField(
    label: String,
    value: Int,
    supportingText: String = "",
    helpText: String = "",
    onValueChange: (Int) -> Unit
) {
    var text by remember { mutableStateOf(value.toString()) }
    var validationMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(text) {
        delay(450)
        text.toIntOrNull()?.let(onValueChange)
    }

    LaunchedEffect(value) {
        val entered = text.toIntOrNull()
        if (entered != null && entered != value) {
            validationMessage = "输入值 $entered 不受支持，已修正为 $value"
            text = value.toString()
        } else if (entered == value) {
            validationMessage = null
        }
    }

    OutlinedTextField(
        value = text,
        onValueChange = { newText ->
            text = newText.filter(Char::isDigit)
        },
        modifier = Modifier.fillMaxWidth(),
        label = {
            Text(label)
        },
        trailingIcon = helpText.takeIf(String::isNotBlank)?.let {
            { ParameterHelpButton(label, it) }
        },
        isError = text.isBlank(),
        supportingText =
            if (text.isBlank()) {
                { Text("请输入数字；清空时不会自动恢复默认值") }
            } else if (validationMessage != null) {
                { Text(validationMessage.orEmpty()) }
            } else if (supportingText.isBlank()) {
                null
            } else {
                {
                    Text(supportingText)
                }
            },
        keyboardOptions =
            KeyboardOptions(
                keyboardType =
                    KeyboardType.Number
            ),
        singleLine = true
    )
}

@Composable
private fun LongField(
    label: String,
    value: Long,
    supportingText: String = "",
    helpText: String = "",
    onValueChange: (Long) -> Unit
) {
    var text by remember {
        mutableStateOf(value.toString())
    }
    var validationMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(text) {
        delay(450)
        text.toLongOrNull()?.let(onValueChange)
    }

    LaunchedEffect(value) {
        val entered = text.toLongOrNull()
        if (entered != null && entered != value) {
            validationMessage = "输入值 $entered 不受支持，已修正为 $value"
            text = value.toString()
        } else if (entered == value) {
            validationMessage = null
        }
    }

    OutlinedTextField(
        value = text,
        onValueChange = { newText ->
            val filtered = newText.filter { character ->
                character.isDigit() ||
                    (
                        character == '-' &&
                            newText.indexOf(character) == 0
                        )
            }

            text = filtered

        },
        modifier = Modifier.fillMaxWidth(),
        label = {
            Text(label)
        },
        trailingIcon = helpText.takeIf(String::isNotBlank)?.let {
            { ParameterHelpButton(label, it) }
        },
        isError = text.isBlank() || text == "-",
        supportingText = if (text.isBlank() || text == "-") {
            { Text("请输入数字；清空时不会自动恢复默认值") }
        } else if (validationMessage != null) {
            { Text(validationMessage.orEmpty()) }
        } else if (supportingText.isBlank()) {
            null
        } else {
            {
                Text(supportingText)
            }
        },
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Number
        ),
        singleLine = true
    )
}

@Composable
private fun DecimalField(
    label: String,
    value: Double,
    supportingText: String = "",
    helpText: String = "",
    onValueChange: (Double) -> Unit
) {
    var text by remember(value) {
        mutableStateOf(
            formatEditableDecimal(value)
        )
    }

    OutlinedTextField(
        value = text,
        onValueChange = { newText ->
            val filtered = filterDecimalInput(
                newText
            )

            text = filtered

            filtered
                .replace(',', '.')
                .toDoubleOrNull()
                ?.let(onValueChange)
        },
        modifier = Modifier.fillMaxWidth(),
        label = {
            Text(label)
        },
        trailingIcon = helpText.takeIf(String::isNotBlank)?.let {
            { ParameterHelpButton(label, it) }
        },
        isError = text.isBlank() || text == "-" || text == ".",
        supportingText = if (supportingText.isBlank()) {
            null
        } else {
            {
                Text(supportingText)
            }
        },
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Decimal
        ),
        singleLine = true
    )
}

@Composable
private fun ParameterHelpButton(
    title: String,
    helpText: String
) {
    var showHelp by remember { mutableStateOf(false) }

    IconButton(
        onClick = { showHelp = true },
        modifier = Modifier.size(28.dp)
    ) {
        Icon(
            imageVector = Icons.Outlined.Info,
            contentDescription = "查看${title}说明",
            modifier = Modifier.size(15.dp)
        )
    }

    if (showHelp) {
        AlertDialog(
            onDismissRequest = { showHelp = false },
            title = { Text(title) },
            text = { Text(helpText) },
            confirmButton = {
                TextButton(onClick = { showHelp = false }) {
                    Text("知道了")
                }
            }
        )
    }
}

@Composable
private fun ParameterSwitchWithDecimal(
    title: String,
    enabled: Boolean,
    value: Double,
    onEnabledChange: (Boolean) -> Unit,
    onValueChange: (Double) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(
            6.dp
        )
    ) {
        SwitchRow(
            title = "发送 $title",
            checked = enabled,
            onCheckedChange = onEnabledChange
        )

        DecimalField(
            label = title,
            value = value,
            onValueChange = onValueChange
        )
    }
}

@Composable
private fun EnumSelector(
    label: String,
    current: String,
    values: List<String>,
    onSelect: (String) -> Unit
) {
    var expanded by remember {
        mutableStateOf(false)
    }

    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge
        )

        Spacer(
            modifier = Modifier.height(4.dp)
        )

        androidx.compose.foundation.layout.Box(
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedButton(
                onClick = {
                    expanded = true
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = current.takeIf(String::isNotBlank)
                        ?.let(::enumDisplayName)
                        ?: "请选择",
                    modifier = Modifier.weight(1f)
                )

                Text("▼")
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = {
                    expanded = false
                }
            ) {
                values.distinct().forEach { value ->
                    DropdownMenuItem(
                        text = {
                            Text(enumDisplayName(value))
                        },
                        onClick = {
                            onSelect(value)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

private fun enumDisplayName(value: String): String {
    return when (value) {
        "SYSTEM" -> "跟随系统"
        "LIGHT" -> "浅色"
        "DARK" -> "深色"
        "THIRD_PARTY" -> "第三方搜索 API"
        "MODEL_BUILT_IN" -> "模型内置工具"
        "CHAT_COMPLETIONS" -> "Chat Completions"
        "RESPONSES" -> "Responses"
        "OFF" -> "关闭"
        "AUTO" -> "自动"
        "ALWAYS" -> "始终开启"
        "DISABLED" -> "关闭"
        "OPENAI_TOOLS" -> "兼容 Tool Calls"
        else -> value
    }
}

private fun linesToList(
    value: String
): List<String> {
    return value
        .lineSequence()
        .map(String::trim)
        .filter(String::isNotBlank)
        .distinct()
        .toList()
}

private fun parseIntegerSet(
    value: String
): Set<Int> {
    return value
        .split(
            ",",
            "，",
            "\n",
            " ",
            ";",
            "；"
        )
        .mapNotNull {
            it.trim().toIntOrNull()
        }
        .filter {
            it in 100..599
        }
        .toSet()
}

private fun filterDecimalInput(
    value: String
): String {
    val result = StringBuilder()
    var hasDecimalSeparator = false

    value.forEachIndexed { index, character ->
        when {
            character.isDigit() -> {
                result.append(character)
            }

            (
                character == '.' ||
                    character == ','
                ) &&
                !hasDecimalSeparator -> {
                result.append(character)
                hasDecimalSeparator = true
            }

            character == '-' &&
                index == 0 -> {
                result.append(character)
            }
        }
    }

    return result.toString()
}

private fun formatEditableDecimal(
    value: Double
): String {
    return if (
        value.isFinite() &&
        value == value.toLong().toDouble()
    ) {
        value.toLong().toString()
    } else {
        value.toString()
    }
}

private fun normalizeHeaderJson(
    raw: String
): String {
    if (raw.isBlank()) {
        return "{}"
    }

    return try {
        JSONObject(raw).toString()
    } catch (_: Exception) {
        raw
    }
}

private inline fun <
    reified T : Enum<T>
    > enumValueOrDefault(
    raw: String,
    default: T
): T {
    return enumValues<T>().firstOrNull {
        it.name.equals(
            raw,
            ignoreCase = true
        )
    } ?: default
}
