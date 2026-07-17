package com.example.chatimage.util

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import com.example.chatimage.data.api.SavedImageResult
import java.io.File
import java.util.UUID

object ImageFileUtils {

    fun copyAttachment(
        context: Context,
        uri: Uri
    ): File {
        val mimeType = context
            .contentResolver
            .getType(uri)
            .orEmpty()

        val extension = extensionForMimeType(
            mimeType
        )

        val directory = File(
            context.filesDir,
            "attachments"
        ).apply {
            mkdirs()
        }

        val destination = File(
            directory,
            "attachment_${System.currentTimeMillis()}_" +
                "${UUID.randomUUID()}.$extension"
        )

        val input = context
            .contentResolver
            .openInputStream(uri)
            ?: throw IllegalStateException(
                "无法读取选择的图片"
            )

        input.use { source ->
            destination.outputStream().use {
                output ->
                source.copyTo(output)
            }
        }

        if (destination.length() <= 0) {
            destination.delete()

            throw IllegalStateException(
                "选择的图片内容为空"
            )
        }

        return destination
    }

    fun saveBase64Image(
        context: Context,
        rawValue: String
    ): SavedImageResult {
        val dataHeader = rawValue
            .takeIf {
                it.startsWith(
                    "data:",
                    ignoreCase = true
                )
            }
            ?.substringBefore(",")

        val mimeType = dataHeader
            ?.substringAfter("data:")
            ?.substringBefore(";")
            ?.takeIf {
                it.startsWith("image/")
            }
            ?: "image/png"

        val cleanBase64 = rawValue
            .substringAfter(
                "base64,",
                rawValue
            )
            .replace(
                Regex("""\s+"""),
                ""
            )

        val bytes = Base64.decode(
            cleanBase64,
            Base64.DEFAULT
        )

        return saveImageBytes(
            context = context,
            bytes = bytes,
            mimeType = mimeType
        )
    }

    fun saveImageBytes(
        context: Context,
        bytes: ByteArray,
        mimeType: String
    ): SavedImageResult {
        if (bytes.isEmpty()) {
            throw IllegalStateException(
                "图片数据为空"
            )
        }

        val normalizedMimeType =
            normalizeImageMimeType(
                mimeType,
                bytes
            )

        val dimensions = readDimensions(
            bytes
        )

        if (
            dimensions.first == null ||
            dimensions.second == null
        ) {
            throw IllegalStateException(
                "服务器返回的数据不是可识别的图片"
            )
        }

        val extension = extensionForMimeType(
            normalizedMimeType
        )

        val directory = File(
            context.filesDir,
            "generated"
        ).apply {
            mkdirs()
        }

        val file = File(
            directory,
            "image_${System.currentTimeMillis()}_" +
                "${UUID.randomUUID()}.$extension"
        )

        file.writeBytes(bytes)

        return SavedImageResult(
            localPath = file.absolutePath,
            mimeType = normalizedMimeType,
            fileSize = file.length(),
            width = dimensions.first,
            height = dimensions.second
        )
    }

    fun fileToBase64(
        file: File,
        includeDataUrlPrefix: Boolean
    ): String {
        if (!file.exists()) {
            throw IllegalStateException(
                "源图片文件不存在"
            )
        }

        val bytes = file.readBytes()

        val encoded = Base64.encodeToString(
            bytes,
            Base64.NO_WRAP
        )

        if (!includeDataUrlPrefix) {
            return encoded
        }

        return "data:${mimeTypeForFile(file)};" +
            "base64,$encoded"
    }

    fun mimeTypeForFile(
        file: File
    ): String {
        return when (
            file.extension.lowercase()
        ) {
            "jpg", "jpeg" -> "image/jpeg"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            else -> "image/png"
        }
    }

    fun isLikelyImageContentType(
        contentType: String
    ): Boolean {
        return contentType
            .substringBefore(";")
            .trim()
            .startsWith(
                "image/",
                ignoreCase = true
            )
    }

    private fun normalizeImageMimeType(
        declaredMimeType: String,
        bytes: ByteArray
    ): String {
        if (
            isLikelyImageContentType(
                declaredMimeType
            )
        ) {
            return declaredMimeType
                .substringBefore(";")
                .trim()
                .lowercase()
        }

        return detectMimeType(bytes)
            ?: "image/png"
    }

    private fun detectMimeType(
        bytes: ByteArray
    ): String? {
        if (
            bytes.size >= 8 &&
            bytes[0] == 0x89.toByte() &&
            bytes[1] == 0x50.toByte() &&
            bytes[2] == 0x4E.toByte() &&
            bytes[3] == 0x47.toByte()
        ) {
            return "image/png"
        }

        if (
            bytes.size >= 3 &&
            bytes[0] == 0xFF.toByte() &&
            bytes[1] == 0xD8.toByte() &&
            bytes[2] == 0xFF.toByte()
        ) {
            return "image/jpeg"
        }

        if (
            bytes.size >= 12 &&
            String(
                bytes,
                0,
                4,
                Charsets.US_ASCII
            ) == "RIFF" &&
            String(
                bytes,
                8,
                4,
                Charsets.US_ASCII
            ) == "WEBP"
        ) {
            return "image/webp"
        }

        return null
    }

    private fun readDimensions(
        bytes: ByteArray
    ): Pair<Int?, Int?> {
        val options =
            BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }

        BitmapFactory.decodeByteArray(
            bytes,
            0,
            bytes.size,
            options
        )

        val width = options.outWidth
            .takeIf {
                it > 0
            }

        val height = options.outHeight
            .takeIf {
                it > 0
            }

        return width to height
    }

    private fun extensionForMimeType(
        mimeType: String
    ): String {
        return when {
            mimeType.contains(
                "jpeg",
                ignoreCase = true
            ) -> "jpg"

            mimeType.contains(
                "webp",
                ignoreCase = true
            ) -> "webp"

            mimeType.contains(
                "gif",
                ignoreCase = true
            ) -> "gif"

            else -> "png"
        }
    }
}
