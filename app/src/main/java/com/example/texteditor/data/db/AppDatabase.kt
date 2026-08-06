package com.example.texteditor.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [TrackedFile::class, FileVersion::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun versionDao(): VersionDao

    companion object {
        @Volatile private var instance: AppDatabase? = null
        fun get(context: Context) = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "text_editor.db")
                .addMigrations(MIGRATION_1_2)
                .build().also { instance = it }
        }

        /**
         * Adds a unique index on (fileId, versionNumber) so the version-number chain can never
         * contain duplicates. If a pre-existing install somehow already has duplicate rows
         * (from the race-condition bug this migration fixes), the oldest copy of each duplicate
         * pair is kept and the rest are removed before the unique index is created.
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "DELETE FROM file_versions WHERE id NOT IN " +
                        "(SELECT MIN(id) FROM file_versions GROUP BY fileId, versionNumber)"
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_file_versions_fileId_versionNumber " +
                        "ON file_versions(fileId, versionNumber)"
                )
            }
        }
    }
}
