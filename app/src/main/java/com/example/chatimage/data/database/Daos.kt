package com.example.chatimage.data.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

data class MessageWithImages(
    @androidx.room.Embedded
    val message: MessageEntity,

    @androidx.room.Relation(
        parentColumn = "id",
        entityColumn = "messageId"
    )
    val images: List<MessageImageEntity>
)

data class ConversationWithMessages(
    @androidx.room.Embedded
    val conversation: ConversationEntity,

    @androidx.room.Relation(
        entity = MessageEntity::class,
        parentColumn = "id",
        entityColumn = "conversationId"
    )
    val rawMessages: List<MessageEntity>
)

@Dao
interface ConversationDao {

    @Query(
        """
        SELECT * FROM conversations
        WHERE archived = 0
        ORDER BY pinned DESC, updatedAt DESC
        """
    )
    fun observeAll(): Flow<List<ConversationEntity>>

    @Query(
        """
        SELECT * FROM conversations
        WHERE archived = 0
          AND title LIKE '%' || :query || '%'
        ORDER BY pinned DESC, updatedAt DESC
        """
    )
    fun search(query: String): Flow<List<ConversationEntity>>

    @Query(
        """
        SELECT * FROM conversations
        WHERE id = :conversationId
        LIMIT 1
        """
    )
    fun observeById(
        conversationId: String
    ): Flow<ConversationEntity?>

    @Query(
        """
        SELECT * FROM conversations
        WHERE id = :conversationId
        LIMIT 1
        """
    )
    suspend fun getById(
        conversationId: String
    ): ConversationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(
        conversation: ConversationEntity
    )

    @Update
    suspend fun update(
        conversation: ConversationEntity
    )

    @Delete
    suspend fun delete(
        conversation: ConversationEntity
    )

    @Query(
        """
        DELETE FROM conversations
        WHERE id = :conversationId
        """
    )
    suspend fun deleteById(
        conversationId: String
    )

    @Query(
        """
        UPDATE conversations
        SET title = :title,
            updatedAt = :updatedAt
        WHERE id = :conversationId
        """
    )
    suspend fun rename(
        conversationId: String,
        title: String,
        updatedAt: Long = System.currentTimeMillis()
    )

    @Query(
        """
        UPDATE conversations
        SET pinned = :pinned,
            updatedAt = :updatedAt
        WHERE id = :conversationId
        """
    )
    suspend fun setPinned(
        conversationId: String,
        pinned: Boolean,
        updatedAt: Long = System.currentTimeMillis()
    )

    @Query(
        """
        UPDATE conversations
        SET updatedAt = :updatedAt
        WHERE id = :conversationId
        """
    )
    suspend fun touch(
        conversationId: String,
        updatedAt: Long = System.currentTimeMillis()
    )

    @Query(
        """
        DELETE FROM conversations
        """
    )
    suspend fun deleteAll()
}

@Dao
interface MessageDao {

    @Transaction
    @Query(
        """
        SELECT * FROM messages
        WHERE conversationId = :conversationId
        ORDER BY createdAt ASC
        """
    )
    fun observeForConversation(
        conversationId: String
    ): Flow<List<MessageWithImages>>

    @Transaction
    @Query(
        """
        SELECT * FROM messages
        WHERE conversationId = :conversationId
        ORDER BY createdAt ASC
        """
    )
    suspend fun getForConversation(
        conversationId: String
    ): List<MessageWithImages>

    @Query(
        """
        SELECT * FROM messages
        WHERE id = :messageId
        LIMIT 1
        """
    )
    suspend fun getById(
        messageId: String
    ): MessageEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(
        message: MessageEntity
    )

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(
        messages: List<MessageEntity>
    )

    @Update
    suspend fun update(
        message: MessageEntity
    )

    @Delete
    suspend fun delete(
        message: MessageEntity
    )

    @Query(
        """
        DELETE FROM messages
        WHERE id = :messageId
        """
    )
    suspend fun deleteById(
        messageId: String
    )

    @Query(
        """
        DELETE FROM messages
        WHERE conversationId = :conversationId
        """
    )
    suspend fun deleteForConversation(
        conversationId: String
    )

    @Query(
        """
        UPDATE messages
        SET text = :text,
            status = :status,
            updatedAt = :updatedAt
        WHERE id = :messageId
        """
    )
    suspend fun updateTextAndStatus(
        messageId: String,
        text: String,
        status: String,
        updatedAt: Long = System.currentTimeMillis()
    )

    @Query(
        """
        UPDATE messages
        SET status = :status,
            errorCode = :errorCode,
            errorMessage = :errorMessage,
            httpStatus = :httpStatus,
            requestId = :requestId,
            durationMs = :durationMs,
            updatedAt = :updatedAt
        WHERE id = :messageId
        """
    )
    suspend fun updateFailure(
        messageId: String,
        status: String = "FAILED",
        errorCode: String?,
        errorMessage: String?,
        httpStatus: Int?,
        requestId: String?,
        durationMs: Long?,
        updatedAt: Long = System.currentTimeMillis()
    )

    @Query(
        """
        SELECT * FROM messages
        WHERE conversationId = :conversationId
          AND messageType IN ('IMAGE_RESULT', 'IMAGE_EDIT_RESULT')
          AND status = 'COMPLETED'
        ORDER BY createdAt DESC
        LIMIT 1
        """
    )
    suspend fun getLatestImageMessage(
        conversationId: String
    ): MessageEntity?
}

@Dao
interface MessageImageDao {

    @Query(
        """
        SELECT * FROM message_images
        WHERE messageId = :messageId
        ORDER BY createdAt ASC
        """
    )
    fun observeForMessage(
        messageId: String
    ): Flow<List<MessageImageEntity>>

    @Query(
        """
        SELECT * FROM message_images
        WHERE messageId = :messageId
        ORDER BY createdAt ASC
        """
    )
    suspend fun getForMessage(
        messageId: String
    ): List<MessageImageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(
        image: MessageImageEntity
    )

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(
        images: List<MessageImageEntity>
    )

    @Delete
    suspend fun delete(
        image: MessageImageEntity
    )

    @Query(
        """
        DELETE FROM message_images
        WHERE id = :imageId
        """
    )
    suspend fun deleteById(
        imageId: String
    )
}

@Dao
interface ApiProfileDao {

    @Query(
        """
        SELECT * FROM api_profiles
        ORDER BY isActive DESC, name ASC
        """
    )
    fun observeAll(): Flow<List<ApiProfileEntity>>

    @Query(
        """
        SELECT * FROM api_profiles
        ORDER BY isActive DESC, name ASC
        """
    )
    suspend fun getAll(): List<ApiProfileEntity>

    @Query(
        """
        SELECT * FROM api_profiles
        WHERE id = :profileId
        LIMIT 1
        """
    )
    suspend fun getById(
        profileId: String
    ): ApiProfileEntity?

    @Query(
        """
        SELECT * FROM api_profiles
        WHERE isActive = 1
        LIMIT 1
        """
    )
    suspend fun getActive(): ApiProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(
        profile: ApiProfileEntity
    )

    @Delete
    suspend fun delete(
        profile: ApiProfileEntity
    )

    @Transaction
    suspend fun setActive(
        profileId: String
    ) {
        clearActive()
        activate(profileId)
    }

    @Query(
        """
        UPDATE api_profiles
        SET isActive = 0
        """
    )
    suspend fun clearActive()

    @Query(
        """
        UPDATE api_profiles
        SET isActive = 1,
            updatedAt = :updatedAt
        WHERE id = :profileId
        """
    )
    suspend fun activate(
        profileId: String,
        updatedAt: Long = System.currentTimeMillis()
    )
}

@Dao
interface SearchProfileDao {

    @Query(
        """
        SELECT * FROM search_profiles
        ORDER BY isActive DESC, name ASC
        """
    )
    fun observeAll(): Flow<List<SearchProfileEntity>>

    @Query(
        """
        SELECT * FROM search_profiles
        ORDER BY isActive DESC, name ASC
        """
    )
    suspend fun getAll(): List<SearchProfileEntity>

    @Query(
        """
        SELECT * FROM search_profiles
        WHERE id = :profileId
        LIMIT 1
        """
    )
    suspend fun getById(
        profileId: String
    ): SearchProfileEntity?

    @Query(
        """
        SELECT * FROM search_profiles
        WHERE isActive = 1
        LIMIT 1
        """
    )
    suspend fun getActive(): SearchProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(
        profile: SearchProfileEntity
    )

    @Delete
    suspend fun delete(
        profile: SearchProfileEntity
    )

    @Transaction
    suspend fun setActive(
        profileId: String
    ) {
        clearActive()
        activate(profileId)
    }

    @Query(
        """
        UPDATE search_profiles
        SET isActive = 0
        """
    )
    suspend fun clearActive()

    @Query(
        """
        UPDATE search_profiles
        SET isActive = 1,
            updatedAt = :updatedAt
        WHERE id = :profileId
        """
    )
    suspend fun activate(
        profileId: String,
        updatedAt: Long = System.currentTimeMillis()
    )
}
