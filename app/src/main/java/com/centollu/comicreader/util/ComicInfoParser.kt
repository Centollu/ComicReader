package com.centollu.comicreader.util

import android.util.Xml
import com.centollu.comicreader.data.model.ComicDocument
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader

data class ComicInfoFields(
    val title: String? = null,
    val series: String? = null,
    val number: Int? = null,
    val publisher: String? = null,
    val writer: String? = null,
    val penciller: String? = null,
    val storyArc: String? = null
)

object ComicInfoParser {

    fun parse(xml: String): ComicInfoFields {
        if (xml.isBlank()) return ComicInfoFields()
        return try {
            val parser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(StringReader(xml))

            var title: String? = null
            var series: String? = null
            var number: Int? = null
            var publisher: String? = null
            var writer: String? = null
            var penciller: String? = null
            var storyArc: String? = null

            var currentTag: String? = null
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.START_TAG -> currentTag = parser.name
                    XmlPullParser.TEXT -> {
                        if (currentTag != null) {
                            val text = parser.text.trim().ifEmpty { null }
                            when (currentTag) {
                                "Title" -> title = text
                                "Series" -> series = text
                                "Number" -> number = text?.toIntOrNull()
                                "Publisher" -> publisher = text
                                "Writer" -> writer = text
                                "Penciller" -> penciller = text
                                "StoryArc" -> storyArc = text
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> currentTag = null
                }
                event = parser.next()
            }

            ComicInfoFields(title, series, number, publisher, writer, penciller, storyArc)
        } catch (e: Exception) {
            e.printStackTrace()
            ComicInfoFields()
        }
    }

    fun applyTo(comic: ComicDocument, fields: ComicInfoFields) {
        if (fields.series != null || fields.title != null) {
            comic.title = combine(fields.series, fields.title)
        }
        fields.series?.let { comic.series = it }
        fields.number?.let { comic.issueNumber = it }
        fields.publisher?.let { comic.publisher = it }
        if (fields.writer != null || fields.penciller != null) {
            comic.authors = combine(fields.writer, fields.penciller)
        }
        fields.storyArc?.let { comic.storyArc = it }
    }

    private fun combine(first: String?, second: String?): String {
        val a = first?.trim().orEmpty()
        val b = second?.trim().orEmpty()
        return when {
            a.isEmpty() && b.isEmpty() -> ""
            a.isEmpty() -> b
            b.isEmpty() -> a
            b.startsWith(a, ignoreCase = true) -> b
            else -> "$a - $b"
        }
    }
}
