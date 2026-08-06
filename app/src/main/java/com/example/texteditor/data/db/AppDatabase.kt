package com.example.texteditor.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * The Room (SQLite) database that stores the version-control information.
 * We use a singleton so the whole app shares one database connection.
 */
@Database(
    entities = [TrackedFile::class, FileVersion::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun versionDao(): VersionDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "text_editor.db"
                ).build().also { instance = it }
            }
        }
    }
}
