package com.centollu.comicreader.data.repository

import android.content.Context
import com.centollu.comicreader.data.model.ComicDocument
import com.centollu.comicreader.data.model.ReadingHistoryDocument
import kotlinx.coroutines.flow.Flow

class ComicRepository(context: Context) {

    private val dao = ComicDatabase.getInstance(context).comicDao()

    // --- LIBRARY COMICS ---

    fun getAllComicsFlow(): Flow<List<ComicDocument>> = dao.getAllComicsFlow()

    suspend fun getComicById(id: String): ComicDocument? = dao.getComicById(id)

    suspend fun findComicByFilePath(filePath: String): ComicDocument? =
        dao.findComicByFilePath(filePath)

    suspend fun insertOrUpdateComic(comic: ComicDocument) = dao.insertOrUpdateComic(comic)

    suspend fun updateCoverFilename(comicId: String, newCoverFilename: String) {
        dao.updateCoverFilename(comicId, newCoverFilename)
    }

    suspend fun updateComicMetadata(
        comicId: String,
        title: String,
        issueNumber: Int?,
        series: String,
        authors: String,
        publisher: String,
        storyArc: String
    ) {
        dao.updateComicMetadata(comicId, title, issueNumber, series, authors, publisher, storyArc)
    }

    suspend fun deleteComic(comicId: String) = dao.deleteComic(comicId)

    // --- HISTORY ---

    fun getReadingHistoryFlow(): Flow<List<ReadingHistoryDocument>> = dao.getReadingHistoryFlow()

    suspend fun saveReadingProgress(
        comicId: String,
        filePath: String,
        title: String,
        coverPath: String,
        pageIndex: Int,
        totalPages: Int
    ) {
        val existing = dao.getHistoryForComic(comicId)
        if (existing != null) {
            existing.lastPageOpened = pageIndex
            existing.totalPages = totalPages
            existing.lastReadTimestamp = System.currentTimeMillis()
            existing.isCompleted = pageIndex >= totalPages - 1
            dao.updateHistory(existing)
        } else {
            val newHistory = ReadingHistoryDocument().apply {
                this.comicId = comicId
                this.filePath = filePath
                this.title = title
                this.coverPath = coverPath
                this.lastPageOpened = pageIndex
                this.totalPages = totalPages
                this.lastReadTimestamp = System.currentTimeMillis()
                this.isCompleted = pageIndex >= totalPages - 1
            }
            dao.insertHistory(newHistory)
        }
    }

    suspend fun getHistoryForComic(comicId: String): ReadingHistoryDocument? =
        dao.getHistoryForComic(comicId)

    suspend fun deleteHistoryItem(id: String) = dao.deleteHistory(id)

    suspend fun clearHistory() = dao.clearHistory()
}
