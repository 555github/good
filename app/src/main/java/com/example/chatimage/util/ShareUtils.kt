package com.example.chatimage.util

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File

object ShareUtils {

    fun copyText(
        context: Context,
        text: String
    ) {
        val clipboard =
            context.getSystemService(
                Context.CLIPBOARD_SERVICE
            ) as android.content.ClipboardManager

        val clip =
            android.content.ClipData
                .newPlainText(
                    "ChatImage 文本",
                    text
                )

        clipboard.setPrimaryClip(clip)
    }

    fun shareText(
        context: Context,
        text: String
    ) {
        val intent = Intent(
            Intent.ACTION_SEND
        ).apply {
            type = "text/plain"

            putExtra(
                Intent.EXTRA_TEXT,
                text
            )
        }

        context.startActivity(
            Intent.createChooser(
                intent,
                "分享文本"
            )
        )
    }

    fun shareFile(
        context: Context,
        file: File,
        chooserTitle: String =
            "分享文件"
    ): Result<Unit> {
        return runCatching {
            require(file.exists()) {
                "文件不存在"
            }

            val uri =
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )

            val intent = Intent(
                Intent.ACTION_SEND
            ).apply {
                type = mimeTypeForFile(file)

                putExtra(
                    Intent.EXTRA_STREAM,
                    uri
                )

                addFlags(
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }

            context.startActivity(
                Intent.createChooser(
                    intent,
                    chooserTitle
                )
            )
        }
    }

    fun shareImage(
        context: Context,
        file: File
    ): Result<Unit> {
        return shareFile(
            context = context,
            file = file,
            chooserTitle = "分享图片"
        )
    }

    fun saveImageToGallery(
        context: Context,
        sourceFile: File
    ): Result<String> {
        return runCatching {
            require(sourceFile.exists()) {
                "图片文件不存在"
            }

            val mimeType =
                mimeTypeForFile(
                    sourceFile
                )

            val displayName =
                "ChatImage_" +
                    System.currentTimeMillis() +
                    "." +
                    sourceFile.extension
                        .ifBlank {
                            extensionForMimeType(
                                mimeType
                            )
                        }

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                val pictures = context.getExternalFilesDir(
                    Environment.DIRECTORY_PICTURES
                ) ?: context.filesDir
                val targetDirectory = File(pictures, "ChatImage").apply {
                    mkdirs()
                }
                val target = File(targetDirectory, displayName)
                sourceFile.copyTo(target, overwrite = true)
                MediaScannerConnection.scanFile(
                    context,
                    arrayOf(target.absolutePath),
                    arrayOf(mimeType),
                    null
                )
                return@runCatching target.absolutePath
            }

            val values =
                ContentValues().apply {
                    put(
                        MediaStore
                            .Images
                            .Media
                            .DISPLAY_NAME,
                        displayName
                    )

                    put(
                        MediaStore
                            .Images
                            .Media
                            .MIME_TYPE,
                        mimeType
                    )

                    put(
                        MediaStore
                            .Images
                            .Media
                            .RELATIVE_PATH,
                        "Pictures/ChatImage"
                    )

                    put(
                        MediaStore
                            .Images
                            .Media
                            .IS_PENDING,
                        1
                    )
                }

            val destinationUri =
                context.contentResolver
                    .insert(
                        MediaStore
                            .Images
                            .Media
                            .EXTERNAL_CONTENT_URI,
                        values
                    )
                    ?: throw
                        IllegalStateException(
                            "无法创建相册文件"
                        )

            try {
                context.contentResolver
                    .openOutputStream(
                        destinationUri
                    )
                    ?.use { output ->
                        sourceFile
                            .inputStream()
                            .use { input ->
                                input.copyTo(output)
                            }
                    }
                    ?: throw
                        IllegalStateException(
                            "无法写入相册文件"
                        )

                values.clear()

                values.put(
                    MediaStore
                        .Images
                        .Media
                        .IS_PENDING,
                    0
                )

                context.contentResolver
                    .update(
                        destinationUri,
                        values,
                        null,
                        null
                    )

                destinationUri.toString()
            } catch (exception: Exception) {
                context.contentResolver
                    .delete(
                        destinationUri,
                        null,
                        null
                    )

                throw exception
            }
        }
    }

    private fun mimeTypeForFile(
        file: File
    ): String {
        return when (
            file.extension.lowercase()
        ) {
            "jpg", "jpeg" ->
                "image/jpeg"

            "png" -> "image/png"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            "json" ->
                "application/json"

            "md", "markdown" ->
                "text/markdown"

            "txt" -> "text/plain"

            else ->
                "application/octet-stream"
        }
    }

    private fun extensionForMimeType(
        mimeType: String
    ): String {
        return when (mimeType) {
            "image/jpeg" -> "jpg"
            "image/webp" -> "webp"
            "image/gif" -> "gif"
            else -> "png"
        }
    }
}
