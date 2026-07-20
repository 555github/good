package com.example.chatimage.data.api

import org.junit.Assert.assertEquals
import org.junit.Test

class ModelsApiClientTest {
    @Test
    fun parsesOpenAiModelsResponse() {
        val models = ModelsApiClient.parseModelIds(
            """{"data":[{"id":"model-b"},{"id":"model-a"}]}"""
        )

        assertEquals(listOf("model-a", "model-b"), models)
    }
}
