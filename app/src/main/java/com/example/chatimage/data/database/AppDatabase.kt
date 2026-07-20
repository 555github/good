package com.example.chatimage.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        ConversationEntity::class,
        MessageEntity::class,
        MessageImageEntity::class,
        ApiProfileEntity::class,
        SearchProfileEntity::class
    ],
    version = 3,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun conversationDao(): ConversationDao

    abstract fun messageDao(): MessageDao

    abstract fun messageImageDao(): MessageImageDao

    abstract fun apiProfileDao(): ApiProfileDao

    abstract fun searchProfileDao(): SearchProfileDao

    companion object {

        @Volatile
        private var instance: AppDatabase? = null

        fun create(
            context: Context
        ): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "chatimage_v3.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                    .also {
                        instance = it
                    }
            }
        }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE messages ADD COLUMN inputTokens INTEGER")
                database.execSQL("ALTER TABLE messages ADD COLUMN outputTokens INTEGER")
                database.execSQL("ALTER TABLE messages ADD COLUMN totalTokens INTEGER")
                database.execSQL("ALTER TABLE messages ADD COLUMN cachedInputTokens INTEGER")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE messages ADD COLUMN attachedFilePath TEXT")
                database.execSQL("ALTER TABLE messages ADD COLUMN attachedFileName TEXT")
                database.execSQL("ALTER TABLE messages ADD COLUMN attachedFileMimeType TEXT")
            }
        }
    }
}
