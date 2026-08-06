package com.example.texteditor.data

import android.content.Context
import java.io.File
import java.nio.charset.Charset

/**
 * Handles all reading and writing of text files.
 */
class FileRepository(context: Context) {

    private val documentsDir: File = File(context.filesDir, "documents").apply { mkdirs() }
    private val prefs = context.getSharedPreferences("file_repository", Context.MODE_PRIVATE)

    fun listFileNames(): List<String> {
        return documentsDir.listFiles()
            ?.filter { it.isFile }
            ?.map { it.name }
            ?.sorted()
            ?: emptyList()
    }

    fun exists(name: String): Boolean = File(documentsDir, name).isFile

    fun readText(name: String, charset: Charset = Charsets.UTF_8): String {
        return File(documentsDir, name).readText(charset)
    }

    fun saveText(name: String, text: String, charset: Charset = Charsets.UTF_8) {
        File(documentsDir, name).writeText(text, charset)
    }

    fun ensureSampleFiles(): Boolean {
        if (prefs.getBoolean("samples_created", false)) return false
        saveText("Welcome.md", SAMPLE_MARKDOWN)
        saveText("HelloWorld.kt", SAMPLE_KOTLIN)
        prefs.edit().putBoolean("samples_created", true).apply()
        return true
    }

    companion object {
        private val SAMPLE_MARKDOWN = """
            # Welcome to Kotlin Text Editor

            This is a **sample Markdown file**.
        """.trimIndent()

        private val SAMPLE_KOTLIN = """
            package demo
            fun main() { println("Hello World") }
        """.trimIndent()
    }
}
