package com.example.chatimage.ui.chat

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.chatimage.data.database.MessageImageEntity
import com.example.chatimage.data.model.AppearanceSettings
import com.example.chatimage.data.model.DiagnosticsSettings
import com.example.chatimage.ui.MessageUiModel
import com.example.chatimage.util.ShareUtils
import java.io.File

@Composable
fun MessageCard(
    model: MessageUiModel,
    appearance: AppearanceSettings,
    diagnosticsSettings:
        DiagnosticsSettings,
    onDelete: () -> Unit,
    onRetry: () -> Unit,
    onImageClick:
        (MessageImageEntity) -> Unit,
    onContinueEditing:
        (MessageImageEntity) -> Unit,
    onRegenerateImage:
        (MessageImageEntity) -> Unit,
    onError: (String) -> Unit
) {
    val context = LocalContext.current

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement =
            if (model.isUser) {
                Arrangement.End
            } else {
                Arrangement.Start
            }
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(
                if (model.isUser) {
                    appearance
                        .messageWidthFraction
                        .coerceIn(
                            0.55f,
                            0.95f
                        )
                } else {
                    0.98f
                }
            ),
            shape = RoundedCornerShape(
                16.dp
            )
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement =
                    Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text =
                        if (model.isUser) {
                            "你"
                        } else {
                            "助手"
                        },
                    style = MaterialTheme
                        .typography
                        .labelMedium
                )

                model.message
                    .attachedImagePath
                    ?.let { path ->
                        val file = File(path)

                        if (file.exists()) {
                            AsyncImage(
                                model = file,
                                contentDescription =
                                    "上传图片",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(220.dp),
                                contentScale =
                                    ContentScale.Fit
                            )
                        }
                    }

                if (model.hasText) {
                    /*
                     * SelectionContainer 允许长按、拖动选择、
                     * 复制局部文本和全选。
                     */
                    SelectionContainer {
                        Text(
                            text =
                                model.message.text
                        )
                    }
                } else if (model.isRunning) {
                    Text("正在生成……")
                }

                model.images.forEach { image ->
                    val file =
                        File(image.localPath)

                    if (file.exists()) {
                        AsyncImage(
                            model = file,
                            contentDescription =
                                "生成图片",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(
                                    appearance
                                        .imagePreviewHeightDp
                                        .coerceIn(
                                            160,
                                            800
                                        )
                                        .dp
                                )
                                .clickable {
                                    onImageClick(
                                        image
                                    )
                                },
                            contentScale =
                                ContentScale.Fit
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(
                                    rememberScrollState()
                                ),
                            horizontalArrangement =
                                Arrangement.End
                        ) {
                            TextButton(
                                onClick = {
                                    onImageClick(image)
                                }
                            ) {
                                Icon(
                                    imageVector =
                                        Icons.Default.ZoomIn,
                                    contentDescription =
                                        null
                                )

                                Spacer(
                                    modifier =
                                        Modifier.width(
                                            4.dp
                                        )
                                )

                                Text("查看")
                            }

                            TextButton(
                                onClick = {
                                    onContinueEditing(
                                        image
                                    )
                                }
                            ) {
                                Icon(
                                    imageVector =
                                        Icons.Default.Edit,
                                    contentDescription =
                                        null
                                )

                                Spacer(
                                    modifier =
                                        Modifier.width(
                                            4.dp
                                        )
                                )

                                Text("继续编辑")
                            }

                            TextButton(
                                onClick = {
                                    ShareUtils
                                        .saveImageToGallery(
                                            context,
                                            file
                                        )
                                        .onFailure {
                                            onError(
                                                it.message
                                                    ?: "保存失败"
                                            )
                                        }
                                }
                            ) {
                                Icon(
                                    imageVector =
                                        Icons.Default.Save,
                                    contentDescription =
                                        null
                                )

                                Text("保存")
                            }

                            TextButton(
                                onClick = {
                                    ShareUtils
                                        .shareImage(
                                            context,
                                            file
                                        )
                                        .onFailure {
                                            onError(
                                                it.message
                                                    ?: "分享失败"
                                            )
                                        }
                                }
                            ) {
                                Icon(
                                    imageVector =
                                        Icons.Default.Share,
                                    contentDescription =
                                        null
                                )

                                Text("分享")
                            }

                            TextButton(
                                onClick = {
                                    onRegenerateImage(
                                        image
                                    )
                                }
                            ) {
                                Icon(
                                    imageVector =
                                        Icons.Default.Refresh,
                                    contentDescription =
                                        null
                                )

                                Text("重新生成")
                            }
                        }
                    } else {
                        Text(
                            text =
                                "本地图片文件已不存在",
                            color = MaterialTheme
                                .colorScheme
                                .error
                        )
                    }
                }

                if (model.citations.isNotEmpty()) {
                    HorizontalDivider()

                    Text(
                        text = "来源",
                        style = MaterialTheme
                            .typography
                            .titleSmall
                    )

                    model.citations.forEach {
                        citation ->
                        Text(
                            text =
                                "[${citation.index}] " +
                                    citation.title,
                            color = MaterialTheme
                                .colorScheme
                                .primary,
                            modifier =
                                if (
                                    citation.url
                                        .isNotBlank()
                                ) {
                                    Modifier.clickable {
                                        runCatching {
                                            context
                                                .startActivity(
                                                    Intent(
                                                        Intent.ACTION_VIEW,
                                                        Uri.parse(
                                                            citation.url
                                                        )
                                                    )
                                                )
                                        }.onFailure {
                                            onError(
                                                "无法打开来源链接"
                                            )
                                        }
                                    }
                                } else {
                                    Modifier
                                }
                        )
                    }
                }

                model.message.errorMessage?.let {
                    error ->
                    SelectionContainer {
                        Text(
                            text = error,
                            color = MaterialTheme
                                .colorScheme
                                .error
                        )
                    }
                }

                if (
                    diagnosticsSettings
                        .showDuration &&
                    model.message.durationMs != null
                ) {
                    Text(
                        text =
                            "耗时：${formatDuration(model.message.durationMs)}",
                        style = MaterialTheme
                            .typography
                            .bodySmall
                    )
                }

                if (
                    diagnosticsSettings
                        .showHttpStatus &&
                    model.message.httpStatus != null
                ) {
                    Text(
                        text =
                            "HTTP ${model.message.httpStatus}",
                        style = MaterialTheme
                            .typography
                            .bodySmall
                    )
                }

                if (
                    diagnosticsSettings
                        .showRequestId &&
                    !model.message.requestId
                        .isNullOrBlank()
                ) {
                    SelectionContainer {
                        Text(
                            text =
                                "Request ID：${model.message.requestId}",
                            style = MaterialTheme
                                .typography
                                .bodySmall
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(
                            rememberScrollState()
                        ),
                    horizontalArrangement =
                        Arrangement.End
                ) {
                    if (
                        appearance
                            .showCopyButton &&
                        model.message.text
                            .isNotBlank()
                    ) {
                        TextButton(
                            onClick = {
                                ShareUtils.copyText(
                                    context,
                                    model.message.text
                                )
                            }
                        ) {
                            Icon(
                                imageVector =
                                    Icons.Default.ContentCopy,
                                contentDescription =
                                    null
                            )

                            Text("复制")
                        }
                    }

                    if (
                        appearance
                            .showShareButton &&
                        model.message.text
                            .isNotBlank()
                    ) {
                        TextButton(
                            onClick = {
                                ShareUtils.shareText(
                                    context,
                                    model.message.text
                                )
                            }
                        ) {
                            Icon(
                                imageVector =
                                    Icons.Default.Share,
                                contentDescription =
                                    null
                            )

                            Text("分享")
                        }
                    }

                    if (
                        appearance
                            .showRegenerateButton &&
                        model.isAssistant &&
                        (
                            model.isFailed ||
                                model.isCancelled
                            )
                    ) {
                        TextButton(
                            onClick = onRetry
                        ) {
                            Icon(
                                imageVector =
                                    Icons.Default.Refresh,
                                contentDescription =
                                    null
                            )

                            Text("重试")
                        }
                    }

                    if (
                        appearance
                            .showDeleteButton
                    ) {
                        TextButton(
                            onClick = onDelete
                        ) {
                            Icon(
                                imageVector =
                                    Icons.Default.Delete,
                                contentDescription =
                                    null
                            )

                            Text("删除")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ImageViewerDialog(
    image: MessageImageEntity,
    onDismiss: () -> Unit,
    onContinueEditing: () -> Unit,
    onRegenerate: () -> Unit,
    onError: (String) -> Unit
) {
    val context = LocalContext.current
    val file = File(image.localPath)

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("图片预览")
        },
        text = {
            Column(
                verticalArrangement =
                    Arrangement.spacedBy(8.dp)
            ) {
                if (file.exists()) {
                    AsyncImage(
                        model = file,
                        contentDescription =
                            "图片原图",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(520.dp),
                        contentScale =
                            ContentScale.Fit
                    )
                } else {
                    Text(
                        text =
                            "图片文件已不存在",
                        color = MaterialTheme
                            .colorScheme
                            .error
                    )
                }

                image.prompt?.let {
                    SelectionContainer {
                        Text(
                            text = "提示词：$it"
                        )
                    }
                }

                Text(
                    text = buildString {
                        if (
                            !image.model
                                .isNullOrBlank()
                        ) {
                            append(
                                "模型：${image.model}"
                            )
                        }

                        if (
                            !image.sizeParameter
                                .isNullOrBlank()
                        ) {
                            append(
                                "\n尺寸：${image.sizeParameter}"
                            )
                        }

                        if (
                            !image.qualityParameter
                                .isNullOrBlank()
                        ) {
                            append(
                                "\n质量：${image.qualityParameter}"
                            )
                        }
                    },
                    style = MaterialTheme
                        .typography
                        .bodySmall
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onContinueEditing()
                    onDismiss()
                },
                enabled = file.exists()
            ) {
                Text("继续编辑")
            }
        },
        dismissButton = {
            Row {
                TextButton(
                    onClick = {
                        ShareUtils
                            .saveImageToGallery(
                                context,
                                file
                            )
                            .onFailure {
                                onError(
                                    it.message
                                        ?: "保存失败"
                                )
                            }
                    },
                    enabled = file.exists()
                ) {
                    Text("保存")
                }

                TextButton(
                    onClick = {
                        ShareUtils
                            .shareImage(
                                context,
                                file
                            )
                            .onFailure {
                                onError(
                                    it.message
                                        ?: "分享失败"
                                )
                            }
                    },
                    enabled = file.exists()
                ) {
                    Text("分享")
                }

                TextButton(
                    onClick = {
                        onRegenerate()
                        onDismiss()
                    }
                ) {
                    Text("重新生成")
                }

                TextButton(
                    onClick = onDismiss
                ) {
                    Text("关闭")
                }
            }
        }
    )
}

private fun formatDuration(
    durationMs: Long
): String {
    return if (durationMs < 1000) {
        "$durationMs ms"
    } else {
        val seconds =
            durationMs / 1000.0

        String.format(
            java.util.Locale.US,
            "%.1f 秒",
            seconds
        )
    }
}
