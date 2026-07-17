package com.example.chatimage.data.api

import com.example.chatimage.data.database.SearchProfileEntity
import com.example.chatimage.data.model.AppSettings
import com.example.chatimage.data.model.RequestDiagnostics
import com.example.chatimage.data.model.RequestError
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
import okhttp3.Response
import org.json.JSONObject

class SearchApiClient(
    private val clientFactory: HttpClientFactory =
        HttpClientFactory()
) {

    suspend fun search(
        resolvedProfile: ResolvedSearchProfile,
        appSettings: AppSettings,
        query: String,
        countOverride: Int? = null
    ): ApiOutcome<SearchResponse> {
        return withContext(Dispatchers.IO) {
            executeRequest(
                resolvedProfile = resolvedProfile,
                appSettings = appSettings,
                query = query,
                countOverride = countOverride
            )
        }
    }

    private fun executeRequest(
        resolvedProfile: ResolvedSearchProfile,
        appSettings: AppSettings,
        query: String,
        countOverride: Int?
    ): ApiOutcome<SearchResponse> {
        val profile = resolvedProfile.profile
        val searchSettings = appSettings.search

        val cleanQuery = query
            .trim()
            .take(
                searchSettings
                    .maximumQueryCharacters
                    .coerceAtLeast(1)
            )

        if (cleanQuery.isBlank()) {
            return ApiOutcome.Failure(
                error = RequestError(
                    code = "EMPTY_SEARCH_QUERY",
                    message = "搜索关键词为空",
                    retryable = false
                )
            )
        }

        val resultCount = (
            countOverride
                ?: searchSettings.resultCount
            ).coerceIn(1, 10)

        val endpoint = UrlUtils.resolveEndpoint(
            profile.baseUrl,
            profile.path
        )

        val startedAt = System.currentTimeMillis()

        var activeCall: Call? = null

        try {
            val requestBuilder = buildRequest(
                endpoint = endpoint,
                profile = profile,
                query = cleanQuery,
                resultCount = resultCount,
                language = searchSettings.language,
                region = searchSettings.region
            )

            HeaderUtils.applyAuthentication(
                builder = requestBuilder,
                mode = profile.authenticationMode,
                apiKey = resolvedProfile.apiKey,
                headerName =
                    profile.authorizationHeaderName,
                prefix = profile.authorizationPrefix
            )

            HeaderUtils.applyCustomHeaders(
                builder = requestBuilder,
                entries = HeaderUtils.decode(
                    profile.customHeadersJson
                ),
                category = RequestCategory.SEARCH
            )

            requestBuilder.header(
                "Accept",
                "application/json"
            )

            val call = clientFactory
                .searchClient(appSettings.timeouts)
                .newCall(requestBuilder.build())

            activeCall = call

            val cancellationHandle =
                coroutineContext.job
                    .invokeOnCompletion { cause ->
                        if (
                            cause is CancellationException
                        ) {
                            call.cancel()
                        }
                    }

            try {
                val response = call.execute()

                return response.use { value ->
                    processResponse(
                        response = value,
                        endpoint = endpoint,
                        profile = profile,
                        cleanQuery = cleanQuery,
                        maximumResults = resultCount,
                        startedAt = startedAt,
                        appSettings = appSettings
                    )
                }
            } finally {
                cancellationHandle.dispose()
            }
        } catch (exception: CancellationException) {
            activeCall?.cancel()
            throw exception
        } catch (exception: Exception) {
            val parsed = ErrorParser.fromException(
                exception = exception,
                endpoint = endpoint,
                model = null,
                durationMs =
                    System.currentTimeMillis() -
                        startedAt
            )

            return ApiOutcome.Failure(
                error = parsed.first,
                diagnostics = parsed.second
            )
        }
    }

    private fun buildRequest(
        endpoint: String,
        profile: SearchProfileEntity,
        query: String,
        resultCount: Int,
        language: String,
        region: String
    ): Request.Builder {
        if (
            profile.requestMethod.equals(
                "GET",
                ignoreCase = true
            )
        ) {
            val urlBuilder = endpoint
                .toHttpUrl()
                .newBuilder()
                .addQueryParameter(
                    profile.queryField,
                    query
                )

            if (profile.countField.isNotBlank()) {
                urlBuilder.addQueryParameter(
                    profile.countField,
                    resultCount.toString()
                )
            }

            if (
                profile.languageField.isNotBlank() &&
                language.isNotBlank()
            ) {
                urlBuilder.addQueryParameter(
                    profile.languageField,
                    language
                )
            }

            if (
                profile.regionField.isNotBlank() &&
                region.isNotBlank()
            ) {
                urlBuilder.addQueryParameter(
                    profile.regionField,
                    region
                )
            }

            return Request.Builder()
                .url(urlBuilder.build())
                .get()
        }

        val standardJson = JSONObject()
            .put(
                profile.queryField,
                query
            )

        if (profile.countField.isNotBlank()) {
            standardJson.put(
                profile.countField,
                resultCount
            )
        }

        if (
            profile.languageField.isNotBlank() &&
            language.isNotBlank()
        ) {
            standardJson.put(
                profile.languageField,
                language
            )
        }

        if (
            profile.regionField.isNotBlank() &&
            region.isNotBlank()
        ) {
            standardJson.put(
                profile.regionField,
                region
            )
        }

        val finalJson = JsonUtils.deepMerge(
            base = standardJson,
            overlay = JsonUtils.parseObjectOrEmpty(
                profile.extraRequestJson
            ),
            overlayWins = true
        )

        val requestBody = finalJson
            .toString()
            .toRequestBody(
                "application/json; charset=utf-8"
                    .toMediaType()
            )

        return Request.Builder()
            .url(endpoint)
            .post(requestBody)
    }

    private fun processResponse(
        response: Response,
        endpoint: String,
        profile: SearchProfileEntity,
        cleanQuery: String,
        maximumResults: Int,
        startedAt: Long,
        appSettings: AppSettings
    ): ApiOutcome<SearchResponse> {
        val body = response.body
            ?.string()
            .orEmpty()

        val duration = System.currentTimeMillis() -
            startedAt

        if (!response.isSuccessful) {
            val parsed = ErrorParser.fromHttpResponse(
                response = response,
                body = body,
                endpoint = endpoint,
                model = null,
                durationMs = duration,
                requestIdHeaderNames =
                    appSettings.diagnostics
                        .requestIdHeaderNames
            )

            return ApiOutcome.Failure(
                error = parsed.first,
                diagnostics = parsed.second
            )
        }

        val diagnostics = buildDiagnostics(
            response = response,
            endpoint = endpoint,
            durationMs = duration,
            requestIdHeaderNames =
                appSettings.diagnostics
                    .requestIdHeaderNames
        )

        val results = parseResults(
            body = body,
            profile = profile,
            maximumResults = maximumResults
        )

        if (results.isEmpty()) {
            return ApiOutcome.Failure(
                error = RequestError(
                    code = "SEARCH_PARSE_ERROR",
                    message =
                        "搜索接口返回成功，但没有按当前字段路径解析到结果。请检查结果数组、标题、URL 和摘要字段。",
                    httpStatus = response.code,
                    requestId = diagnostics.requestId,
                    durationMs = duration,
                    retryable = false,
                    rawBodyPreview = body.take(2000)
                ),
                diagnostics = diagnostics
            )
        }

        return ApiOutcome.Success(
            value = SearchResponse(
                query = cleanQuery,
                results = results
            ),
            diagnostics = diagnostics
        )
    }

    private fun parseResults(
        body: String,
        profile: SearchProfileEntity,
        maximumResults: Int
    ): List<SearchResult> {
        val root = JSONObject(body)

        val resultArray = JsonUtils.readArray(
            root,
            profile.resultArrayPath
        ) ?: return emptyList()

        return buildList {
            val count = minOf(
                resultArray.length(),
                maximumResults
            )

            for (index in 0 until count) {
                val resultObject =
                    resultArray.optJSONObject(index)
                        ?: continue

                val title = JsonUtils.readString(
                    resultObject,
                    profile.resultTitlePath
                ).orEmpty()

                val url = JsonUtils.readString(
                    resultObject,
                    profile.resultUrlPath
                ).orEmpty()

                val snippet = JsonUtils.readString(
                    resultObject,
                    profile.resultSnippetPath
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
                        title = title.ifBlank {
                            url.ifBlank {
                                "搜索结果"
                            }
                        },
                        url = url,
                        snippet = snippet,
                        publishedAt =
                            JsonUtils.readString(
                                resultObject,
                                profile.resultDatePath
                            ),
                        source =
                            JsonUtils.readString(
                                resultObject,
                                profile.resultSourcePath
                            )
                    )
                )
            }
        }
    }

    private fun buildDiagnostics(
        response: Response,
        endpoint: String,
        durationMs: Long,
        requestIdHeaderNames: List<String>
    ): RequestDiagnostics {
        val requestId = requestIdHeaderNames
            .firstNotNullOfOrNull { name ->
                response.header(name)
            }

        return RequestDiagnostics(
            endpoint = endpoint,
            method = response.request.method,
            model = null,
            httpStatus = response.code,
            durationMs = durationMs,
            contentType = response.header(
                "Content-Type"
            ),
            server = response.header("Server"),
            requestId = requestId
        )
    }
}
