package com.example.chatimage.ui.chat

sealed interface MessageTextBlock {
    data class Paragraph(val text: String) : MessageTextBlock
    data class Code(val language: String, val code: String) : MessageTextBlock
}

fun parseMessageTextBlocks(text: String): List<MessageTextBlock> {
    if (!text.contains("```")) {
        return listOf(MessageTextBlock.Paragraph(text))
    }

    val blocks = mutableListOf<MessageTextBlock>()
    var cursor = 0

    while (cursor < text.length) {
        val opening = text.indexOf("```", cursor)
        if (opening < 0) {
            text.substring(cursor)
                .takeIf(String::isNotEmpty)
                ?.let { blocks += MessageTextBlock.Paragraph(it) }
            break
        }

        if (opening > cursor) {
            blocks += MessageTextBlock.Paragraph(
                text.substring(cursor, opening)
            )
        }

        val headerEnd = text.indexOf('\n', opening + 3)
        if (headerEnd < 0) {
            blocks += MessageTextBlock.Paragraph(text.substring(opening))
            break
        }

        val closing = text.indexOf("```", headerEnd + 1)
        if (closing < 0) {
            blocks += MessageTextBlock.Paragraph(text.substring(opening))
            break
        }

        blocks += MessageTextBlock.Code(
            language = text.substring(opening + 3, headerEnd).trim(),
            code = text.substring(headerEnd + 1, closing).trimEnd('\r', '\n')
        )
        cursor = closing + 3
    }

    return blocks.ifEmpty {
        listOf(MessageTextBlock.Paragraph(text))
    }
}
