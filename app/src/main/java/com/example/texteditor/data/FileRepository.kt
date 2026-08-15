package com.example.texteditor.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.nio.charset.Charset

// Reads and writes text files in the folder the user picked.
class FileRepository(private val context: Context) {

    private val prefs = context.getSharedPreferences("file_repository", Context.MODE_PRIVATE)

    // Where files were saved before folder picking existed. Old files here get moved over.
    private val legacyDocumentsDir = File(context.filesDir, "documents")

    var folderUri: Uri?
        get() = prefs.getString(KEY_FOLDER_URI, null)?.toUri()
        private set(value) = prefs.edit { putString(KEY_FOLDER_URI, value?.toString()) }

    // True once a folder is picked and we still have permission to use it.
    fun hasFolder(): Boolean {
        val uri = folderUri ?: return false
        return context.contentResolver.persistedUriPermissions.any {
            it.uri == uri && it.isReadPermission && it.isWritePermission
        }
    }

    // Called once, right after the user picks a folder.
    fun setFolder(uri: Uri) {
        context.contentResolver.takePersistableUriPermission(
            uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )
        folderUri = uri
        migrateLegacyFiles()
    }

    // Copies files from the old storage location into the newly picked folder.
    private fun migrateLegacyFiles() {
        val legacyFiles = legacyDocumentsDir.listFiles()?.filter { it.isFile } ?: return
        if (legacyFiles.isEmpty()) return
        val root = rootDir()
        for (file in legacyFiles) {
            if (root.findFile(file.name) == null) {
                val doc = root.createFile(MIME_TYPE, file.name)
                if (doc != null) {
                    context.contentResolver.openOutputStream(doc.uri, "wt")?.use { out ->
                        file.inputStream().use { it.copyTo(out) }
                    }
                }
            }
            file.delete()
        }
    }

    private fun rootDir(): DocumentFile {
        val uri = folderUri ?: throw IllegalStateException("No folder chosen yet")
        return DocumentFile.fromTreeUri(context, uri) ?: throw IOException("The chosen folder is no longer accessible")
    }

    fun listFileNames(): List<String> =
        rootDir().listFiles().filter { it.isFile }.mapNotNull { it.name }.sorted()

    // An intent that opens the device's file manager inside the chosen folder, or null if no folder is chosen.
    fun buildFolderViewIntent(): Intent? {
        val treeUri = folderUri ?: return null
        val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, DocumentsContract.getTreeDocumentId(treeUri))
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(docUri, DocumentsContract.Document.MIME_TYPE_DIR)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun exists(name: String): Boolean = rootDir().findFile(name) != null

    fun readText(name: String, charset: Charset = Charsets.UTF_8): String {
        val doc = findWithRetry(name) ?: throw FileNotFoundException(name)
        return context.contentResolver.openInputStream(doc.uri)?.use { it.readBytes().toString(charset) }
            ?: throw IOException("Could not read $name")
    }

    // A file just written can briefly not show up yet in a folder lookup (seen with the
    // MediaStore-backed Downloads folder), so give it a couple of short retries before failing.
    private fun findWithRetry(name: String): DocumentFile? {
        val root = rootDir()
        repeat(3) { attempt ->
            root.findFile(name)?.let { return it }
            if (attempt < 2) Thread.sleep(150)
        }
        return null
    }

    fun saveText(name: String, text: String, charset: Charset = Charsets.UTF_8) {
        val root = rootDir()
        val doc = root.findFile(name) ?: root.createFile(MIME_TYPE, name) ?: throw IOException("Could not create $name")
        context.contentResolver.openOutputStream(doc.uri, "wt")?.use { it.write(text.toByteArray(charset)) }
            ?: throw IOException("Could not write $name")
    }

    fun ensureSampleFiles(): Boolean {
        if (prefs.getBoolean("samples_created", false)) return false
        if (!exists("Welcome.md")) saveText("Welcome.md", SAMPLE_MARKDOWN)
        if (!exists("HelloWorld.kt")) saveText("HelloWorld.kt", SAMPLE_KOTLIN)
        prefs.edit { putBoolean("samples_created", true) }
        return true
    }

    companion object {
        private const val KEY_FOLDER_URI = "folder_uri"

        // Keeps file names like "notes.kt" unchanged when creating them.
        private const val MIME_TYPE = "application/octet-stream"

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
