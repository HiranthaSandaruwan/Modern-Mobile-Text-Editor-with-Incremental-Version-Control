package com.example.texteditor.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

/**
 * DAO = Data Access Object. Room generates the SQL implementation of this
 * interface at compile time. All functions are "suspend", so they must be
 * called from a coroutine and never block the UI thread.
 */
@Dao
interface VersionDao {

    // ---- tracked_files table ----

    @Query("SELECT * FROM tracked_files WHERE name = :name LIMIT 1")
    suspend fun getFileByName(name: String): TrackedFile?

    @Insert
    suspend fun insertFile(file: TrackedFile): Long

    @Query("UPDATE tracked_files SET isReadOnly = :readOnly WHERE id = :fileId")
    suspend fun setReadOnly(fileId: Long, readOnly: Boolean)

    @Query("UPDATE tracked_files SET encoding = :encoding WHERE id = :fileId")
    suspend fun setEncoding(fileId: Long, encoding: String)

    // ---- file_versions table ----

    @Query("SELECT * FROM file_versions WHERE fileId = :fileId ORDER BY versionNumber ASC")
    suspend fun getVersionsForFile(fileId: Long): List<FileVersion>

    @Insert
    suspend fun insertVersion(version: FileVersion): Long
}
