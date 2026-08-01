package com.centollu.comicreader.data.model

import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.PrimaryKey
import org.mongodb.kbson.ObjectId

class ReadingHistoryDocument : RealmObject {
    @PrimaryKey
    var _id: ObjectId = ObjectId()
    var comicId: String = ""
    var filePath: String = ""
    var title: String = ""
    var coverPath: String = ""
    var lastPageOpened: Int = 0
    var totalPages: Int = 0
    var lastReadTimestamp: Long = System.currentTimeMillis()
    var isCompleted: Boolean = false
}
