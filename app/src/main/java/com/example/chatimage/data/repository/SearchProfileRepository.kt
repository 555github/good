package com.example.chatimage.data.repository

import com.example.chatimage.data.database.SearchProfileDao
import com.example.chatimage.data.database.SearchProfileEntity
import com.example.chatimage.data.settings.SecureKeyStore
import java.util.UUID
import kotlinx.coroutines.flow.Flow

data class ResolvedSearchProfile(
    val profile: SearchProfileEntity,
    val apiKey: String
)

class SearchProfileRepository(
    private val dao: SearchProfileDao,
    private val secureKeyStore: SecureKeyStore
) {

    fun observeAll():
        Flow<List<SearchProfileEntity>> {
        return dao.observeAll()
    }

    suspend fun getAll():
        List<SearchProfileEntity> {
        return dao.getAll()
    }

    suspend fun getById(
        profileId: String
    ): SearchProfileEntity? {
        return dao.getById(profileId)
    }

    suspend fun getActive():
        SearchProfileEntity? {
        return dao.getActive()
    }

    suspend fun getResolvedActive():
        ResolvedSearchProfile? {
        val profile = dao.getActive()
            ?: return null

        return resolve(profile)
    }

    suspend fun resolveById(
        profileId: String
    ): ResolvedSearchProfile? {
        val profile = dao.getById(profileId)
            ?: return null

        return resolve(profile)
    }

    fun resolve(
        profile: SearchProfileEntity
    ): ResolvedSearchProfile {
        return ResolvedSearchProfile(
            profile = profile,
            apiKey = secureKeyStore.get(
                profile.encryptedApiKeyAlias
            )
        )
    }

    suspend fun create(
        name: String,
        baseUrl: String,
        path: String,
        apiKey: String,
        activate: Boolean = false
    ): SearchProfileEntity {
        val alias = secureKeyStore.createAlias(
            prefix = "search_key"
        )

        secureKeyStore.put(
            alias,
            apiKey
        )

        val entity = SearchProfileEntity(
            id = UUID.randomUUID().toString(),
            name = uniqueName(name),
            isActive = false,
            baseUrl = baseUrl
                .trim()
                .trimEnd('/'),
            path = normalizePath(path),
            encryptedApiKeyAlias = alias
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
        profile: SearchProfileEntity,
        newApiKey: String? = null
    ): SearchProfileEntity {
        var alias =
            profile.encryptedApiKeyAlias

        if (alias.isBlank()) {
            alias = secureKeyStore.createAlias(
                prefix = "search_key"
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
                    "未命名搜索配置"
                },
            baseUrl = profile.baseUrl
                .trim()
                .trimEnd('/'),
            path = normalizePath(
                profile.path
            ),
            encryptedApiKeyAlias = alias,
            providerType =
                profile.providerType.trim(),
            requestMethod =
                profile.requestMethod
                    .trim()
                    .uppercase(),
            authorizationHeaderName =
                profile
                    .authorizationHeaderName
                    .trim(),
            queryField =
                profile.queryField.trim(),
            countField =
                profile.countField.trim(),
            languageField =
                profile.languageField.trim(),
            regionField =
                profile.regionField.trim(),
            resultArrayPath =
                profile.resultArrayPath.trim(),
            resultTitlePath =
                profile.resultTitlePath.trim(),
            resultUrlPath =
                profile.resultUrlPath.trim(),
            resultSnippetPath =
                profile.resultSnippetPath.trim(),
            resultDatePath =
                profile.resultDatePath.trim(),
            resultSourcePath =
                profile.resultSourcePath.trim(),
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
    ): SearchProfileEntity? {
        val source = dao.getById(sourceId)
            ?: return null

        val sourceKey = secureKeyStore.get(
            source.encryptedApiKeyAlias
        )

        val newAlias =
            secureKeyStore.createAlias(
                prefix = "search_key"
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

    private suspend fun uniqueName(
        requested: String
    ): String {
        val base = requested
            .trim()
            .ifBlank {
                "未命名搜索配置"
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
