package com.example.texteditor.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One row per snapshot (version) of a file.
 *
 * How the "no duplication" rule from the assignment is fulfilled:
 *  - Version 1 stores the FULL text in [baseContent] ([patchText] is null).
 *  - Every later version stores ONLY a unified-diff patch in [patchText]
 *    ([baseContent] is null). The full text of version N is rebuilt by
 *    starting from version 1 and applying the patches of versions 2..N.
 */
@Entity(
    tableName = "file_versions",
    indices = [Index(value = ["fileId"])]
)
data class FileVersion(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fileId: Long,          // which TrackedFile this version belongs to
    val versionNumber: Int,    // 1, 2, 3, ... per file
    val label: String,         // name the user typed for this snapshot
    val createdAt: Long,       // timestamp in milliseconds
    val baseContent: String?,  // full text (only for version 1)
    val patchText: String?     // unified diff patch (versions 2 and later)
)
