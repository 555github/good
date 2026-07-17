package com.example.chatimage.data.api

import android.content.Context
import com.example.chatimage.data.model.AppSettings
import com.example.chatimage.data.model.ImageEditTransport
import com.example.chatimage.data.model.RequestDiagnostics
import com.example.chatimage.data.repository.ResolvedApiProfile
import com.example.chatimage.util.ErrorParser
import com.example.chatimage.util.HeaderUtils
import com.example.chatimage.util.ImageFileUtils
import com.example.chatimage.util.JsonUtils
import com.example.chatimage.util.RequestCategory
import com.example.chatimage.util.UrlUtils
import java.io.File
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

class ImageApiClient(
    private val context: Context,
    private val clientFactory: HttpClientFactory =
        HttpClientFactory()
) {

    suspend fun generate(
        resolvedProfile: ResolvedApiProfile,
        appSettings: AppSettings,
        options: ImageRequestOptions
    ): ApiOutcome<ImageGenerationResult> =
        withContext(Dispatchers.IO) {
            val profile =
                resolvedProfile.profile

            val settings =
                appSettings.imageParameters

            val isEdit =
                !options.sourceImagePath
                    .isNullOrBlank()

            val endpoint =
                UrlUtils.resolveEndpoint(
                    profile.baseUrl,
                    if (isEdit) {
                        profile.imageEditPath
                    } else {
                        profile
                            .imageGenerationPath
                    }
                )

            val startedAt =
                System.currentTimeMillis()

            var call: Call? = null

            try {
                val body = if (!isEdit) {
                    buildGenerationBody(
                        settings = settings,
                        options = options
                    )
                } else {
                    val sourceFile = File(
                        options.sourceImagePath
                            ?: throw
                                IllegalStateException(
                                    "图生图缺少源图片"
                                )
                    )

                    if (!sourceFile.exists()) {
                        throw IllegalStateException(
                            "图生图源图片文件不存在"
                        )
                    }

                    buildEditBody(
                        settings = settings,
                        options = options,
                        sourceFile = sourceFile
                    )
                }

                val builder = Request.Builder()
                    .url(endpoint)
                    .header(
                        "Accept",
                        "application/json"
                    )
                    .post(body)

                HeaderUtils.applyAuthentication(
                    builder = builder,
                    mode = profile
                        .authenticationMode,
                    apiKey = resolvedProfile.apiKey,
                    headerName = profile
                        .authorizationHeaderName,
                    prefix = profile
                        .authorizationPrefix
                )

                HeaderUtils.applyCustomHeaders(
                    builder = builder,
                    entries = HeaderUtils.decode(
                        profile.customHeadersJson
                    ),
                    category =
                        RequestCategory.IMAGE
                )

                val client =
                    clientFactory.imageClient(
                        appSettings.timeouts
                    )

                val requestCall = client.newCall(
                    builder.build()
                )

                call = requestCall

                val cancellationHandle =
                    coroutineContext.job
                        .invokeOnCompletion { cause ->
                            if (
                                cause is
                                CancellationException
                            ) {
                                requestCall.cancel()
                            }
                        }

                try {
                    requestCall.execute().use {


                        response ->
                        val responseText =
                            response.body
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
                                        body =
                                            responseText,
                                        endpoint = endpoint,
                                        model =
                                            options.model,
                                        durationMs =
                                            duration,
                                        requestIdHeaderNames =
                                            appSettings
                                                .diagnostics
                                                .requestIdHeaderNames
                                    )

                            return@withContext
                                ApiOutcome.Failure(
                                    error = parsed.first,
                                    diagnostics =
                                        parsed.second
                                )
                        }

                        val images =
                            parseAndSaveImages(
                                responseText =
                                    responseText,
                                resolvedProfile =
                                    resolvedProfile,
                                appSettings =
                                    appSettings
                            )

                        if (images.isEmpty()) {
                            val diagnostics =
                                diagnostics(
                                    response,
                                    endpoint,
                                    options.model,
                                    duration
                                )

                            return@withContext
                                ApiOutcome.Failure(
                                    error =
                                        com.example
                                            .chatimage
                                            .data
                                            .model
                                            .RequestError(
                                                code =
                                                    "NO_IMAGE_DATA",
                                                message =
                                                    "图片接口返回成功，但没有按当前字段路径解析到图片。请检查图片数组路径、URL 字段和 Base64 字段设置。",
                                                httpStatus =
                                                    response.code,
                                                requestId =
                                                    diagnostics
                                                        .requestId,
                                                durationMs =
                                                    duration,
                                                retryable =
                                                    false,
                                                rawBodyPreview =
                                                    responseText
                                                        .take(
                                                            2000
                                                        )
                                            ),
                                    diagnostics =
                                        diagnostics
                                )
                        }

                        val root = try {
                            JSONObject(responseText)
                        } catch (_: Exception) {
                            JSONObject()
                        }

                        val revisedPrompt =
                            JsonUtils.readString(
                                root,
                                "data[0].revised_prompt"
                            )

                        return@withContext
                            ApiOutcome.Success(
                                value =
                                    ImageGenerationResult(
                                        images = images,
                                        revisedPrompt =
                                            revisedPrompt
                                    ),
                                diagnostics =
                                    diagnostics(
                                        response,
                                        endpoint,
                                        options.model,
                                        duration
                                    )
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
                val duration =
                    System.currentTimeMillis() -
                        startedAt

                val parsed =
                    ErrorParser.fromException(
                        exception = exception,
                        endpoint = endpoint,
                        model = options.model,
                        durationMs = duration
                    )

                ApiOutcome.Failure(
                    error = parsed.first,
                    diagnostics = parsed.second
                )
            }
        }

    private fun buildGenerationBody(
        settings:
            com.example.chatimage
                .data.model
                .ImageParameterSettings,
        options: ImageRequestOptions
    ): RequestBody {
        val standard = JSONObject()

        standard.put(
            settings.modelFieldName,
            options.model
        )

        standard.put(
            settings.promptFieldName,
            options.prompt
        )

        addOptionalImageFields(
            standard,
            settings,
            options
        )

        val extra =
            JsonUtils.parseObjectOrEmpty(
                settings.extraGenerationJson
            )

        return JsonUtils.deepMerge(
            standard,
            extra,
            overlayWins = true
        )
            .toString()
            .toRequestBody(
                "application/json; charset=utf-8"
                    .toMediaType()
            )
    }

    private fun buildEditBody(
        settings:
            com.example.chatimage
                .data.model
                .ImageParameterSettings,
        options: ImageRequestOptions,
        sourceFile: File
    ): RequestBody {
        return when (
            settings.imageEditTransport
        ) {
            ImageEditTransport.MULTIPART -> {
                buildMultipartEditBody(
                    settings,
                    options,
                    sourceFile
                )
            }

            ImageEditTransport.BASE64_JSON,
            ImageEditTransport.DATA_URL_JSON -> {
                val includePrefix =
                    settings
                        .imageEditTransport ==
                        ImageEditTransport
                            .DATA_URL_JSON ||
                        settings
                            .includeDataUrlPrefix

                val imageValue =
                    ImageFileUtils.fileToBase64(
                        sourceFile,
                        includePrefix
                    )

                buildJsonEditBody(
                    settings = settings,
                    options = options,
                    imageValue = imageValue
                )
            }

            ImageEditTransport.IMAGE_URL_JSON -> {
                throw IllegalStateException(
                    "当前源图片是手机本地文件，不能直接作为公网 image_url。请选择 Multipart、Base64 JSON 或 Data URL JSON。"
                )
            }
        }
    }

    private fun buildMultipartEditBody(
        settings:
            com.example.chatimage
                .data.model
                .ImageParameterSettings,
        options: ImageRequestOptions,
        sourceFile: File
    ): RequestBody {
        val builder = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                settings.modelFieldName,
                options.model
            )
            .addFormDataPart(
                settings.promptFieldName,
                options.prompt
            )
            .addFormDataPart(
                settings.imageFieldName,
                sourceFile.name,
                sourceFile.asRequestBody(
                    ImageFileUtils
                        .mimeTypeForFile(
                            sourceFile
                        )
                        .toMediaType()
                )
            )

        if (settings.sizeEnabled) {
            builder.addFormDataPart(
                settings.sizeFieldName,
                options.size
            )
        }

        if (settings.qualityEnabled) {
            builder.addFormDataPart(
                settings.qualityFieldName,
                options.quality
            )
        }

        if (settings.countEnabled) {
            builder.addFormDataPart(
                settings.countFieldName,
                options.count.toString()
            )
        }

        if (
            settings.responseFormatEnabled
        ) {
            builder.addFormDataPart(
                settings
                    .responseFormatFieldName,
                options.responseFormat
            )
        }

        val extra =
            JsonUtils.parseObjectOrEmpty(
                settings.extraEditJson
            )

        val keys = extra.keys()

        while (keys.hasNext()) {
            val key = keys.next()
            val value = extra.opt(key)

            if (
                value != null &&
                value != JSONObject.NULL
            ) {
                builder.addFormDataPart(
                    key,
                    JsonUtils.toCompactString(
                        value
                    )
                )
            }
        }

        return builder.build()
    }

    private fun buildJsonEditBody(
        settings:
            com.example.chatimage
                .data.model
                .ImageParameterSettings,
        options: ImageRequestOptions,
        imageValue: String
    ): RequestBody {
        val standard = JSONObject()
            .put(
                settings.modelFieldName,
                options.model
            )
            .put(
                settings.promptFieldName,
                options.prompt
            )
            .put(
                settings.imageFieldName,
                imageValue
            )

        addOptionalImageFields(
            standard,
            settings,
            options
        )

        val extra =
            JsonUtils.parseObjectOrEmpty(
                settings.extraEditJson
            )

        return JsonUtils.deepMerge(
            standard,
            extra,
            overlayWins = true
        )
            .toString()
            .toRequestBody(
                "application/json; charset=utf-8"
                    .toMediaType()
            )
    }

    private fun addOptionalImageFields(
        json: JSONObject,
        settings:
            com.example.chatimage
                .data.model
                .ImageParameterSettings,
        options: ImageRequestOptions
    ) {
        if (settings.sizeEnabled) {
            json.put(
                settings.sizeFieldName,
                options.size
            )
        }

        if (settings.qualityEnabled) {
            json.put(
                settings.qualityFieldName,
                options.quality
            )
        }

        if (settings.countEnabled) {
            json.put(
                settings.countFieldName,
                options.count
            )
        }

        if (
            settings.responseFormatEnabled
        ) {
            json.put(
                settings
                    .responseFormatFieldName,
                options.responseFormat
            )
        }
    }

    private fun parseAndSaveImages(
        responseText: String,
        resolvedProfile:
            ResolvedApiProfile,
        appSettings: AppSettings
    ): List<SavedImageResult> {
        val root = JSONObject(responseText)

        val settings =
            appSettings.imageParameters

        val array = JsonUtils.readArray(
            root,
            settings.imageArrayPath
        ) ?: if (settings.imageArrayPath != "images") {
            root.optJSONArray("images")
        } else {
            null
        } ?: return emptyList()

        val results =
            mutableListOf<SavedImageResult>()

        for (
            index in 0 until
                array.length()
        ) {
            val item = array.opt(index)

            if (item is String) {
                when {
                    item.startsWith(
                        "data:image",
                        ignoreCase = true
                    ) -> {
                        results +=
                            ImageFileUtils
                                .saveBase64Image(
                                    context,
                                    item
                                )
                    }

                    UrlUtils.isHttpOrHttps(
                        item
                    ) -> {
                        results += downloadImage(
                            rawUrl = item,
                            resolvedProfile =
                                resolvedProfile,
                            appSettings =
                                appSettings
                        )
                    }
                }

                continue
            }

            val objectItem =
                item as? JSONObject
                    ?: continue

            val base64 =
                JsonUtils.firstNonBlankString(
                    objectItem,
                    settings
                        .imageBase64Paths
                )

            if (!base64.isNullOrBlank()) {
                results +=
                    ImageFileUtils
                        .saveBase64Image(
                            context,
                            base64
                        )

                continue
            }

            val rawUrl =
                JsonUtils.firstNonBlankString(
                    objectItem,
                    settings.imageUrlPaths
                )

            if (!rawUrl.isNullOrBlank()) {
                if (
                    rawUrl.startsWith(
                        "data:image",
                        ignoreCase = true
                    )
                ) {
                    results +=
                        ImageFileUtils
                            .saveBase64Image(
                                context,
                                rawUrl
                            )
                } else {
                    results += downloadImage(
                        rawUrl = rawUrl,
                        resolvedProfile =
                            resolvedProfile,
                        appSettings =
                            appSettings
                    )
                }
            }
        }

        return results
    }

    private fun downloadImage(
        rawUrl: String,
        resolvedProfile:
            ResolvedApiProfile,
        appSettings: AppSettings
    ): SavedImageResult {
        val profile =
            resolvedProfile.profile

        val imageSettings =
            appSettings.imageParameters

        val base = imageSettings
            .relativeUrlBase
            .ifBlank {
                profile.baseUrl
            }

        val absoluteUrl =
            UrlUtils.resolveRelativeUrl(
                base,
                rawUrl
            )

        val builder = Request.Builder()
            .url(absoluteUrl)
            .get()
            .header(
                "Accept",
                "image/*,*/*;q=0.8"
            )

        when (
            imageSettings
                .downloadAuthenticationMode
                .uppercase()
        ) {
            "IMAGE_API",
            "CHAT_API" -> {
                HeaderUtils.applyAuthentication(
                    builder = builder,
                    mode = profile
                        .authenticationMode,
                    apiKey =
                        resolvedProfile.apiKey,
                    headerName = profile
                        .authorizationHeaderName,
                    prefix = profile
                        .authorizationPrefix
                )
            }

            "NONE" -> Unit

            else -> {
                HeaderUtils.applyAuthentication(
                    builder = builder,
                    mode = profile
                        .authenticationMode,
                    apiKey =
                        resolvedProfile.apiKey,
                    headerName = profile
                        .authorizationHeaderName,
                    prefix = profile
                        .authorizationPrefix
                )
            }
        }

        HeaderUtils.applyCustomHeaders(
            builder = builder,
            entries = HeaderUtils.decode(
                profile.customHeadersJson
            ),
            category =
                RequestCategory.DOWNLOAD
        )

        val client =
            clientFactory.imageDownloadClient(
                appSettings.timeouts
            )

        client.newCall(
            builder.build()
        ).execute().use { response ->
            if (!response.isSuccessful) {
                val errorText =
                    response.body
                        ?.string()
                        .orEmpty()

                throw IllegalStateException(
                    "图片已经生成，但下载失败：" +
                        "HTTP ${response.code}\n" +
                        (
                            ErrorParser
                                .parseJsonError(
                                    errorText
                                )
                                ?: if (
                                    errorText
                                        .contains(
                                            "<html",
                                            ignoreCase =
                                                true
                                        )
                                ) {
                                    ErrorParser
                                        .parseHtmlError(
                                            errorText
                                        )
                                } else {
                                    errorText.take(500)
                                }
                            )
                )
            }

            val contentType =
                response.header(
                    "Content-Type"
                )
                    ?.substringBefore(";")
                    ?.trim()
                    .orEmpty()

            val bytes =
                response.body?.bytes()
                    ?: throw
                        IllegalStateException(
                            "下载到的图片为空"
                        )

            val saved =
                ImageFileUtils.saveImageBytes(
                    context = context,
                    bytes = bytes,
                    mimeType =
                        contentType.ifBlank {
                            "application/octet-stream"
                        }
                )

            return saved.copy(
                originalUrl = rawUrl
            )
        }
    }

    private fun diagnostics(
        response: okhttp3.Response,
        endpoint: String,
        model: String,
        duration: Long
    ): RequestDiagnostics {
        val requestId =
            listOf(
                "x-request-id",
                "request-id",
                "cf-ray"
            ).firstNotNullOfOrNull {
                response.header(it)
            }

        return RequestDiagnostics(
            endpoint = endpoint,
            method =
                response.request.method,
            model = model,
            httpStatus = response.code,
            durationMs = duration,
            contentType =
                response.header(
                    "Content-Type"
                ),
            server =
                response.header("Server"),
            requestId = requestId
        )
    }
}
