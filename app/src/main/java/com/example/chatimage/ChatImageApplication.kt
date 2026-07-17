package com.example.chatimage

import android.app.Application
import java.io.File

class ChatImageApplication : Application() {

    val container: AppContainer by lazy {
        AppContainer(this)
    }

    override fun onCreate() {
        super.onCreate()

        createAppDirectories()

        /*
         * 主动访问 container，使 Room、DataStore 和
         * 加密存储在 App 启动后完成初始化。
         */
        container
    }

    private fun createAppDirectories() {
        listOf(
            "generated",
            "attachments",
            "exports"
        ).forEach { directoryName ->
            File(
                filesDir,
                directoryName
            ).mkdirs()
        }
    }
}
