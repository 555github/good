package com.example.chatimage.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        ConversationEntity::class,
        MessageEntity::class,
        MessageImageEntity::class,
        ApiProfileEntity::class,
        SearchProfileEntity::class
    ],
    version = 1,
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
                    .fallbackToDestructiveMigration()
                    .build()
                    .also {
                        instance = it
                    }
            }
        }
    }
}
