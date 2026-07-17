package com.example.chatimage

import android.content.Context
import com.example.chatimage.data.api.ChatApiClient
import com.example.chatimage.data.api.HttpClientFactory
import com.example.chatimage.data.api.ImageApiClient
import com.example.chatimage.data.api.SearchApiClient
import com.example.chatimage.data.api.ToolCallEngine
import com.example.chatimage.data.database.AppDatabase
import com.example.chatimage.data.repository.AiRequestRepository
import com.example.chatimage.data.repository.ApiProfileRepository
import com.example.chatimage.data.repository.ConversationRepository
import com.example.chatimage.data.repository.SearchProfileRepository
import com.example.chatimage.data.settings.AppSettingsStore
import com.example.chatimage.data.settings.SecureKeyStore
import com.example.chatimage.domain.ContextManager
import com.example.chatimage.domain.ImageParameterParser
import com.example.chatimage.domain.IntentRouter
import com.example.chatimage.domain.PromptOptimizer
import com.example.chatimage.util.ExportUtils

class AppContainer(
    context: Context
) {

    private val applicationContext =
        context.applicationContext

    val database: AppDatabase =
        AppDatabase.create(
            applicationContext
        )

    val secureKeyStore =
        SecureKeyStore(
            applicationContext
        )

    val settingsStore =
        AppSettingsStore(
            applicationContext
        )

    val apiProfileRepository =
        ApiProfileRepository(
            dao = database.apiProfileDao(),
            secureKeyStore =
                secureKeyStore
        )

    val searchProfileRepository =
        SearchProfileRepository(
            dao = database.searchProfileDao(),
            secureKeyStore =
                secureKeyStore
        )

    val conversationRepository =
        ConversationRepository(
            conversationDao =
                database.conversationDao(),
            messageDao =
                database.messageDao(),
            messageImageDao =
                database.messageImageDao()
        )

    private val httpClientFactory =
        HttpClientFactory()

    val chatApiClient =
        ChatApiClient(
            clientFactory =
                httpClientFactory
        )

    val imageApiClient =
        ImageApiClient(
            context = applicationContext,
            clientFactory =
                httpClientFactory
        )

    val searchApiClient =
        SearchApiClient(
            clientFactory =
                httpClientFactory
        )

    val toolCallEngine =
        ToolCallEngine(
            chatApiClient =
                chatApiClient,
            searchApiClient =
                searchApiClient
        )

    val intentRouter =
        IntentRouter()

    val contextManager =
        ContextManager()

    val imageParameterParser =
        ImageParameterParser()

    val promptOptimizer =
        PromptOptimizer(
            chatApiClient =
                chatApiClient
        )

    val aiRequestRepository =
        AiRequestRepository(
            chatApiClient =
                chatApiClient,
            imageApiClient =
                imageApiClient,
            toolCallEngine =
                toolCallEngine,
            intentRouter =
                intentRouter
        )

    val exportUtils =
        ExportUtils(
            context = applicationContext,
            conversationRepository =
                conversationRepository
        )
}
