package com.example.texteditor.editor

import android.content.Context
import android.os.Handler
import android.os.Looper
import java.io.File

// Saves a copy of the current text every 10 seconds, so it can be recovered after a crash.
class AutoSaveManager(
    context: Context,
    private val currentState: () -> Pair<String?, String>
) {
    data class Backup(val fileName: String?, val text: String)

    private val backupFile = File(context.cacheDir, "autosave_buffer.txt")
    private val prefs = context.getSharedPreferences("autosave", Context.MODE_PRIVATE)
    private val handler = Handler(Looper.getMainLooper())

    private val periodicSave = object : Runnable {
        override fun run() {
            saveNow()
            handler.postDelayed(this, INTERVAL_MS)
        }
    }

    fun start() = handler.postDelayed(periodicSave, INTERVAL_MS)
    fun stop() = handler.removeCallbacks(periodicSave)

    fun saveNow() {
        val (fileName, text) = currentState()
        if (fileName == null && text.isEmpty()) return
        backupFile.writeText(text)
        prefs.edit().putString("file", fileName).putBoolean("has", true).apply()
    }

    fun hasBackup(): Boolean = prefs.getBoolean("has", false) && backupFile.exists()

    fun readBackup() = if (hasBackup()) Backup(prefs.getString("file", null), backupFile.readText()) else null

    fun clearBackup() {
        backupFile.delete()
        prefs.edit().putBoolean("has", false).remove("file").apply()
    }

    companion object {
        private const val INTERVAL_MS = 10_000L
    }
}
