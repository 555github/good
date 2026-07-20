package com.example.chatimage.ui.chat

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.chatimage.data.database.MessageImageEntity
import com.example.chatimage.data.model.AppearanceSettings
import com.example.chatimage.data.model.DiagnosticsSettings
import com.example.chatimage.ui.MessageUiModel
import com.example.chatimage.util.ShareUtils
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MessageCard(
    model: MessageUiModel,
    appearance: AppearanceSettings,
    diagnosticsSettings: DiagnosticsSettings,
    onDelete: () -> Unit,
    onRetry: () -> Unit,
    onImageClick: (MessageImageEntity) -> Unit,
    onContinueEditing: (MessageImageEntity) -> Unit,
    onRegenerateImage: (MessageImageEntity) -> Unit,
    onError: (String) -> Unit
) {
    val context = LocalContext.current
    val bubbleFraction = if (model.isUser) {
        appearance.messageWidthFraction.coerceIn(0.55f, 0.95f)
    } else {
        0.94f
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (model.isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top
    ) {
        if (!model.isUser) {
            MessageAvatar(isUser = false)
            Spacer(Modifier.width(7.dp))
        }

        Card(
            modifier = Modifier.fillMaxWidth(bubbleFraction),
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (model.isUser) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                }
            )
        ) {
            Column(
                modifier = Modifier.padding(
                    appearance.messagePaddingDp.coerceIn(4, 24).dp
                ),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                model.message.attachedImagePath?.let { path ->
                    File(path).takeIf(File::exists)?.let { file ->
                        AsyncImage(
                            model = file,
                            contentDescription = "上传图片",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp),
                            contentScale = ContentScale.Fit
                        )
                    }
                }

                if (model.hasText) {
                    MessageTextContent(model.message.text)
                } else if (model.isRunning) {
                    Text("正在生成...")
                }

                model.images.forEach { image ->
                    val file = File(image.localPath)
                    if (file.exists()) {
                        AsyncImage(
                            model = file,
                            contentDescription = "生成图片",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(
                                    appearance.imagePreviewHeightDp
                                        .coerceIn(160, 800)
                                        .dp
                                )
                                .clickable { onImageClick(image) },
                            contentScale = ContentScale.Fit
                        )

                        CompactImageActions(
                            onView = { onImageClick(image) },
                            onEdit = { onContinueEditing(image) },
                            onSave = {
                                ShareUtils.saveImageToGallery(context, file)
                                    .onSuccess {
                                        Toast.makeText(
                                            context,
                                            "已保存到相册",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                    .onFailure {
                                        onError(it.message ?: "保存失败")
                                    }
                            },
                            onShare = {
                                ShareUtils.shareImage(context, file)
                                    .onFailure {
                                        onError(it.message ?: "分享失败")
                                    }
                            },
                            onRegenerate = { onRegenerateImage(image) }
                        )
                    } else {
                        Text(
                            "本地图片文件已不存在",
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }

                if (model.citations.isNotEmpty()) {
                    HorizontalDivider()
                    Text("来源", style = MaterialTheme.typography.titleSmall)
                    model.citations.forEach { citation ->
                        Column(
                            modifier = Modifier.clickable(enabled = citation.url.isNotBlank()) {
                                runCatching {
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW, Uri.parse(citation.url))
                                    )
                                }.onFailure { onError("无法打开来源链接") }
                            }
                        ) {
                            Text(
                                "[${citation.index}] ${citation.title}",
                                color = MaterialTheme.colorScheme.primary
                            )
                            if (citation.snippet.isNotBlank()) {
                                Text(
                                    citation.snippet,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 3
                                )
                            }
                        }
                    }
                }

                model.message.errorMessage?.let { error ->
                    SelectionContainer {
                        Text(error, color = MaterialTheme.colorScheme.error)
                    }
                }

                MessageMetadata(model, diagnosticsSettings)
                CompactMessageActions(
                    model = model,
                    appearance = appearance,
                    onCopy = {
                        ShareUtils.copyText(context, model.message.text)
                        Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
                    },
                    onShare = {
                        ShareUtils.shareText(context, model.message.text)
                    },
                    onRetry = onRetry,
                    onDelete = onDelete
                )
            }
        }

        if (model.isUser) {
            Spacer(Modifier.width(7.dp))
            MessageAvatar(isUser = true)
        }
    }
}

@Composable
private fun MessageAvatar(isUser: Boolean) {
    Surface(
        modifier = Modifier.size(30.dp),
        shape = CircleShape,
        color = if (isUser) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.secondaryContainer
        }
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = if (isUser) Icons.Default.Person else Icons.Default.SmartToy,
                contentDescription = if (isUser) "用户" else "AI 助手",
                modifier = Modifier.size(18.dp),
                tint = if (isUser) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSecondaryContainer
                }
            )
        }
    }
}

@Composable
private fun MessageTextContent(text: String) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        parseMessageTextBlocks(text).forEach { block ->
            when (block) {
                is MessageTextBlock.Paragraph -> {
                    if (block.text.isNotEmpty()) {
                        SelectionContainer { Text(block.text) }
                    }
                }

                is MessageTextBlock.Code -> CodeBlock(block)
            }
        }
    }
}

@Composable
private fun CodeBlock(block: MessageTextBlock.Code) {
    val context = LocalContext.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(start = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    block.language.ifBlank { "代码" },
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall
                )
                SmallActionButton(
                    icon = Icons.Default.ContentCopy,
                    description = "复制代码"
                ) {
                    ShareUtils.copyText(context, block.code)
                    Toast.makeText(context, "代码已复制", Toast.LENGTH_SHORT).show()
                }
            }
            SelectionContainer {
                Text(
                    text = block.code,
                    modifier = Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(10.dp),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp
                )
            }
        }
    }
}

@Composable
private fun MessageMetadata(
    model: MessageUiModel,
    diagnostics: DiagnosticsSettings
) {
    val text = remember(
        model.message.model,
        model.message.createdAt,
        model.message.durationMs,
        model.message.httpStatus,
        model.message.totalTokens,
        model.message.cachedInputTokens
    ) {
        buildList {
            model.message.model?.takeIf(String::isNotBlank)?.let(::add)
            add(formatTimestamp(model.message.createdAt))
            if (diagnostics.showDuration) {
                model.message.durationMs?.let { add(formatDuration(it)) }
            }
            if (diagnostics.showHttpStatus) {
                model.message.httpStatus?.let { add("HTTP $it") }
            }
            model.message.totalTokens?.let { total ->
                add(
                    buildString {
                        append("$total tokens")
                        model.message.cachedInputTokens?.takeIf { it > 0 }?.let {
                            append("（缓存 $it）")
                        }
                    }
                )
            }
        }.joinToString("  ·  ")
    }

    if (text.isNotBlank()) {
        Text(
            text = text,
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}

@Composable
private fun CompactMessageActions(
    model: MessageUiModel,
    appearance: AppearanceSettings,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onRetry: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.End
    ) {
        if (appearance.showCopyButton && model.hasText) {
            SmallActionButton(Icons.Default.ContentCopy, "复制消息", onCopy)
        }
        if (appearance.showShareButton && model.hasText) {
            SmallActionButton(Icons.Default.Share, "分享消息", onShare)
        }
        if (
            appearance.showRegenerateButton &&
            model.isAssistant &&
            (model.isFailed || model.isCancelled)
        ) {
            SmallActionButton(Icons.Default.Refresh, "重试", onRetry)
        }
        if (appearance.showDeleteButton) {
            SmallActionButton(Icons.Default.Delete, "删除消息", onDelete)
        }
    }
}

@Composable
private fun CompactImageActions(
    onView: () -> Unit,
    onEdit: () -> Unit,
    onSave: () -> Unit,
    onShare: () -> Unit,
    onRegenerate: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.End
    ) {
        SmallActionButton(Icons.Default.SmartToy, "全屏查看", onView)
        SmallActionButton(Icons.Default.Edit, "继续编辑", onEdit)
        SmallActionButton(Icons.Default.Save, "保存图片", onSave)
        SmallActionButton(Icons.Default.Share, "分享图片", onShare)
        SmallActionButton(Icons.Default.Refresh, "重新生成", onRegenerate)
    }
}

@Composable
private fun SmallActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(32.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            modifier = Modifier.size(17.dp)
        )
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
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        scale = (scale * zoomChange).coerceIn(1f, 6f)
        offset = if (scale > 1f) offset + panChange else Offset.Zero
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            if (file.exists()) {
                AsyncImage(
                    model = file,
                    contentDescription = "图片原图",
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onDoubleTap = {
                                    if (scale > 1f) {
                                        scale = 1f
                                        offset = Offset.Zero
                                    } else {
                                        scale = 2.5f
                                    }
                                }
                            )
                        }
                        .transformable(transformState)
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = offset.x,
                            translationY = offset.y
                        ),
                    contentScale = ContentScale.Fit
                )
            } else {
                Text(
                    "图片文件已不存在",
                    modifier = Modifier.align(Alignment.Center),
                    color = Color.White
                )
            }

            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
                    .background(Color.Black.copy(alpha = 0.55f), CircleShape)
            ) {
                Icon(Icons.Default.Close, "关闭全屏图片", tint = Color.White)
            }

            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(),
                color = Color.Black.copy(alpha = 0.72f)
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    val details = listOfNotNull(
                        image.model?.takeIf(String::isNotBlank),
                        image.sizeParameter?.takeIf(String::isNotBlank),
                        image.qualityParameter?.takeIf(String::isNotBlank)
                    ).joinToString("  ·  ")
                    if (details.isNotBlank()) {
                        Text(
                            details,
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        ViewerAction(Icons.Default.Edit, "继续编辑") {
                            onContinueEditing()
                            onDismiss()
                        }
                        ViewerAction(Icons.Default.Save, "保存") {
                            ShareUtils.saveImageToGallery(context, file)
                                .onSuccess {
                                    Toast.makeText(
                                        context,
                                        "已保存到相册",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                                .onFailure { onError(it.message ?: "保存失败") }
                        }
                        ViewerAction(Icons.Default.Share, "分享") {
                            ShareUtils.shareImage(context, file)
                                .onFailure { onError(it.message ?: "分享失败") }
                        }
                        ViewerAction(Icons.Default.Refresh, "重新生成") {
                            onRegenerate()
                            onDismiss()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ViewerAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(onClick = onClick, modifier = Modifier.size(40.dp)) {
            Icon(icon, label, tint = Color.White)
        }
        Text(label, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}

private fun formatDuration(durationMs: Long): String {
    return if (durationMs < 1000) {
        "${durationMs}ms"
    } else {
        String.format(Locale.getDefault(), "%.1fs", durationMs / 1000.0)
    }
}

private fun formatTimestamp(timestamp: Long): String {
    return SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(timestamp))
}
