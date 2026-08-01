package com.centollu.comicreader.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ComicTitleParserTest {

    @Test
    fun `extrae titulo y numero del formato habitual`() {
        val result = ComicTitleParser.parse("Titulo que puede contener espacios 003 (Digital) (otra info)")
        assertEquals("Titulo que puede contener espacios", result.title)
        assertEquals(3, result.issueNumber)
    }

    @Test
    fun `extrae numero con ceros a la izquierda`() {
        val result = ComicTitleParser.parse("Batman 012 (2019) (Digital)")
        assertEquals("Batman", result.title)
        assertEquals(12, result.issueNumber)
    }

    @Test
    fun `nombre sin numero devuelve numero nulo`() {
        val result = ComicTitleParser.parse("Batman Anual")
        assertEquals("Batman Anual", result.title)
        assertNull(result.issueNumber)
    }

    @Test
    fun `informacion entre parentesis se ignora`() {
        val result = ComicTitleParser.parse("X-Men 100 (Digital) (c2c)")
        assertEquals("X-Men", result.title)
        assertEquals(100, result.issueNumber)
    }

    @Test
    fun `anio entre parentesis no se confunde con el numero`() {
        val result = ComicTitleParser.parse("Spider-Man (2000)")
        assertEquals("Spider-Man", result.title)
        assertNull(result.issueNumber)
    }

    @Test
    fun `nombre vacio`() {
        val result = ComicTitleParser.parse("")
        assertEquals("", result.title)
        assertNull(result.issueNumber)
    }

    @Test
    fun `numero grande devuelve null al desbordar`() {
        val result = ComicTitleParser.parse("Titulo 99999999999999999999 (Digital)")
        assertEquals("Titulo 99999999999999999999", result.title)
        assertNull(result.issueNumber)
    }
}
