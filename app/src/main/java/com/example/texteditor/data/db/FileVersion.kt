package com.example.texteditor.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One row per snapshot (version) of a file.
 */
@Entity(
    tableName = "file_versions",
    indices = [Index(value = ["fileId"])]
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
