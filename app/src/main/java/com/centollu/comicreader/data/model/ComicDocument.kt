package com.centollu.comicreader.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "comics")
class ComicDocument {
    @PrimaryKey
    var _id: String = UUID.randomUUID().toString()
    var filePath: String = ""
    var title: String = ""
    var issueNumber: Int? = null
    var series: String = ""
    var authors: String = ""
    var publisher: String = ""
    var storyArc: String = ""
    var coverFilename: String = ""
    var pageCount: Int = 0
    var addedTimestamp: Long = System.currentTimeMillis()
    var isNfs: Boolean = false
}
