package com.example.texteditor.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One row per snapshot (version) of a file.
 */
@Entity(
    tableName = "file_versions",
    indices = [
        Index(value = ["fileId"]),
        // Guarantees the version-number chain can never contain duplicates for a file, even if
        // two "create snapshot" calls ever raced each other - the second insert throws instead
        // of silently corrupting rebuildText()'s "versions[i] == version (i+1)" assumption.
        Index(value = ["fileId", "versionNumber"], unique = true)
    ]
)
data class FileVersion(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fileId: Long,
    val versionNumber: Int,
    val label: String,
    val createdAt: Long,
    val baseContent: String?,
    val patchText: String?
)
