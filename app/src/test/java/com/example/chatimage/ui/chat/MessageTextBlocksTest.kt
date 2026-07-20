package com.example.chatimage.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageTextBlocksTest {

    @Test
    fun parsesParagraphAndFencedCode() {
        val result = parseMessageTextBlocks(
            "说明\n```kotlin\nval answer = 42\n```\n完成"
        )

        assertEquals(3, result.size)
        assertEquals(
            MessageTextBlock.Code("kotlin", "val answer = 42"),
            result[1]
        )
    }

    @Test
    fun keepsUnclosedFenceAsParagraph() {
        val result = parseMessageTextBlocks("```text\nnot finished")

        assertEquals(1, result.size)
        assertTrue(result.single() is MessageTextBlock.Paragraph)
    }
}
