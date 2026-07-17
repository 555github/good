package com.example.chatimage.data.repository

import com.example.chatimage.data.database.ApiProfileDao
import com.example.chatimage.data.database.ApiProfileEntity
import com.example.chatimage.data.settings.SecureKeyStore
import java.util.UUID
import kotlinx.coroutines.flow.Flow

data class ResolvedApiProfile(
    val profile: ApiProfileEntity,
    val apiKey: String
)

class ApiProfileRepository(
    private val dao: ApiProfileDao,
    private val secureKeyStore: SecureKeyStore
) {

    fun observeAll():
        Flow<List<ApiProfileEntity>> {
        return dao.observeAll()
    }

    suspend fun getAll():
        List<ApiProfileEntity> {
        return dao.getAll()
    }

    suspend fun getById(
        profileId: String
    ): ApiProfileEntity? {
        return dao.getById(profileId)
    }

    suspend fun getActive():
        ApiProfileEntity? {
        return dao.getActive()
    }

    suspend fun getResolvedActive():
        ResolvedApiProfile? {
        val profile = dao.getActive()
            ?: return null

        return resolve(profile)
    }

    suspend fun resolveById(
        profileId: String
    ): ResolvedApiProfile? {
        val profile = dao.getById(profileId)
            ?: return null

        return resolve(profile)
    }

    fun resolve(
        profile: ApiProfileEntity
    ): ResolvedApiProfile {
        return ResolvedApiProfile(
            profile = profile,
            apiKey = secureKeyStore.get(
                profile.encryptedApiKeyAlias
            )
        )
    }

    suspend fun create(
        name: String,
        baseUrl: String,
        apiKey: String,
        chatModel: String,
        imageModel: String,
        activate: Boolean = false
    ): ApiProfileEntity {
        val alias = secureKeyStore.createAlias(
            prefix = "api_key"
        )

        secureKeyStore.put(
            alias,
            apiKey
        )

        val entity = ApiProfileEntity(
            id = UUID.randomUUID().toString(),
            name = uniqueName(name),
            isActive = false,
            baseUrl = baseUrl
                .trim()
                .trimEnd('/'),
            encryptedApiKeyAlias = alias,
            chatModel = chatModel.trim(),
            imageModel = imageModel.trim()
        )

        dao.upsert(entity)

        if (
            activate ||
            dao.getActive() == null
        ) {
            dao.setActive(entity.id)
        }

        return dao.getById(entity.id)
            ?: entity
    }

    suspend fun upsert(
        profile: ApiProfileEntity,
        newApiKey: String? = null
    ): ApiProfileEntity {
        var alias =
            profile.encryptedApiKeyAlias

        if (alias.isBlank()) {
            alias = secureKeyStore.createAlias(
                prefix = "api_key"
            )
        }

        if (newApiKey != null) {
            secureKeyStore.put(
                alias,
                newApiKey
            )
        }

        val normalized = profile.copy(
            name = profile.name
                .trim()
                .ifBlank {
                    "未命名 API 配置"
                },
            baseUrl = profile.baseUrl
                .trim()
                .trimEnd('/'),
            encryptedApiKeyAlias = alias,
            chatModel = profile.chatModel.trim(),
            imageModel = profile.imageModel.trim(),
            visionModel =
                profile.visionModel.trim(),
            optimizerModel =
                profile.optimizerModel.trim(),
            chatPath =
                normalizePath(
                    profile.chatPath
                ),
            imageGenerationPath =
                normalizePath(
                    profile.imageGenerationPath
                ),
            imageEditPath =
                normalizePath(
                    profile.imageEditPath
                ),
            modelsPath =
                normalizePath(
                    profile.modelsPath
                ),
            updatedAt =
                System.currentTimeMillis()
        )

        dao.upsert(normalized)

        if (normalized.isActive) {
            dao.setActive(normalized.id)
        }

        return dao.getById(
            normalized.id
        ) ?: normalized
    }

    suspend fun duplicate(
        sourceId: String
    ): ApiProfileEntity? {
        val source = dao.getById(sourceId)
            ?: return null

        val sourceKey = secureKeyStore.get(
            source.encryptedApiKeyAlias
        )

        val newAlias =
            secureKeyStore.createAlias(
                prefix = "api_key"
            )

        secureKeyStore.put(
            newAlias,
            sourceKey
        )

        val copy = source.copy(
            id = UUID.randomUUID().toString(),
            name = uniqueName(
                "${source.name} 副本"
            ),
            isActive = false,
            encryptedApiKeyAlias =
                newAlias,
            createdAt =
                System.currentTimeMillis(),
            updatedAt =
                System.currentTimeMillis()
        )

        dao.upsert(copy)

        return copy
    }

    suspend fun setActive(
        profileId: String
    ) {
        val profile = dao.getById(profileId)
            ?: return

        if (!profile.enabled) {
            return
        }

        dao.setActive(profileId)
    }

    suspend fun delete(
        profileId: String
    ) {
        val profile = dao.getById(profileId)
            ?: return

        val wasActive = profile.isActive

        dao.delete(profile)

        secureKeyStore.remove(
            profile.encryptedApiKeyAlias
        )

        if (wasActive) {
            dao.getAll()
                .firstOrNull {
                    it.enabled
                }
                ?.let {
                    dao.setActive(it.id)
                }
        }
    }

    suspend fun updateApiKey(
        profileId: String,
        newApiKey: String
    ) {
        val profile = dao.getById(profileId)
            ?: return

        secureKeyStore.put(
            profile.encryptedApiKeyAlias,
            newApiKey
        )

        dao.upsert(
            profile.copy(
                updatedAt =
                    System.currentTimeMillis()
            )
        )
    }

    suspend fun clearApiKey(
        profileId: String
    ) {
        val profile = dao.getById(profileId)
            ?: return

        secureKeyStore.remove(
            profile.encryptedApiKeyAlias
        )
    }

    suspend fun ensureDefaultProfile() {
        if (dao.getAll().isNotEmpty()) {
            return
        }

        create(
            name = "默认线路",
            baseUrl =
                "https://api.example.com/v1",
            apiKey = "",
            chatModel = "gpt-4o",
            imageModel = "image-2",
            activate = true
        )
    }

    private suspend fun uniqueName(
        requested: String
    ): String {
        val base = requested
            .trim()
            .ifBlank {
                "未命名 API 配置"
            }

        val existingNames = dao.getAll()
            .map {
                it.name
            }
            .toSet()

        if (base !in existingNames) {
            return base
        }

        var number = 2

        while (
            "$base $number" in existingNames
        ) {
            number++
        }

        return "$base $number"
    }

    private fun normalizePath(
        value: String
    ): String {
        val trimmed = value.trim()

        if (trimmed.isBlank()) {
            return ""
        }

        if (
            trimmed.startsWith("http://") ||
            trimmed.startsWith("https://")
        ) {
            return trimmed
        }

        return if (
            trimmed.startsWith("/")
        ) {
            trimmed
        } else {
            "/$trimmed"
        }
    }
}
