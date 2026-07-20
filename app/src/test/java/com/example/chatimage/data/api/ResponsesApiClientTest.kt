package com.example.chatimage.data.api

import org.junit.Assert.assertEquals
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
}
