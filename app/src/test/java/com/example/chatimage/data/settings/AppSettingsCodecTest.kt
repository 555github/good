package com.example.chatimage.data.settings

import com.example.chatimage.data.model.AppSettings
import com.example.chatimage.data.model.SearchSettings
import com.example.chatimage.data.model.WebSearchProvider
import org.junit.Assert.assertEquals
import org.junit.Test

class AppSettingsCodecTest {
    @Test
    fun oldThirdPartyDefaultMigratesToAutomaticSelection() {
        val decoded = AppSettingsCodec.decode(
            """{"search":{"mode":"AUTO","provider":"THIRD_PARTY"}}"""
        )

        assertEquals(WebSearchProvider.AUTO, decoded.search.provider)
    }

    @Test
    fun explicitThirdPartySelectionSurvivesCurrentCodec() {
        val settings = AppSettings(
            search = SearchSettings(
                provider = WebSearchProvider.THIRD_PARTY
            )
        )

        val decoded = AppSettingsCodec.decode(
            AppSettingsCodec.encode(settings)
        )

        assertEquals(
            WebSearchProvider.THIRD_PARTY,
            decoded.search.provider
        )
    }
}
