package com.example.chatimage.data.api

import android.content.Context
import com.example.chatimage.data.model.AppSettings
import com.example.chatimage.data.model.ImageEditTransport
import com.example.chatimage.data.model.ImageParameterSettings
import com.example.chatimage.data.model.RequestDiagnostics
import com.example.chatimage.data.model.RequestError
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
import okhttp3.Response
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
    ): ApiOutcome<ImageGenerationResult> {
        return withContext(Dispatchers.IO) {
            executeRequest(
                resolvedProfile = resolvedProfile,
                appSettings = appSettings,
                options = options
            )
        }
    }

    private suspend fun executeRequest(
        resolvedProfile: ResolvedApiProfile,
        appSettings: AppSettings,
        options: ImageRequestOptions
    ): ApiOutcome<ImageGenerationResult> {
        val profile = resolvedProfile.profile
        val parameters = appSettings.imageParameters

        val isEdit = !options.sourceImagePath
            .isNullOrBlank()

        val endpoint = UrlUtils.resolveEndpoint(
            profile.baseUrl,
            if (isEdit) {
                profile.imageEditPath
            } else {
                profile.imageGenerationPath
            }
        )

        val startedAt = System.currentTimeMillis()

        var activeCall: Call? = null

        try {
            val requestBody = if (isEdit) {
                val sourceFile = File(
                    options.sourceImagePath
                        ?: throw IllegalStateException(
                            "图生图缺少源图片"
                        )
                )

                if (!sourceFile.exists()) {
                    throw IllegalStateException(
                        "图生图源图片文件不存在"
                    )
                }

                buildEditBody(
                    settings = parameters,
                    options = options,
                    sourceFile = sourceFile
                )
            } else {
                buildGenerationBody(
                    settings = parameters,
                    options = options
                )
            }

            val requestBuilder = Request.Builder()
                .url(endpoint)
                .post(requestBody)
                .header(
                    "Accept",
                    "application/json"
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
                category = RequestCategory.IMAGE
            )

            val call = clientFactory
                .imageClient(appSettings.timeouts)
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
                        resolvedProfile =
                            resolvedProfile,
                        appSettings = appSettings,
                        options = options,
                        startedAt = startedAt
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
                model = options.model,
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

    private fun processResponse(
        response: Response,
        endpoint: String,
        resolvedProfile: ResolvedApiProfile,
        appSettings: AppSettings,
        options: ImageRequestOptions,
        startedAt: Long
    ): ApiOutcome<ImageGenerationResult> {
        val responseText = response.body
            ?.string()
            .orEmpty()

        val duration =
            System.currentTimeMillis() - startedAt

        if (!response.isSuccessful) {
            val parsed = ErrorParser.fromHttpResponse(
                response = response,
                body = responseText,
                endpoint = endpoint,
                model = options.model,
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
            model = options.model,
            durationMs = duration,
            requestIdHeaderNames =
                appSettings.diagnostics
                    .requestIdHeaderNames
        )

        val images = parseAndSaveImages(
            responseText = responseText,
            resolvedProfile = resolvedProfile,
            appSettings = appSettings
        )

        if (images.isEmpty()) {
            return ApiOutcome.Failure(
                error = RequestError(
                    code = "NO_IMAGE_DATA",
                    message =
                        "图片接口返回成功，但没有按当前字段路径解析到图片。请检查图片数组路径、URL 字段和 Base64 字段设置。",
                    httpStatus = response.code,
                    requestId = diagnostics.requestId,
                    durationMs = duration,
                    retryable = false,
                    rawBodyPreview =
                        responseText.take(2000)
                ),
                diagnostics = diagnostics
            )
        }

        val responseJson = try {
            JSONObject(responseText)
        } catch (_: Exception) {
            JSONObject()
        }

        val revisedPrompt = JsonUtils.readString(
            responseJson,
            "data[0].revised_prompt"
        )

        return ApiOutcome.Success(
            value = ImageGenerationResult(
                images = images,
                revisedPrompt = revisedPrompt
            ),
            diagnostics = diagnostics
        )
    }

    private fun buildGenerationBody(
        settings: ImageParameterSettings,
        options: ImageRequestOptions
    ): RequestBody {
        val standardJson = JSONObject()
            .put(
                settings.modelFieldName,
                options.model
            )
            .put(
                settings.promptFieldName,
                options.prompt
            )

        addOptionalImageFields(
            json = standardJson,
            settings = settings,
            options = options
        )

        val finalJson = JsonUtils.deepMerge(
            base = standardJson,
            overlay = JsonUtils.parseObjectOrEmpty(
                settings.extraGenerationJson
            ),
            overlayWins = true
        )

        return finalJson
            .toString()
            .toRequestBody(
                "application/json; charset=utf-8"
                    .toMediaType()
            )
    }

    private fun buildEditBody(
        settings: ImageParameterSettings,
        options: ImageRequestOptions,
        sourceFile: File
    ): RequestBody {
        return when (settings.imageEditTransport) {
            ImageEditTransport.MULTIPART -> {
                buildMultipartEditBody(
                    settings = settings,
                    options = options,
                    sourceFile = sourceFile
                )
            }

            ImageEditTransport.BASE64_JSON -> {
                buildJsonEditBody(
                    settings = settings,
                    options = options,
                    imageValue =
                        ImageFileUtils.fileToBase64(
                            sourceFile,
                            settings.includeDataUrlPrefix
                        )
                )
            }

            ImageEditTransport.DATA_URL_JSON -> {
                buildJsonEditBody(
                    settings = settings,
                    options = options,
                    imageValue =
                        ImageFileUtils.fileToBase64(
                            sourceFile,
                            true
                        )
                )
            }

            ImageEditTransport.IMAGE_URL_JSON -> {
                throw IllegalStateException(
                    "当前源图片位于手机本地，不能直接作为公网 image_url。请选择 MULTIPART、BASE64_JSON 或 DATA_URL_JSON。"
                )
            }
        }
    }

    private fun buildMultipartEditBody(
        settings: ImageParameterSettings,
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
                        .mimeTypeForFile(sourceFile)
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

        if (settings.responseFormatEnabled) {
            builder.addFormDataPart(
                settings.responseFormatFieldName,
                options.responseFormat
            )
        }

        val extraJson = JsonUtils.parseObjectOrEmpty(
            settings.extraEditJson
        )

        val keys = extraJson.keys()

        while (keys.hasNext()) {
            val key = keys.next()
            val value = extraJson.opt(key)

            if (
                value != null &&
                value != JSONObject.NULL
            ) {
                builder.addFormDataPart(
                    key,
                    JsonUtils.toCompactString(value)
                )
            }
        }

        return builder.build()
    }

    private fun buildJsonEditBody(
        settings: ImageParameterSettings,
        options: ImageRequestOptions,
        imageValue: String
    ): RequestBody {
        val standardJson = JSONObject()
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
            json = standardJson,
            settings = settings,
            options = options
        )

        val finalJson = JsonUtils.deepMerge(
            base = standardJson,
            overlay = JsonUtils.parseObjectOrEmpty(
                settings.extraEditJson
            ),
            overlayWins = true
        )

        return finalJson
            .toString()
            .toRequestBody(
                "application/json; charset=utf-8"
                    .toMediaType()
            )
    }

    private fun addOptionalImageFields(
        json: JSONObject,
        settings: ImageParameterSettings,
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

        if (settings.responseFormatEnabled) {
            json.put(
                settings.responseFormatFieldName,
                options.responseFormat
            )
        }
    }

    private fun parseAndSaveImages(
        responseText: String,
        resolvedProfile: ResolvedApiProfile,
        appSettings: AppSettings
    ): List<SavedImageResult> {
        val root = JSONObject(responseText)
        val settings = appSettings.imageParameters

        val imageArray = JsonUtils.readArray(
            root,
            settings.imageArrayPath
        ) ?: root.optJSONArray("data")
            ?: root.optJSONArray("images")
            ?: return emptyList()

        val results =
            mutableListOf<SavedImageResult>()

        for (
            index in 0 until imageArray.length()
        ) {
            val item = imageArray.opt(index)

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

                    UrlUtils.isHttpOrHttps(item) -> {
                        results += downloadImage(
                            rawUrl = item,
                            resolvedProfile =
                                resolvedProfile,
                            appSettings = appSettings
                        )
                    }
                }

                continue
            }

            val imageObject = item as? JSONObject
                ?: continue

            val base64Value =
                JsonUtils.firstNonBlankString(
                    imageObject,
                    settings.imageBase64Paths
                )

            if (!base64Value.isNullOrBlank()) {
                results +=
                    ImageFileUtils.saveBase64Image(
                        context,
                        base64Value
                    )

                continue
            }

            val rawUrl =
                JsonUtils.firstNonBlankString(
                    imageObject,
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
                        appSettings = appSettings
                    )
                }
            }
        }

        return results
    }

    private fun downloadImage(
        rawUrl: String,
        resolvedProfile: ResolvedApiProfile,
        appSettings: AppSettings
    ): SavedImageResult {
        val profile = resolvedProfile.profile
        val settings = appSettings.imageParameters

        val baseUrl = settings.relativeUrlBase
            .ifBlank {
                profile.baseUrl
            }

        val absoluteUrl = UrlUtils.resolveRelativeUrl(
            baseUrl,
            rawUrl
        )

        val requestBuilder = Request.Builder()
            .url(absoluteUrl)
            .get()
            .header(
                "Accept",
                "image/*,*/*;q=0.8"
            )

        when (
            settings.downloadAuthenticationMode
                .uppercase()
        ) {
            "NONE" -> Unit

            else -> {
                HeaderUtils.applyAuthentication(
                    builder = requestBuilder,
                    mode =
                        profile.authenticationMode,
                    apiKey =
                        resolvedProfile.apiKey,
                    headerName =
                        profile
                            .authorizationHeaderName,
                    prefix =
                        profile.authorizationPrefix
                )
            }
        }

        HeaderUtils.applyCustomHeaders(
            builder = requestBuilder,
            entries = HeaderUtils.decode(
                profile.customHeadersJson
            ),
            category = RequestCategory.DOWNLOAD
        )

        val response = clientFactory
            .imageDownloadClient(
                appSettings.timeouts
            )
            .newCall(requestBuilder.build())
            .execute()

        return response.use { value ->
            if (!value.isSuccessful) {
                val errorText = value.body
                    ?.string()
                    .orEmpty()

                val readableError =
                    ErrorParser.parseJsonError(
                        errorText
                    ) ?: if (
                        errorText.contains(
                            "<html",
                            ignoreCase = true
                        )
                    ) {
                        ErrorParser.parseHtmlError(
                            errorText
                        )
                    } else {
                        errorText.take(1000)
                    }

                throw IllegalStateException(
                    "图片已生成，但下载失败：HTTP ${value.code}\n$readableError"
                )
            }

            val bytes = value.body
                ?.bytes()
                ?: throw IllegalStateException(
                    "下载到的图片内容为空"
                )

            val contentType = value.header(
                "Content-Type"
            ).orEmpty()

            ImageFileUtils.saveImageBytes(
                context = context,
                bytes = bytes,
                mimeType = contentType
            ).copy(
                originalUrl = rawUrl
            )
        }
    }

    private fun buildDiagnostics(
        response: Response,
        endpoint: String,
        model: String,
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
            model = model,
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
