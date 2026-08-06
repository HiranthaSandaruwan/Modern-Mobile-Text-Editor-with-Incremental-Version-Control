package com.example.texteditor.data

import android.content.Context
import java.io.File
import java.nio.charset.Charset

/**
 * Handles all reading and writing of text files.
 *
 * Every document is stored in the app's PRIVATE internal storage, inside a
 * folder called "documents" (e.g. /data/data/com.example.texteditor/files/documents).
 * Using internal storage means we do NOT need any storage permissions.
 */
class FileRepository(context: Context) {

    private val documentsDir: File = File(context.filesDir, "documents").apply { mkdirs() }

    private val prefs = context.getSharedPreferences("file_repository", Context.MODE_PRIVATE)

    /** Returns the names of all saved documents, alphabetically sorted. */
    fun listFileNames(): List<String> {
        return documentsDir.listFiles()
            ?.filter { it.isFile }
            ?.map { it.name }
            ?.sorted()
            ?: emptyList()
    }

    fun exists(name: String): Boolean = File(documentsDir, name).isFile

    /** Reads the whole file as text using the given character encoding. */
    fun readText(name: String, charset: Charset = Charsets.UTF_8): String {
        return File(documentsDir, name).readText(charset)
    }

    /** Writes (or overwrites) the file with the given text and encoding. */
    fun saveText(name: String, text: String, charset: Charset = Charsets.UTF_8) {
        File(documentsDir, name).writeText(text, charset)
    }

    /**
     * On the very first launch we create two small demo files, so the app
     * can be demonstrated immediately. Returns true only on that first run.
     */
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

            This is a **sample Markdown file** so you can try the app immediately.

            ## Things to try
            - Toggle *Markdown preview* from the menu
            - Edit this text, then use **Undo / Redo**
            - Save the file, then create a *snapshot* from the menu
            - Open **Version history** to see the diff and restore old versions

            Inline `code` is highlighted, and so are [links](https://kotlinlang.org).

            > Tip: open HelloWorld.kt from the sidebar to see Kotlin highlighting.
        """.trimIndent()

        private val SAMPLE_KOTLIN = """
            package demo

            import kotlin.random.Random

            /** A small sample file to demonstrate Kotlin syntax highlighting. */
            @Suppress("unused")
            class Greeter(private val name: String) {

                // Single line comment
                fun greet(): String {
                    val luckyNumber = Random.nextInt(1, 100)
                    return "Hello, ${'$'}name! Your lucky number is ${'$'}luckyNumber"
                }
            }

            fun main() {
                val greeter = Greeter("IS2205")
                println(greeter.greet())
            }
        """.trimIndent()
    }
}
