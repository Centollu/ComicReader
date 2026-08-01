package com.centollu.comicreader.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "reading_history")
class ReadingHistoryDocument {
    @PrimaryKey
    var _id: String = UUID.randomUUID().toString()
    var comicId: String = ""
    var filePath: String = ""
    var title: String = ""
    var coverPath: String = ""
    var lastPageOpened: Int = 0
    var totalPages: Int = 0
    var lastReadTimestamp: Long = System.currentTimeMillis()
    var isCompleted: Boolean = false
}
