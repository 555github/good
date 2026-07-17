package com.example.chatimage

import android.app.Application
import com.example.chatimage.data.database.AppDatabase

class ChatImageApplication : Application() {

    val database: AppDatabase by lazy {
        AppDatabase.create(this)
    }

    override fun onCreate() {
        super.onCreate()

        createAppDirectories()
    }

    private fun createAppDirectories() {
        listOf(
            "generated",
            "attachments",
            "exports"
        ).forEach { directoryName ->
            filesDir
                .resolve(directoryName)
                .mkdirs()
        }
    }
}
