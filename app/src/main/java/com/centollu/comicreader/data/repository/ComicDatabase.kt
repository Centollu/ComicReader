package com.centollu.comicreader.data.repository

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.centollu.comicreader.data.model.ComicDocument
import com.centollu.comicreader.data.model.ReadingHistoryDocument

@Database(
    entities = [ComicDocument::class, ReadingHistoryDocument::class],
    version = 1,
    exportSchema = false
)
abstract class ComicDatabase : RoomDatabase() {

    abstract fun comicDao(): ComicDao

    companion object {
        @Volatile
        private var instance: ComicDatabase? = null

        fun getInstance(context: Context): ComicDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    ComicDatabase::class.java,
                    "comic_library.db"
                ).build().also { instance = it }
            }
        }
    }
}
