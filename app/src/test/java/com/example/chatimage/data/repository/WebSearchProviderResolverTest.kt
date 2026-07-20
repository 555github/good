package com.example.chatimage.data.repository

import com.example.chatimage.data.model.WebSearchProvider
import org.junit.Assert.assertEquals
import org.junit.Test

class WebSearchProviderResolverTest {
    @Test
    fun automaticProviderUsesBuiltInSearchForResponses() {
        assertEquals(
            WebSearchProvider.MODEL_BUILT_IN,
            resolveWebSearchProvider(
                configuredProvider = WebSearchProvider.AUTO,
                usesResponses = true
            )
        )
    }

    @Test
    fun automaticProviderUsesThirdPartyForChatCompletions() {
        assertEquals(
            WebSearchProvider.THIRD_PARTY,
            resolveWebSearchProvider(
                configuredProvider = WebSearchProvider.AUTO,
                usesResponses = false
            )
        )
    }

    @Test
    fun explicitProviderIsNotOverridden() {
        assertEquals(
            WebSearchProvider.THIRD_PARTY,
            resolveWebSearchProvider(
                configuredProvider = WebSearchProvider.THIRD_PARTY,
                usesResponses = true
            )
        )
    }
}
