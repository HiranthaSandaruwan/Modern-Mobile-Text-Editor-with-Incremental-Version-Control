package com.example.texteditor.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One row per document that the app knows about.
 * Stores per-file settings: the read-only lock and the text encoding.
 */
@Entity(
    tableName = "tracked_files",
    indices = [Index(value = ["name"], unique = true)]
)
data class TrackedFile(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val isReadOnly: Boolean = false,
    val encoding: String = "UTF-8"
)
