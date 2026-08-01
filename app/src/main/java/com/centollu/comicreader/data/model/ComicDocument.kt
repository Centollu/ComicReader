package com.centollu.comicreader.data.model

import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.PrimaryKey
import org.mongodb.kbson.ObjectId

class ComicDocument : RealmObject {
    @PrimaryKey
    var _id: ObjectId = ObjectId()
    var filePath: String = ""
    var title: String = ""
    var series: String = ""
    var authors: String = ""
    var publisher: String = ""
    var storyArc: String = ""
    var coverFilename: String = ""
    var pageCount: Int = 0
    var addedTimestamp: Long = System.currentTimeMillis()
    var isNfs: Boolean = false
}
