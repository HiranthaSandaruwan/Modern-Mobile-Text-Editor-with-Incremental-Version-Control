package com.example.texteditor.data

import android.content.Context
import androidx.core.content.edit

// Remembers the names of recently opened files.
class RecentFilesStore(context: Context) {

    private val prefs = context.getSharedPreferences("recent_files", Context.MODE_PRIVATE)

    fun getAll(): List<String> {
        val raw = prefs.getString(KEY, "") ?: ""
        return if (raw.isEmpty()) emptyList() else raw.split("\n")
    }

    fun add(name: String) {
        val updated = (listOf(name) + getAll().filter { it != name }).take(MAX_ENTRIES)
        prefs.edit { putString(KEY, updated.joinToString("\n")) }
    }

    companion object {
        private const val KEY = "list"
        private const val MAX_ENTRIES = 10
    }
}
