package com.example.chatimage.data.api

import com.example.chatimage.data.model.AppSettings
import com.example.chatimage.data.repository.ResolvedApiProfile
import com.example.chatimage.util.HeaderUtils
import com.example.chatimage.util.RequestCategory
import com.example.chatimage.util.UrlUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

class ModelsApiClient(
    private val clientFactory: HttpClientFactory = HttpClientFactory()
) {
    suspend fun fetch(
        resolvedProfile: ResolvedApiProfile,
        settings: AppSettings
    ): Result<List<String>> = withContext(Dispatchers.IO) {
        runCatching {
            val profile = resolvedProfile.profile
            val endpoint = UrlUtils.resolveEndpoint(
                profile.baseUrl,
                profile.modelsPath
            )
            val builder = Request.Builder()
                .url(endpoint)
                .get()
                .header("Accept", "application/json")

            HeaderUtils.applyAuthentication(
                builder = builder,
                mode = profile.authenticationMode,
                apiKey = resolvedProfile.apiKey,
                headerName = profile.authorizationHeaderName,
                prefix = profile.authorizationPrefix
            )
            HeaderUtils.applyCustomHeaders(
                builder = builder,
                entries = HeaderUtils.decode(profile.customHeadersJson),
                category = RequestCategory.CHAT
            )

            clientFactory.chatClient(settings.timeouts)
                .newCall(builder.build())
                .execute()
                .use { response ->
                    val body = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        throw IllegalStateException(
                            "获取模型列表失败：HTTP ${response.code} ${body.take(500)}"
                        )
                    }
                    parseModelIds(body).ifEmpty {
                        throw IllegalStateException(
                            "接口返回成功，但未在 data[].id 中找到模型名称"
                        )
                    }
                }
        }
    }

    companion object {
        fun parseModelIds(body: String): List<String> {
            val parsed: Any = body.trim().let { raw ->
                if (raw.startsWith("[")) JSONArray(raw) else JSONObject(raw)
            }
            val array = when (parsed) {
                is JSONArray -> parsed
                is JSONObject -> parsed.optJSONArray("data") ?: JSONArray()
                else -> JSONArray()
            }

            return buildList {
                for (index in 0 until array.length()) {
                    val item = array.opt(index)
                    val id = when (item) {
                        is JSONObject -> item.optString("id")
                        is String -> item
                        else -> ""
                    }.trim()
                    if (id.isNotBlank()) add(id)
                }
            }.distinct().sorted()
        }
    }
}
