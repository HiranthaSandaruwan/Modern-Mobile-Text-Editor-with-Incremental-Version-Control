package com.example.texteditor.data

import android.content.Context

/**
 * Remembers the recently opened file names (most recent first).
 *
 * The list is stored in SharedPreferences as one string joined with
 * newline characters, which is safe because file names cannot contain '\n'.
 */
class RecentFilesStore(context: Context) {

    private val prefs = context.getSharedPreferences("recent_files", Context.MODE_PRIVATE)

    fun getAll(): List<String> {
        val raw = prefs.getString(KEY, "") ?: ""
        return if (raw.isEmpty()) emptyList() else raw.split("\n")
    }

    /** Adds a file to the top of the list (removing duplicates, max 10 entries). */
    fun add(name: String) {
        val updated = (listOf(name) + getAll().filter { it != name }).take(MAX_ENTRIES)
        prefs.edit().putString(KEY, updated.joinToString("\n")).apply()
    }

    companion object {
        private const val KEY = "list"
        private const val MAX_ENTRIES = 10
    }
}
