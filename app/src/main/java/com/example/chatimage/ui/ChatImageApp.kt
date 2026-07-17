package com.example.chatimage.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.chatimage.data.model.RequestRoute
import com.example.chatimage.ui.chat.ConversationScreen
import com.example.chatimage.ui.chat.ImageViewerDialog
import com.example.chatimage.ui.history.HistoryDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatImageApp(
    viewModel: AppViewModel
) {
    val state by viewModel.uiState.collectAsState()

    var showRouteMenu by remember {
        mutableStateOf(false)
    }

    if (!state.initialized) {
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            LinearProgressIndicator()
        }

        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = state
                                .currentConversation
                                ?.title
                                ?: "ChatImage",
                            maxLines = 1
                        )

                        val secondaryText =
                            buildList {
                                if (
                                    state.appSettings
                                        .appearance
                                        .showProfileInTopBar
                                ) {
                                    state.activeApiProfile
                                        ?.name
                                        ?.takeIf {
                                            it.isNotBlank()
                                        }
                                        ?.let(::add)
                                }

                                if (
                                    state.appSettings
                                        .appearance
                                        .showModelInTopBar
                                ) {
                                    currentModelName(
                                        state
                                    ).takeIf {
                                        it.isNotBlank()
                                    }?.let(::add)
                                }
                            }.joinToString(" · ")

                        if (
                            secondaryText.isNotBlank()
                        ) {
                            Text(
                                text = secondaryText,
                                style = MaterialTheme
                                    .typography
                                    .bodySmall,
                                maxLines = 1
                            )
                        }
                    }
                },
                actions = {
                    Box {
                        TextButton(
                            onClick = {
                                showRouteMenu = true
                            }
                        ) {
                            Text(
                                routeLabel(
                                    state.selectedRoute
                                )
                            )
                        }

                        DropdownMenu(
                            expanded = showRouteMenu,
                            onDismissRequest = {
                                showRouteMenu = false
                            }
                        ) {
                            routeEntries.forEach {
                                route ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(
                                                routeLabel(
                                                    route
                                                )
                                            )

                                            Text(
                                                routeDescription(
                                                    route
                                                ),
                                                style = MaterialTheme
                                                    .typography
                                                    .bodySmall
                                            )
                                        }
                                    },
                                    onClick = {
                                        viewModel
                                            .setSelectedRoute(
                                                route
                                            )

                                        showRouteMenu = false
                                    }
                                )
                            }
                        }
                    }

                    IconButton(
                        onClick = {
                            viewModel.setShowHistory(
                                true
                            )
                        }
                    ) {
                        Icon(
                            imageVector =
                                Icons.Default.History,
                            contentDescription =
                                "历史对话"
                        )
                    }

                    IconButton(
                        onClick = {
                            viewModel.newConversation()
                        }
                    ) {
                        Icon(
                            imageVector =
                                Icons.Default.Add,
                            contentDescription =
                                "新建对话"
                        )
                    }

                    IconButton(
                        onClick = {
                            viewModel.setShowSettings(
                                true
                            )
                        }
                    ) {
                        Icon(
                            imageVector =
                                Icons.Default.Settings,
                            contentDescription =
                                "设置"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        ConversationScreen(
            state = state,
            viewModel = viewModel,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        )
    }

    if (state.showHistory) {
        HistoryDialog(
            state = state,
            onDismiss = {
                viewModel.setShowHistory(false)
            },
            onSelect = {
                viewModel.selectConversation(it)
            },
            onRename = { id, title ->
                viewModel.renameConversation(
                    id,
                    title
                )
            },
            onTogglePinned = {
                viewModel.togglePinned(it)
            },
            onDelete = {
                viewModel.deleteConversation(it)
            },
            onSearchQueryChange = {
                viewModel.setHistorySearchQuery(it)
            }
        )
    }

    state.selectedImageForViewer?.let {
        image ->
        ImageViewerDialog(
            image = image,
            onDismiss = {
                viewModel.setImageViewer(null)
            },
            onContinueEditing = {
                viewModel.continueEditingImage(
                    image
                )
            },
            onRegenerate = {
                viewModel.regenerateImage(
                    image
                )
            },
            onError = {
                viewModel.showError(it)
            }
        )
    }

    state.pendingOptimization?.let {
        pending ->
        PromptOptimizationDialog(
            pending = pending,
            onOptimize = {
                viewModel.optimizePendingPrompt()
            },
            onUseOriginal = {
                viewModel
                    .generateWithoutOptimization()
            },
            onUseOptimized = {
                viewModel.useOptimizedPrompt()
            },
            onCancel = {
                viewModel
                    .cancelPromptOptimization()
            }
        )
    }

    state.pendingRouteConfirmation?.let {
        pending ->
        AlertDialog(
            onDismissRequest = {
                viewModel
                    .cancelRouteConfirmation()
            },
            title = {
                Text("确认图片编辑")
            },
            text = {
                Column {
                    Text(
                        pending.decision.reason
                    )

                    if (
                        pending.decision
                            .sourceImagePath
                            .isNullOrBlank()
                    ) {
                        Text(
                            "没有找到可编辑的源图片，请先选择图片。",
                            color = MaterialTheme
                                .colorScheme
                                .error
                        )
                    } else {
                        Text(
                            "本次请求将使用最近生成或指定的图片作为源图。"
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel
                            .confirmRouteDecision()
                    },
                    enabled =
                        !pending.decision
                            .sourceImagePath
                            .isNullOrBlank()
                ) {
                    Text("继续")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        viewModel
                            .cancelRouteConfirmation()
                    }
                ) {
                    Text("取消")
                }
            }
        )
    }

    state.globalError?.let { error ->
        AlertDialog(
            onDismissRequest = {
                viewModel.clearError()
            },
            title = {
                Text("错误")
            },
            text = {
                androidx.compose.foundation
                    .text.selection
                    .SelectionContainer {
                        Text(error)
                    }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearError()
                    }
                ) {
                    Text("确定")
                }
            }
        )
    }

    /*
     * 设置界面将在下一批交付。当前状态保留，等下一批
     * SettingsDialog 加入后，点击齿轮即可打开完整图形设置。
     */
}

@Composable
private fun PromptOptimizationDialog(
    pending: PendingPromptOptimization,
    onOptimize: () -> Unit,
    onUseOriginal: () -> Unit,
    onUseOptimized: () -> Unit,
    onCancel: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = {
            Text("提示词较长")
        },
        text = {
            Column {
                Text(
                    "长提示词可能使部分中转站发生 502 或 504。可以先使用聊天模型压缩提示词。"
                )

                Text(
                    "原始长度：${pending.originalPrompt.length} 字符",
                    style = MaterialTheme
                        .typography
                        .bodySmall
                )

                if (pending.optimizing) {
                    LinearProgressIndicator()
                    Text("正在优化提示词……")
                }

                pending.optimizedPrompt?.let {
                    optimized ->
                    Text(
                        "优化结果：",
                        style = MaterialTheme
                            .typography
                            .titleSmall
                    )

                    androidx.compose.foundation
                        .text.selection
                        .SelectionContainer {
                            Text(optimized)
                        }
                }

                pending.error?.let { error ->
                    Text(
                        text = error,
                        color = MaterialTheme
                            .colorScheme
                            .error
                    )
                }
            }
        },
        confirmButton = {
            if (
                pending.optimizedPrompt
                    .isNullOrBlank()
            ) {
                Button(
                    onClick = onOptimize,
                    enabled = !pending.optimizing
                ) {
                    Text("优化")
                }
            } else {
                Button(
                    onClick = onUseOptimized
                ) {
                    Text("使用优化结果")
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onUseOriginal,
                enabled = !pending.optimizing
            ) {
                Text("使用原文")
            }

            TextButton(
                onClick = onCancel,
                enabled = !pending.optimizing
            ) {
                Text("取消")
            }
        }
    )
}

private val routeEntries = listOf(
    RequestRoute.AUTO,
    RequestRoute.CHAT,
    RequestRoute.IMAGE_GENERATION,
    RequestRoute.IMAGE_EDIT
)

private fun routeLabel(
    route: RequestRoute
): String {
    return when (route) {
        RequestRoute.AUTO -> "自动"
        RequestRoute.CHAT -> "聊天"
        RequestRoute.IMAGE_GENERATION ->
            "文生图"

        RequestRoute.IMAGE_EDIT ->
            "图生图"

        RequestRoute.VISION_CHAT ->
            "视觉分析"
    }
}

private fun routeDescription(
    route: RequestRoute
): String {
    return when (route) {
        RequestRoute.AUTO ->
            "根据输入内容自动判断"

        RequestRoute.CHAT ->
            "使用语言模型回答"

        RequestRoute.IMAGE_GENERATION ->
            "使用图片模型生成新图片"

        RequestRoute.IMAGE_EDIT ->
            "修改上传或上文图片"

        RequestRoute.VISION_CHAT ->
            "使用视觉模型分析图片"
    }
}

private fun currentModelName(
    state: AppUiState
): String {
    val profile =
        state.activeApiProfile
            ?: return ""

    return when (state.selectedRoute) {
        RequestRoute.IMAGE_GENERATION,
        RequestRoute.IMAGE_EDIT -> {
            state.appSettings
                .imageParameters
                .modelOverride
                .ifBlank {
                    profile.imageModel
                }
        }

        RequestRoute.VISION_CHAT -> {
            profile.visionModel
                .ifBlank {
                    profile.chatModel
                }
        }

        else -> profile.chatModel
    }
}
