package com.example.chatimage.util

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import java.util.zip.ZipInputStream

data class LocalFileAttachment(
    val path: String,
    val name: String,
    val mimeType: String,
    val size: Long
)

sealed interface PreparedFileAttachment {
    data class TextContext(val text: String) : PreparedFileAttachment
    data class BinaryFile(
        val fileName: String,
        val dataUrl: String
    ) : PreparedFileAttachment
}

object FileAttachmentUtils {
    fun copyAttachment(context: Context, uri: Uri): LocalFileAttachment {
        val resolver = context.contentResolver
        val displayName = resolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }?.takeIf(String::isNotBlank) ?: "file_${System.currentTimeMillis()}"
        val safeName = displayName.replace(Regex("[^A-Za-z0-9._()\u4e00-\u9fff-]"), "_")
        val mimeType = resolver.getType(uri).orEmpty().ifBlank {
            mimeTypeFromName(safeName)
        }
        val directory = File(context.filesDir, "attachments").apply { mkdirs() }
        val destination = File(directory, "file_${UUID.randomUUID()}_$safeName")

        resolver.openInputStream(uri)?.use { input ->
            destination.outputStream().use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var total = 0L
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    require(total <= MAX_FILE_BYTES) {
                        "文件超过 25 MB 上限"
                    }
                    output.write(buffer, 0, count)
                }
            }
        } ?: throw IllegalStateException("无法读取选择的文件")

        require(destination.length() > 0) { "选择的文件为空" }
        return LocalFileAttachment(
            path = destination.absolutePath,
            name = safeName,
            mimeType = mimeType,
            size = destination.length()
        )
    }

    fun prepare(
        file: File,
        fileName: String,
        mimeType: String,
        allowBinaryInput: Boolean
    ): PreparedFileAttachment {
        require(file.exists()) { "附件文件已不存在" }
        val extension = fileName.substringAfterLast('.', "").lowercase()

        if (extension == "zip") {
            return PreparedFileAttachment.TextContext(readZipContext(file, fileName))
        }
        if (extension in TEXT_EXTENSIONS || mimeType.startsWith("text/")) {
            return PreparedFileAttachment.TextContext(
                "<attached_file name=\"$fileName\">\n" +
                    readTextLimited(file) +
                    "\n</attached_file>"
            )
        }
        if (!allowBinaryInput) {
            throw IllegalStateException(
                "当前 /chat/completions 线路不能直接读取 $fileName；请改用 /responses，或上传文本/代码/ZIP 文件"
            )
        }

        val encoded = Base64.encodeToString(file.readBytes(), Base64.NO_WRAP)
        return PreparedFileAttachment.BinaryFile(
            fileName = fileName,
            dataUrl = "data:${mimeType.ifBlank { mimeTypeFromName(fileName) }};base64,$encoded"
        )
    }

    private fun readTextLimited(file: File): String {
        val bytes = file.inputStream().use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (output.size() < MAX_TEXT_BYTES) {
                val count = input.read(
                    buffer,
                    0,
                    minOf(buffer.size, MAX_TEXT_BYTES - output.size())
                )
                if (count < 0) break
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        }
        return bytes.toString(Charsets.UTF_8).take(MAX_CONTEXT_CHARS)
    }

    private fun readZipContext(file: File, fileName: String): String {
        val sections = mutableListOf<String>()
        val names = mutableListOf<String>()
        var entryCount = 0
        var uncompressedBytes = 0L
        var contextChars = 0

        ZipInputStream(file.inputStream().buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                entryCount++
                require(entryCount <= MAX_ZIP_ENTRIES) {
                    "ZIP 条目超过 $MAX_ZIP_ENTRIES 个，已停止读取"
                }
                val safeEntryName = entry.name.replace('\\', '/')
                names += safeEntryName
                if (!entry.isDirectory) {
                    val extension = safeEntryName.substringAfterLast('.', "").lowercase()
                    val output = ByteArrayOutputStream()
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val count = zip.read(buffer)
                        if (count < 0) break
                        uncompressedBytes += count
                        require(uncompressedBytes <= MAX_ZIP_UNCOMPRESSED_BYTES) {
                            "ZIP 展开内容超过 10 MB，已停止读取"
                        }
                        if (
                            extension in TEXT_EXTENSIONS &&
                            output.size() < MAX_ZIP_ENTRY_TEXT_BYTES
                        ) {
                            output.write(
                                buffer,
                                0,
                                minOf(count, MAX_ZIP_ENTRY_TEXT_BYTES - output.size())
                            )
                        }
                    }
                    if (output.size() > 0 && contextChars < MAX_CONTEXT_CHARS) {
                        val text = output.toByteArray()
                            .toString(Charsets.UTF_8)
                            .take(MAX_CONTEXT_CHARS - contextChars)
                        sections += "--- $safeEntryName ---\n$text"
                        contextChars += text.length
                    }
                }
                zip.closeEntry()
            }
        }

        return buildString {
            appendLine("<attached_zip name=\"$fileName\">")
            appendLine("文件清单：")
            names.forEach { appendLine("- $it") }
            if (sections.isNotEmpty()) {
                appendLine()
                appendLine("可读取的文本内容：")
                sections.forEach { appendLine(it) }
            }
            append("</attached_zip>")
        }.take(MAX_CONTEXT_CHARS)
    }

    private fun mimeTypeFromName(name: String): String = when (
        name.substringAfterLast('.', "").lowercase()
    ) {
        "pdf" -> "application/pdf"
        "json" -> "application/json"
        "zip" -> "application/zip"
        "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
        else -> "application/octet-stream"
    }

    private val TEXT_EXTENSIONS = setOf(
        "txt", "md", "csv", "json", "jsonl", "xml", "yaml", "yml",
        "kt", "kts", "java", "js", "jsx", "ts", "tsx", "py", "rb",
        "go", "rs", "c", "h", "cpp", "hpp", "cs", "swift", "dart",
        "html", "htm", "css", "scss", "sql", "sh", "ps1", "bat",
        "toml", "ini", "properties", "gradle", "log"
    )
    private const val MAX_FILE_BYTES = 25L * 1024L * 1024L
    private const val MAX_TEXT_BYTES = 512 * 1024
    private const val MAX_CONTEXT_CHARS = 120_000
    private const val MAX_ZIP_ENTRIES = 200
    private const val MAX_ZIP_UNCOMPRESSED_BYTES = 10L * 1024L * 1024L
    private const val MAX_ZIP_ENTRY_TEXT_BYTES = 128 * 1024
}
