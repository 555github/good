package com.example.chatimage.data.api

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatWireMessageTest {

    @Test
    fun multimodalContentUsesOpenAiCompatibleShape() {
        val result = ChatWireMessage(
            role = "user",
            content = "分析图片",
            contentParts = listOf(
                ChatContentPart.Text("分析图片"),
                ChatContentPart.ImageUrl(
                    "data:image/png;base64,AA=="
                )
            )
        ).toJson()

        val content = result.get("content")
        assertTrue(content is JSONArray)

        val parts = content as JSONArray
        assertEquals("text", parts.getJSONObject(0).getString("type"))
        assertEquals(
            "分析图片",
            parts.getJSONObject(0).getString("text")
        )
        assertEquals(
            "data:image/png;base64,AA==",
            parts.getJSONObject(1)
                .getJSONObject("image_url")
                .getString("url")
        )
    }
}
