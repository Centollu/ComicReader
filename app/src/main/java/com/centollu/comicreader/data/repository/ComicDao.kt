package com.centollu.comicreader.data.repository

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.centollu.comicreader.data.model.ComicDocument
import com.centollu.comicreader.data.model.ReadingHistoryDocument
import kotlinx.coroutines.flow.Flow

@Dao
interface ComicDao {

    @Query("SELECT * FROM comics")
    fun getAllComicsFlow(): Flow<List<ComicDocument>>

    @Query("SELECT * FROM comics WHERE _id = :id")
    suspend fun getComicById(id: String): ComicDocument?

    @Query("SELECT * FROM comics WHERE filePath = :filePath")
    suspend fun findComicByFilePath(filePath: String): ComicDocument?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateComic(comic: ComicDocument)

    @Query("UPDATE comics SET coverFilename = :coverFilename WHERE _id = :id")
    suspend fun updateCoverFilename(id: String, coverFilename: String)

    @Query("UPDATE comics SET title = :title, issueNumber = :issueNumber, series = :series, authors = :authors, publisher = :publisher, storyArc = :storyArc WHERE _id = :id")
    suspend fun updateComicMetadata(
        id: String,
        title: String,
        issueNumber: Int?,
        series: String,
        authors: String,
        publisher: String,
        storyArc: String
    )

    @Query("DELETE FROM comics WHERE _id = :id")
    suspend fun deleteComic(id: String)

    @Query("SELECT * FROM reading_history ORDER BY lastReadTimestamp DESC")
    fun getReadingHistoryFlow(): Flow<List<ReadingHistoryDocument>>

    @Query("SELECT * FROM reading_history WHERE comicId = :comicId")
    suspend fun getHistoryForComic(comicId: String): ReadingHistoryDocument?

    @Query("DELETE FROM reading_history WHERE _id = :id")
    suspend fun deleteHistory(id: String)

    @Query("DELETE FROM reading_history")
    suspend fun clearHistory()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(history: ReadingHistoryDocument)

    @Update
    suspend fun updateHistory(history: ReadingHistoryDocument)
}
