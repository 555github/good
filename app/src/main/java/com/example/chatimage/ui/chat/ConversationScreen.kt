package com.example.chatimage.ui.chat

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.chatimage.data.model.RequestRoute
import com.example.chatimage.ui.AppUiState
import com.example.chatimage.ui.AppViewModel
import java.io.File

@Composable
fun ConversationScreen(
    state: AppUiState,
    viewModel: AppViewModel,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val focusManager = LocalFocusManager.current

    var input by remember(
        state.currentConversationId
    ) {
        mutableStateOf("")
    }

    var pendingImageUri by remember {
        mutableStateOf<Uri?>(null)
    }

    val imagePicker =
        rememberLauncherForActivityResult(
            contract =
                ActivityResultContracts
                    .GetContent()
        ) { uri: Uri? ->
            uri?.let {
                if (state.selectedRoute == RequestRoute.AUTO) {
                    pendingImageUri = it
                } else {
                    viewModel.selectAttachment(it)
                }
            }
        }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let(viewModel::selectFileAttachment)
    }

    LaunchedEffect(
        state.messages.size,
        state.messages
            .lastOrNull()
            ?.message
            ?.text
            ?.length
            ?.div(240),
        state.messages.lastOrNull()?.images?.size
    ) {
        val lastIndex = state.messages.lastIndex
        val lastVisible = listState.layoutInfo
            .visibleItemsInfo
            .lastOrNull()
            ?.index
            ?: lastIndex
        val nearBottom = lastVisible >= lastIndex - 1

        if (
            state.appSettings
                .appearance
                .autoScroll &&
            state.messages.isNotEmpty() &&
            nearBottom
        ) {
            listState.scrollToItem(lastIndex)
        }
    }

    Column(
        modifier = modifier
    ) {
        if (state.messages.isEmpty()) {
            EmptyConversation(
                state = state,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            )
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp),
                verticalArrangement =
                    Arrangement.spacedBy(
                        state.appSettings.appearance
                            .messageSpacingDp
                            .coerceIn(2, 24)
                            .dp
                    )
            ) {
                items(
                    items = state.messages,
                    key = {
                        it.message.id
                    }
                ) { message ->
                    MessageCard(
                        model = message,
                        appearance =
                            state.appSettings
                                .appearance,
                        diagnosticsSettings =
                            state.appSettings
                                .diagnostics,
                        onDelete = {
                            viewModel.deleteMessage(
                                message.message.id
                            )
                        },
                        onRetry = {
                            viewModel
                                .retryAssistantMessage(
                                    message.message.id
                                )
                        },
                        onImageClick = {
                            viewModel
                                .setImageViewer(it)
                        },
                        onContinueEditing = {
                            viewModel
                                .continueEditingImage(it)
                        },
                        onRegenerateImage = {
                            viewModel
                                .regenerateImage(it)
                        },
                        onError = {
                            viewModel.showError(it)
                        }
                    )
                }

                item {
                    Spacer(
                        modifier =
                            Modifier.height(4.dp)
                    )
                }
            }
        }

        if (state.statusText.isNotBlank()) {
            Text(
                text = state.statusText,
                style = MaterialTheme
                    .typography
                    .bodySmall,
                modifier = Modifier.padding(
                    horizontal = 14.dp,
                    vertical = 3.dp
                )
            )
        }

        if (
            state.selectedRoute ==
                RequestRoute
                    .IMAGE_GENERATION ||
            state.selectedRoute ==
                RequestRoute.IMAGE_EDIT ||
            state.selectedRoute ==
                RequestRoute.AUTO
        ) {
            ImageParameterSummary(
                state = state,
                onOpenParameters = {
                    viewModel
                        .setShowImageParameters(
                            true
                        )
                }
            )
        }

        state.attachedImagePath?.let {
            path ->
            SourceImagePreview(
                path = path,
                label =
                    if (
                        state.selectedRoute ==
                        RequestRoute.VISION_CHAT
                    ) {
                        "已附加图片，将发送给视觉模型"
                    } else {
                        "已附加图片，将优先进行图生图"
                    },
                onRemove = {
                    viewModel.clearAttachment()
                }
            )
        }

        state.referencedImagePath?.let {
            path ->
            SourceImagePreview(
                path = path,
                label =
                    "正在编辑指定的上文图片",
                onRemove = {
                    viewModel
                        .clearReferencedImage()
                }
            )
        }

        state.attachedFilePath?.let { path ->
            FileAttachmentPreview(
                path = path,
                name = state.attachedFileName ?: File(path).name,
                onRemove = viewModel::clearFileAttachment
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment =
                Alignment.Bottom
        ) {
            IconButton(
                onClick = {
                    imagePicker.launch(
                        "image/*"
                    )
                },
                enabled = !state.loading,
                modifier = Modifier.size(52.dp)
            ) {
                Icon(
                    imageVector =
                        Icons.Default.Image,
                    contentDescription =
                        "选择图片"
                )
            }

            IconButton(
                onClick = {
                    filePicker.launch(arrayOf("*/*"))
                },
                enabled = !state.loading,
                modifier = Modifier.size(52.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.AttachFile,
                    contentDescription = "选择文件"
                )
            }

            IconButton(
                onClick = {
                    viewModel
                        .cycleSearchForNextRequest()
                },
                enabled = !state.loading,
                modifier = Modifier.size(52.dp)
            ) {
                Icon(
                    imageVector =
                        Icons.Default.Public,
                    contentDescription =
                        "本次联网搜索",
                    tint =
                        if (
                            state
                                .forceSearchForNextRequest
                        ) {
                            MaterialTheme
                                .colorScheme
                                .primary
                        } else {
                            MaterialTheme
                                .colorScheme
                                .onSurfaceVariant
                        }
                )
            }

            OutlinedTextField(
                value = input,
                onValueChange = {
                    input = it

                    if (it.isNotBlank()) {
                        viewModel.previewRoute(it)
                    }
                },
                modifier = Modifier.weight(1f),
                placeholder = {
                    Text(
                        inputPlaceholder(state)
                    )
                },
                supportingText = {
                    val preview =
                        state.routePreview

                    if (preview != null) {
                        Text(
                            "预计模式：" +
                                routeDisplayName(
                                    preview.route
                                )
                        )
                    } else if (
                        state.forceSearchForNextRequest
                    ) {
                        Text("本次强制联网")
                    }
                },
                minLines = 1,
                maxLines = 6,
                enabled = !state.loading
            )

            Spacer(
                modifier = Modifier.width(4.dp)
            )

            if (state.loading) {
                IconButton(
                    onClick = {
                        viewModel.stopGeneration()
                    },
                    modifier = Modifier.size(52.dp)
                ) {
                    Icon(
                        imageVector =
                            Icons.Default.Stop,
                        contentDescription =
                            "停止生成"
                    )
                }
            } else {
                IconButton(
                    onClick = {
                        if (input.isNotBlank()) {
                            viewModel.send(input)

                            input = ""

                            if (
                                state.appSettings
                                    .appearance
                                    .dismissKeyboardOnSend
                            ) {
                                focusManager
                                    .clearFocus()
                            }
                        }
                    },
                    modifier = Modifier.size(52.dp)
                ) {
                    Icon(
                        imageVector =
                            Icons.Default.Send,
                        contentDescription =
                            "发送"
                    )
                }
            }
        }
    }

    pendingImageUri?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingImageUri = null },
            title = { Text("这张图片要用来做什么？") },
            text = {
                Text("选择“理解图片”会把图片交给视觉模型分析；选择“编辑图片”会进入图生图。")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.setSelectedRoute(RequestRoute.VISION_CHAT)
                        viewModel.selectAttachment(uri)
                        pendingImageUri = null
                    }
                ) {
                    Text("理解图片")
                }
            },
            dismissButton = {
                Row {
                    TextButton(
                        onClick = {
                            viewModel.setSelectedRoute(RequestRoute.IMAGE_EDIT)
                            viewModel.selectAttachment(uri)
                            pendingImageUri = null
                        }
                    ) {
                        Text("编辑图片")
                    }
                    TextButton(onClick = { pendingImageUri = null }) {
                        Text("取消")
                    }
                }
            }
        )
    }
}

@Composable
private fun EmptyConversation(
    state: AppUiState,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(24.dp),
        verticalArrangement =
            Arrangement.Center,
        horizontalAlignment =
            Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector =
                Icons.Default.AutoFixHigh,
            contentDescription = null
        )

        Spacer(
            modifier = Modifier.height(12.dp)
        )

        Text(
            text = "开始新的对话",
            style = MaterialTheme
                .typography
                .headlineSmall
        )

        Spacer(
            modifier = Modifier.height(8.dp)
        )

        Text(
            text = when (
                state.selectedRoute
            ) {
                RequestRoute.AUTO ->
                    "输入问题、图片生成要求，或上传图片后描述修改要求。"

                RequestRoute.CHAT ->
                    "当前使用语言模型聊天。"

                RequestRoute.IMAGE_GENERATION ->
                    "描述你想生成的图片。"

                RequestRoute.IMAGE_EDIT ->
                    "上传图片，或点击上文图片的“继续编辑”。"

                RequestRoute.VISION_CHAT ->
                    "上传图片并提出分析问题。"
            }
        )
    }
}

@Composable
private fun ImageParameterSummary(
    state: AppUiState,
    onOpenParameters: () -> Unit
) {
    val parameters =
        state.appSettings.imageParameters

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = 10.dp,
                vertical = 2.dp
            ),
        horizontalArrangement =
            Arrangement.spacedBy(6.dp)
    ) {
        AssistChip(
            onClick = onOpenParameters,
            label = {
                Text(parameters.size)
            },
            leadingIcon = {
                Icon(
                    imageVector =
                        Icons.Default.Image,
                    contentDescription = null
                )
            }
        )

        AssistChip(
            onClick = onOpenParameters,
            label = {
                Text(parameters.quality)
            }
        )

        AssistChip(
            onClick = onOpenParameters,
            label = {
                Text(
                    "${parameters.count} 张"
                )
            }
        )
    }
}

@Composable
private fun SourceImagePreview(
    path: String,
    label: String,
    onRemove: () -> Unit
) {
    val file = File(path)

    if (!file.exists()) {
        return
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = 10.dp,
                vertical = 3.dp
            )
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment =
                Alignment.CenterVertically
        ) {
            AsyncImage(
                model = file,
                contentDescription =
                    "源图片",
                modifier = Modifier
                    .width(72.dp)
                    .height(72.dp),
                contentScale =
                    ContentScale.Crop
            )

            Spacer(
                modifier = Modifier.width(8.dp)
            )

            Text(
                text = label,
                modifier = Modifier.weight(1f)
            )

            TextButton(
                onClick = onRemove
            ) {
                Text("取消")
            }
        }
    }
}

@Composable
private fun FileAttachmentPreview(
    path: String,
    name: String,
    onRemove: () -> Unit
) {
    val file = File(path)
    if (!file.exists()) return

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 3.dp)
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.InsertDriveFile,
                contentDescription = null,
                modifier = Modifier.size(30.dp)
            )
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(name, maxLines = 1)
                Text(
                    formatFileSize(file.length()),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            TextButton(onClick = onRemove) {
                Text("移除")
            }
        }
    }
}

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes >= 1024L * 1024L ->
            String.format("%.1f MB", bytes / 1024.0 / 1024.0)
        bytes >= 1024L ->
            String.format("%.1f KB", bytes / 1024.0)
        else -> "$bytes B"
    }
}

private fun inputPlaceholder(
    state: AppUiState
): String {
    return when {
        state.attachedFilePath != null ->
            "输入关于该文件的问题"

        state.attachedImagePath != null &&
            state.selectedRoute ==
            RequestRoute.VISION_CHAT ->
            "输入对这张图片的分析问题"

        state.referencedImagePath != null ->
            "描述如何修改这张图片"

        state.attachedImagePath != null ->
            "描述如何修改上传的图片"

        state.selectedRoute ==
            RequestRoute.IMAGE_GENERATION ->
            "描述想生成的图片"

        state.selectedRoute ==
            RequestRoute.IMAGE_EDIT ->
            "描述图片修改要求"

        else -> "输入消息"
    }
}

private fun routeDisplayName(
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
