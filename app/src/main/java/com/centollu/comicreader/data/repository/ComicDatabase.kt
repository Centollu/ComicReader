package com.centollu.comicreader.data.repository

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.centollu.comicreader.data.model.ComicDocument
import com.centollu.comicreader.data.model.ReadingHistoryDocument

@Database(
    entities = [ComicDocument::class, ReadingHistoryDocument::class],
    version = 2,
    exportSchema = false
)
abstract class ComicDatabase : RoomDatabase() {

    abstract fun comicDao(): ComicDao

    companion object {
        @Volatile
        private var instance: ComicDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE comics ADD COLUMN issueNumber INTEGER")
            }
        }

        fun getInstance(context: Context): ComicDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    ComicDatabase::class.java,
                    "comic_library.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { instance = it }
            }
        }
    }
}
