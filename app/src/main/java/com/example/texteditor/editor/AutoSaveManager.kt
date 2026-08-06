package com.example.texteditor.editor

import android.content.Context
import android.os.Handler
import android.os.Looper
import java.io.File

/**
 * Crash-recovery mechanism ("local history") required by the assignment.
 *
 * Every 10 seconds, the current editor text is copied into a temporary
 * backup file in the app's cache directory. If the app is closed normally,
 * the backup is deleted. If the app CRASHES (or is killed by the system),
 * the backup survives - and on the next launch MainActivity offers to
 * restore the unsaved text from it.
 */
class AutoSaveManager(
    context: Context,
    /** Asks MainActivity for the current state: (file name or null, editor text). */
    private val currentState: () -> Pair<String?, String>
) {

    /** What was recovered from the backup file. */
    data class Backup(val fileName: String?, val text: String)

    private val backupFile = File(context.cacheDir, "autosave_buffer.txt")
    private val prefs = context.getSharedPreferences("autosave", Context.MODE_PRIVATE)
    private val handler = Handler(Looper.getMainLooper())

    // A Runnable that saves the buffer and then re-schedules itself.
    private val periodicSave = object : Runnable {
        override fun run() {
            saveNow()
            handler.postDelayed(this, INTERVAL_MS)
        }
    }

    /** Starts the 10 second timer (called from onResume). */
    fun start() {
        handler.postDelayed(periodicSave, INTERVAL_MS)
    }

    /** Stops the timer (called from onPause). */
    fun stop() {
        handler.removeCallbacks(periodicSave)
    }

    /** Writes the current editor buffer into the temporary backup file. */
    fun saveNow() {
        val (fileName, text) = currentState()
        backupFile.writeText(text)
        prefs.edit()
            .putString(KEY_FILE_NAME, fileName)
            .putBoolean(KEY_HAS_BACKUP, true)
            .apply()
    }

    fun hasBackup(): Boolean = prefs.getBoolean(KEY_HAS_BACKUP, false) && backupFile.exists()

    fun readBackup(): Backup? {
        if (!hasBackup()) return null
        return Backup(
            fileName = prefs.getString(KEY_FILE_NAME, null),
            text = backupFile.readText()
        )
    }

    /** Removes the backup - called after a clean exit or after the user restored/discarded it. */
    fun clearBackup() {
        backupFile.delete()
        prefs.edit().putBoolean(KEY_HAS_BACKUP, false).remove(KEY_FILE_NAME).apply()
    }

    companion object {
        private const val INTERVAL_MS = 10_000L // 10 seconds, as required by the spec
        private const val KEY_HAS_BACKUP = "has_backup"
        private const val KEY_FILE_NAME = "file_name"
    }
}
