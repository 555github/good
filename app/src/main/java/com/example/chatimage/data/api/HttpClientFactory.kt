package com.example.chatimage.data.api

import com.example.chatimage.data.model.TimeoutSettings
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient

class HttpClientFactory {

    fun chatClient(
        timeouts: TimeoutSettings
    ): OkHttpClient {
        return baseBuilder()
            .connectTimeout(
                timeouts.chatConnectSeconds
                    .coerceAtLeast(1),
                TimeUnit.SECONDS
            )
            .readTimeout(
                timeouts.chatReadSeconds
                    .coerceAtLeast(1),
                TimeUnit.SECONDS
            )
            .writeTimeout(
                timeouts.chatWriteSeconds
                    .coerceAtLeast(1),
                TimeUnit.SECONDS
            )
            .apply {
                if (
                    timeouts.chatCallSeconds > 0
                ) {
                    callTimeout(
                        timeouts.chatCallSeconds,
                        TimeUnit.SECONDS
                    )
                } else {
                    callTimeout(
                        0,
                        TimeUnit.SECONDS
                    )
                }
            }
            .build()
    }

    fun toolDecisionClient(
        timeouts: TimeoutSettings
    ): OkHttpClient {
        return baseBuilder()
            .connectTimeout(
                timeouts.chatConnectSeconds
                    .coerceAtLeast(1),
                TimeUnit.SECONDS
            )
            .readTimeout(
                timeouts.toolDecisionSeconds
                    .coerceAtLeast(1),
                TimeUnit.SECONDS
            )
            .writeTimeout(
                timeouts.chatWriteSeconds
                    .coerceAtLeast(1),
                TimeUnit.SECONDS
            )
            .callTimeout(
                timeouts.toolDecisionSeconds
                    .coerceAtLeast(1),
                TimeUnit.SECONDS
            )
            .build()
    }

    fun imageClient(
        timeouts: TimeoutSettings
    ): OkHttpClient {
        return baseBuilder()
            .connectTimeout(
                timeouts.imageConnectSeconds
                    .coerceAtLeast(1),
                TimeUnit.SECONDS
            )
            .readTimeout(
                timeouts.imageReadSeconds
                    .coerceAtLeast(1),
                TimeUnit.SECONDS
            )
            .writeTimeout(
                timeouts.imageWriteSeconds
                    .coerceAtLeast(1),
                TimeUnit.SECONDS
            )
            .apply {
                if (
                    timeouts.imageCallSeconds > 0
                ) {
                    callTimeout(
                        timeouts.imageCallSeconds,
                        TimeUnit.SECONDS
                    )
                } else {
                    callTimeout(
                        0,
                        TimeUnit.SECONDS
                    )
                }
            }
            .build()
    }

    fun imageDownloadClient(
        timeouts: TimeoutSettings
    ): OkHttpClient {
        return baseBuilder()
            .connectTimeout(
                timeouts.imageConnectSeconds
                    .coerceAtLeast(1),
                TimeUnit.SECONDS
            )
            .readTimeout(
                timeouts.imageDownloadSeconds
                    .coerceAtLeast(1),
                TimeUnit.SECONDS
            )
            .callTimeout(
                timeouts.imageDownloadSeconds
                    .coerceAtLeast(1),
                TimeUnit.SECONDS
            )
            .build()
    }

    fun searchClient(
        timeouts: TimeoutSettings
    ): OkHttpClient {
        return baseBuilder()
            .connectTimeout(
                timeouts.searchConnectSeconds
                    .coerceAtLeast(1),
                TimeUnit.SECONDS
            )
            .readTimeout(
                timeouts.searchReadSeconds
                    .coerceAtLeast(1),
                TimeUnit.SECONDS
            )
            .callTimeout(
                timeouts.searchCallSeconds
                    .coerceAtLeast(1),
                TimeUnit.SECONDS
            )
            .build()
    }

    private fun baseBuilder():
        OkHttpClient.Builder {
        return OkHttpClient.Builder()
            .retryOnConnectionFailure(false)
            .followRedirects(true)
            .followSslRedirects(true)
    }
}
