package com.example.chatimage.data.api

import com.example.chatimage.data.model.AppSettings
import com.example.chatimage.data.model.RequestDiagnostics
import com.example.chatimage.data.model.SearchResult
import com.example.chatimage.data.repository.ResolvedSearchProfile
import com.example.chatimage.util.ErrorParser
import com.example.chatimage.util.HeaderUtils
import com.example.chatimage.util.JsonUtils
import com.example.chatimage.util.RequestCategory
import com.example.chatimage.util.UrlUtils
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class SearchApiClient(
    private val clientFactory: HttpClientFactory =
        HttpClientFactory()
) {

    suspend fun search(
        resolvedProfile:
            ResolvedSearchProfile,
        appSettings: AppSettings,
        query: String,
        countOverride: Int? = null
    ): ApiOutcome<SearchResponse> =
        withContext(Dispatchers.IO) {
            val profile =
                resolvedProfile.profile

            val searchSettings =
                appSettings.search

            val cleanQuery = query
                .trim()
                .take(
                    searchSettings
                        .maximumQueryCharacters
                        .coerceAtLeast(1)
                )

            val count = (
                countOverride
                    ?: searchSettings.resultCount
                )
                .coerceIn(1, 10)

            val endpoint =
                UrlUtils.resolveEndpoint(
                    profile.baseUrl,
                    profile.path
                )

            val startedAt =
                System.currentTimeMillis()

            var call: Call? = null

            try {
                val requestBuilder =
                    if (
                        profile.requestMethod
                            .equals(
                                "GET",
                                ignoreCase = true
                            )
                    ) {
                        val urlBuilder =
                            endpoint
                                .toHttpUrl()
                                .newBuilder()
                                .addQueryParameter(
                                    profile.queryField,
                                    cleanQuery
                                )

                        if (
                            profile.countField
                                .isNotBlank()
                        ) {
                            urlBuilder
                                .addQueryParameter(
                                    profile.countField,
                                    count.toString()
                                )
                        }

                        if (
                            profile.languageField
                                .isNotBlank() &&
                            searchSettings
                                .language
                                .isNotBlank()
                        ) {
                            urlBuilder
                                .addQueryParameter(
                                    profile
                                        .languageField,
                                    searchSettings
                                        .language
                                )
                        }

                        if (
                            profile.regionField
                                .isNotBlank() &&
                            searchSettings
                                .region
                                .isNotBlank()
                        ) {
                            urlBuilder
                                .addQueryParameter(
                                    profile.regionField,
                                    searchSettings
                                        .region
                                )
                        }

                        Request.Builder()
                            .url(urlBuilder.build())
                            .get()
                    } else {
                        val standard =
                            JSONObject()
                                .put(
                                    profile.queryField,
                                    cleanQuery
                                )

                        if (
                            profile.countField
                                .isNotBlank()
                        ) {
                            standard.put(
                                profile.countField,
                                count
                            )
                        }

                        if (
                            profile.languageField
                                .isNotBlank() &&
                            searchSettings
                                .language
                                .isNotBlank()
                        ) {
                            standard.put(
                                profile.languageField,
                                searchSettings
                                    .language
                            )
                        }

                        if (
                            profile.regionField
                                .isNotBlank() &&
                            searchSettings
                                .region
                                .isNotBlank()
                        ) {
                            standard.put(
                                profile.regionField,
                                searchSettings
                                    .region
                            )
                        }

                        val extra =
                            JsonUtils
                                .parseObjectOrEmpty(
                                    profile
                                        .extraRequestJson
                                )

                        val body =
                            JsonUtils.deepMerge(
                                standard,
                                extra,
                                overlayWins = true
                            )
                                .toString()
                                .toRequestBody(
                                    "application/json; charset=utf-8"
                                        .toMediaType()
                                )

                        Request.Builder()
                            .url(endpoint)
                            .post(body)
                    }

                HeaderUtils.applyAuthentication(
                    builder = requestBuilder,
                    mode = profile
                        .authenticationMode,
                    apiKey =
                        resolvedProfile.apiKey,
                    headerName = profile
                        .authorizationHeaderName,
                    prefix = profile
                        .authorizationPrefix
                )

                HeaderUtils.applyCustomHeaders(
                    builder = requestBuilder,
                    entries = HeaderUtils.decode(
                        profile.customHeadersJson
                    ),
                    category =
                        RequestCategory.SEARCH
                )

                requestBuilder.header(
                    "Accept",
                    "application/json"
                )

                val client =
                    clientFactory.searchClient(
                        appSettings.timeouts
                    )

                call = client.newCall(
                    requestBuilder.build()
                )

                val cancellationHandle =
                    coroutineContext.job
                        .invokeOnCompletion {
                            cause ->
                            if (
                                cause is
                                    CancellationException
                            ) {
                                call?.cancel()
                            }
                        }

                try {
                    call.execute().use {
                        response ->
                        val body = response.body
                            ?.string()
                            .orEmpty()

                        val duration =
                            System.currentTimeMillis() -
                                startedAt

                        if (!response.isSuccessful) {
                            val parsed =
                                ErrorParser
                                    .fromHttpResponse(
                                        response = response,
                                        body = body,
                                        endpoint = endpoint,
                                        model = null,
                                        durationMs =
                                            duration,
                                        requestIdHeaderNames =
                                            appSettings
                                                .diagnostics
                                                .requestIdHeaderNames
                                    )

                            return@withContext
                                ApiOutcome.Failure(
                                    parsed.first,
                                    parsed.second
                                )
                        }

                        val results =
                            parseResults(
                                body = body,
                                profile = profile,
                                maximumResults =
                                    count
                            )

                        val diagnostics =
                            RequestDiagnostics(
                                endpoint = endpoint,
                                method =
                                    response
                                        .request
                                        .method,
                                httpStatus =
                                    response.code,
                                durationMs =
                                    duration,
                                contentType =
                                    response.header(
                                        "Content-Type"
                                    ),
                                server =
                                    response.header(
                                        "Server"
                                    ),
                                requestId =
                                    appSettings
                                        .diagnostics
                                        .requestIdHeaderNames
                                        .firstNotNullOfOrNull {
                                            response.header(
                                                it
                                            )
                                        }
                            )

                        if (results.isEmpty()) {
                            return@withContext
                                ApiOutcome.Failure(
                                    error =
                                        com.example
                                            .chatimage
                                            .data
                                            .model
                                            .RequestError(
                                                code =
                                                    "SEARCH_PARSE_ERROR",
                                                message =
                                                    "搜索接口返回成功，但没有按当前字段路径解析到结果。请检查结果数组、标题、URL 和摘要字段。",
                                                httpStatus =
                                                    response.code,
                                                requestId =
                                                    diagnostics
                                                        .requestId,
                                                durationMs =
                                                    duration,
                                                rawBodyPreview =
                                                    body.take(
                                                        2000
                                                    )
                                            ),
                                    diagnostics =
                                        diagnostics
                                )
                        }

                        return@withContext
                            ApiOutcome.Success(
                                value =
                                    SearchResponse(
                                        query =
                                            cleanQuery,
                                        results =
                                            results
                                    ),
                                diagnostics =
                                    diagnostics
                            )
                    }
                } finally {
                    cancellationHandle.dispose()
                }
            } catch (
                exception: CancellationException
            ) {
                call?.cancel()
                throw exception
            } catch (exception: Exception) {
                val parsed =
                    ErrorParser.fromException(
                        exception = exception,
                        endpoint = endpoint,
                        model = null,
                        durationMs =
                            System.currentTimeMillis() -
                                startedAt
                    )

                ApiOutcome.Failure(
                    parsed.first,
                    parsed.second
                )
            }
        }

    private fun parseResults(
        body: String,
        profile:
            com.example.chatimage
                .data.database
                .SearchProfileEntity,
        maximumResults: Int
    ): List<SearchResult> {
        val root = JSONObject(body)

        val array = JsonUtils.readArray(
            root,
            profile.resultArrayPath
        ) ?: return emptyList()

        return buildList {
            for (
                index in 0 until
                    minOf(
                        array.length(),
                        maximumResults
                    )
            ) {
                val item =
                    array.optJSONObject(index)
                        ?: continue

                val title =
                    JsonUtils.readString(
                        item,
                        profile.resultTitlePath
                    ).orEmpty()

                val url =
                    JsonUtils.readString(
                        item,
                        profile.resultUrlPath
                    ).orEmpty()

                val snippet =
                    JsonUtils.readString(
                        item,
                        profile
                            .resultSnippetPath
                    ).orEmpty()

                if (
                    title.isBlank() &&
                    url.isBlank() &&
                    snippet.isBlank()
                ) {
                    continue
                }

                add(
                    SearchResult(
                        title =
                            title.ifBlank {
                                url.ifBlank {
                                    "搜索结果"
                                }
                            },
                        url = url,
                        snippet = snippet,
                        publishedAt =
                            JsonUtils.readString(
                                item,
                                profile
                                    .resultDatePath
                            ),
                        source =
                            JsonUtils.readString(
                                item,
                                profile
                                    .resultSourcePath
                            )
                    )
                )
            }
        }
    }
}
