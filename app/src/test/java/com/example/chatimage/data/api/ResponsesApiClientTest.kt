package com.example.chatimage.data.api

import com.example.chatimage.data.model.AppSettings
import com.example.chatimage.data.model.SearchSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ResponsesApiClientTest {
    @Test
    fun parsesTextCitationsAndUsage() {
        val body = """
            {
              "status":"completed",
              "output":[{"type":"message","content":[{
                "type":"output_text","text":"answer",
                "annotations":[{"type":"url_citation","title":"Source","url":"https://example.com"}]
              }]}],
              "usage":{"input_tokens":10,"output_tokens":4,"total_tokens":14}
            }
        """.trimIndent()

        val result = ResponsesApiClient.parseNonStreamBody(body)

        assertEquals("answer", result.content)
        assertEquals(1, result.citations.size)
        assertEquals(14L, result.usage.totalTokens)
    }

    @Test
    fun builtInSearchAddsResponsesWebSearchTool() {
        val request = ResponsesApiClient().buildRequestJson(
            model = "gpt-test",
            settings = AppSettings(
                search = SearchSettings(
                    builtInToolType = "web_search"
                )
            ),
            messages = listOf(
                ChatWireMessage(
                    role = "user",
                    content = "查一下今天的新闻"
                )
            ),
            builtInWebSearch = true
        )

        assertEquals(
            "web_search",
            request.getJSONArray("tools")
                .getJSONObject(0)
                .getString("type")
        )
        assertEquals(
            "web_search_call.action.sources",
            request.getJSONArray("include").getString(0)
        )
    }

    @Test
    fun normalResponsesRequestDoesNotAddSearchTool() {
        val request = ResponsesApiClient().buildRequestJson(
            model = "gpt-test",
            settings = AppSettings(),
            messages = emptyList(),
            builtInWebSearch = false
        )

        assertFalse(request.has("tools"))
        assertFalse(request.has("include"))
    }

    @Test
    fun forcedBuiltInSearchRequiresToolCall() {
        val request = ResponsesApiClient().buildRequestJson(
            model = "gpt-test",
            settings = AppSettings(),
            messages = emptyList(),
            builtInWebSearch = true,
            requireBuiltInWebSearch = true
        )

        assertEquals("required", request.getString("tool_choice"))
    }
}
