package com.example.texteditor.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// One row per saved snapshot of a file.
@Entity(
    tableName = "file_versions",
    indices = [
        Index(value = ["fileId"]),
        // Stops a file from ever getting two versions with the same number.
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
