package com.centollu.comicreader.data.repository

import com.centollu.comicreader.data.model.ComicDocument
import com.centollu.comicreader.data.model.ReadingHistoryDocument
import io.realm.kotlin.Realm
import io.realm.kotlin.RealmConfiguration
import io.realm.kotlin.ext.query
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.mongodb.kbson.ObjectId

class MongoRepository {

    private val config = RealmConfiguration.Builder(
        schema = setOf(ComicDocument::class, ReadingHistoryDocument::class)
    ).name("comic_library.realm")
     .schemaVersion(1)
     .build()

    private val realm: Realm by lazy { Realm.open(config) }

    // --- LIBRAY COMICS ---

    fun getAllComicsFlow(): Flow<List<ComicDocument>> {
        return realm.query<ComicDocument>().asFlow().map { it.list }
    }

    suspend fun getComicById(id: String): ComicDocument? {
        return try {
            val objectId = ObjectId(id)
            realm.query<ComicDocument>("_id == $0", objectId).first().find()
        } catch (e: Exception) {
            null
        }
    }

    suspend fun findComicByFilePath(filePath: String): ComicDocument? {
        return realm.query<ComicDocument>("filePath == $0", filePath).first().find()
    }

    suspend fun insertOrUpdateComic(comic: ComicDocument): ComicDocument {
        return realm.write {
            copyToRealm(comic, io.realm.kotlin.UpdatePolicy.ALL)
        }
    }

    suspend fun updateCoverFilename(comicId: String, newCoverFilename: String) {
        try {
            val objId = ObjectId(comicId)
            realm.write {
                val liveComic = query<ComicDocument>("_id == $0", objId).first().find()
                liveComic?.coverFilename = newCoverFilename
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun updateComicMetadata(
        comicId: String,
        title: String,
        series: String,
        authors: String,
        publisher: String,
        storyArc: String
    ) {
        try {
            val objId = ObjectId(comicId)
            realm.write {
                val liveComic = query<ComicDocument>("_id == $0", objId).first().find()
                liveComic?.let {
                    it.title = title
                    it.series = series
                    it.authors = authors
                    it.publisher = publisher
                    it.storyArc = storyArc
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun deleteComic(comicId: String) {
        try {
            val objId = ObjectId(comicId)
            realm.write {
                val liveComic = query<ComicDocument>("_id == $0", objId).first().find()
                liveComic?.let { delete(it) }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // --- HISTORY ---

    fun getReadingHistoryFlow(): Flow<List<ReadingHistoryDocument>> {
        return realm.query<ReadingHistoryDocument>()
            .sort("lastReadTimestamp", io.realm.kotlin.query.Sort.DESCENDING)
            .asFlow()
            .map { it.list }
    }

    suspend fun saveReadingProgress(
        comicId: String,
        filePath: String,
        title: String,
        coverPath: String,
        pageIndex: Int,
        totalPages: Int
    ) {
        realm.write {
            val existing = query<ReadingHistoryDocument>("comicId == $0", comicId).first().find()
            if (existing != null) {
                existing.lastPageOpened = pageIndex
                existing.totalPages = totalPages
                existing.lastReadTimestamp = System.currentTimeMillis()
                existing.isCompleted = pageIndex >= totalPages - 1
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
                copyToRealm(newHistory)
            }
        }
    }

    suspend fun getHistoryForComic(comicId: String): ReadingHistoryDocument? {
        return realm.query<ReadingHistoryDocument>("comicId == $0", comicId).first().find()
    }
}
