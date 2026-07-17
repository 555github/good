package com.example.chatimage.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.chatimage.data.database.ConversationEntity
import com.example.chatimage.ui.AppUiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryDialog(
    state: AppUiState,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
    onRename: (String, String) -> Unit,
    onTogglePinned: (String) -> Unit,
    onDelete: (String) -> Unit,
    onSearchQueryChange: (String) -> Unit
) {
    var conversationToRename by remember {
        mutableStateOf<
            ConversationEntity?
            >(null)
    }

    var conversationToDelete by remember {
        mutableStateOf<
            ConversationEntity?
            >(null)
    }

    val filteredConversations =
        state.conversations.filter {
            conversation ->
            state.historySearchQuery
                .isBlank() ||
                conversation.title.contains(
                    state.historySearchQuery,
                    ignoreCase = true
                )
        }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("历史对话")
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(560.dp)
            ) {
                OutlinedTextField(
                    value =
                        state.historySearchQuery,
                    onValueChange =
                        onSearchQueryChange,
                    modifier =
                        Modifier.fillMaxWidth(),
                    label = {
                        Text("搜索对话")
                    },
                    singleLine = true
                )

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement =
                        Arrangement.spacedBy(
                            2.dp
                        )
                ) {
                    items(
                        items =
                            filteredConversations,
                        key = {
                            it.id
                        }
                    ) { conversation ->
                        ConversationHistoryItem(
                            conversation =
                                conversation,
                            selected =
                                conversation.id ==
                                    state
                                        .currentConversationId,
                            onSelect = {
                                onSelect(
                                    conversation.id
                                )
                            },
                            onRename = {
                                conversationToRename =
                                    conversation
                            },
                            onTogglePinned = {
                                onTogglePinned(
                                    conversation.id
                                )
                            },
                            onDelete = {
                                conversationToDelete =
                                    conversation
                            }
                        )
                    }
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

    conversationToRename?.let {
        conversation ->
        RenameConversationDialog(
            initialTitle =
                conversation.title,
            onConfirm = { newTitle ->
                onRename(
                    conversation.id,
                    newTitle
                )

                conversationToRename =
                    null
            },
            onDismiss = {
                conversationToRename =
                    null
            }
        )
    }

    conversationToDelete?.let {
        conversation ->
        AlertDialog(
            onDismissRequest = {
                conversationToDelete =
                    null
            },
            title = {
                Text("删除对话")
            },
            text = {
                Text(
                    "确定删除“${conversation.title}”吗？此操作不会自动删除已经保存到相册的图片。"
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDelete(
                            conversation.id
                        )

                        conversationToDelete =
                            null
                    }
                ) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        conversationToDelete =
                            null
                    }
                ) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
private fun ConversationHistoryItem(
    conversation: ConversationEntity,
    selected: Boolean,
    onSelect: () -> Unit,
    onRename: () -> Unit,
    onTogglePinned: () -> Unit,
    onDelete: () -> Unit
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalAlignment =
                Alignment.CenterVertically
        ) {
            TextButton(
                onClick = onSelect,
                modifier = Modifier.weight(1f)
            ) {
                Column(
                    modifier =
                        Modifier.fillMaxWidth()
                ) {
                    Text(
                        text =
                            if (
                                conversation.pinned
                            ) {
                                "📌 ${conversation.title}"
                            } else {
                                conversation.title
                            },
                        color =
                            if (selected) {
                                MaterialTheme
                                    .colorScheme
                                    .primary
                            } else {
                                MaterialTheme
                                    .colorScheme
                                    .onSurface
                            },
                        maxLines = 2
                    )

                    Text(
                        text = formatTime(
                            conversation.updatedAt
                        ),
                        style = MaterialTheme
                            .typography
                            .bodySmall
                    )
                }
            }

            IconButton(
                onClick = onTogglePinned
            ) {
                Icon(
                    imageVector =
                        Icons.Default.PushPin,
                    contentDescription =
                        if (
                            conversation.pinned
                        ) {
                            "取消置顶"
                        } else {
                            "置顶"
                        }
                )
            }

            IconButton(
                onClick = onRename
            ) {
                Icon(
                    imageVector =
                        Icons.Default.Edit,
                    contentDescription =
                        "重命名"
                )
            }

            IconButton(
                onClick = onDelete
            ) {
                Icon(
                    imageVector =
                        Icons.Default.Delete,
                    contentDescription =
                        "删除"
                )
            }
        }

        HorizontalDivider()
    }
}

@Composable
private fun RenameConversationDialog(
    initialTitle: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var title by remember(
        initialTitle
    ) {
        mutableStateOf(initialTitle)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("重命名对话")
        },
        text = {
            OutlinedTextField(
                value = title,
                onValueChange = {
                    title = it
                },
                modifier =
                    Modifier.fillMaxWidth(),
                label = {
                    Text("对话名称")
                },
                singleLine = true
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(
                        title.trim()
                    )
                },
                enabled =
                    title.trim().isNotBlank()
            ) {
                Text("保存")
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

private fun formatTime(
    timestamp: Long
): String {
    return SimpleDateFormat(
        "yyyy-MM-dd HH:mm",
        Locale.getDefault()
    ).format(
        Date(timestamp)
    )
}
