package com.example.chatimage.util

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertTrue
import org.junit.Test

class FileAttachmentUtilsTest {
    @Test
    fun extractsTextAndFileListFromZip() {
        val file = File.createTempFile("attachment", ".zip")
        try {
            ZipOutputStream(file.outputStream()).use { zip ->
                zip.putNextEntry(ZipEntry("src/main.kt"))
                zip.write("fun main() = println(42)".toByteArray())
                zip.closeEntry()
                zip.putNextEntry(ZipEntry("assets/image.bin"))
                zip.write(byteArrayOf(1, 2, 3))
                zip.closeEntry()
            }

            val result = FileAttachmentUtils.prepare(
                file = file,
                fileName = "project.zip",
                mimeType = "application/zip",
                allowBinaryInput = false
            ) as PreparedFileAttachment.TextContext

            assertTrue(result.text.contains("src/main.kt"))
            assertTrue(result.text.contains("println(42)"))
            assertTrue(result.text.contains("assets/image.bin"))
        } finally {
            file.delete()
        }
    }
}
