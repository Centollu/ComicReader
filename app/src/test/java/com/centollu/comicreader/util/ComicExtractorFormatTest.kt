package com.centollu.comicreader.util

import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.FileInputStream

class ComicExtractorFormatTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun createTempFile(bytes: ByteArray): java.io.File {
        return tempFolder.newFile().apply { writeBytes(bytes) }
    }

    @Test
    fun `detecta pdf por magic bytes por fichero`() {
        val file = createTempFile(byteArrayOf(0x25, 0x50, 0x44, 0x46))
        assertEquals(ComicExtractor.ArchiveType.PDF, ComicExtractor.detectArchiveType(file) { FileInputStream(file) })
    }

    @Test
    fun `detecta pdf por magic bytes por stream`() {
        val file = createTempFile(byteArrayOf(0x25, 0x50, 0x44, 0x46))
        assertEquals(ComicExtractor.ArchiveType.PDF, ComicExtractor.detectArchiveType(null) { FileInputStream(file) })
    }

    @Test
    fun `detecta zip por magic bytes`() {
        val file = createTempFile(byteArrayOf(0x50, 0x4B, 0x03, 0x04))
        assertEquals(ComicExtractor.ArchiveType.ZIP, ComicExtractor.detectArchiveType(file) { FileInputStream(file) })
    }

    @Test
    fun `detecta rar por magic bytes`() {
        val file = createTempFile(byteArrayOf(0x52, 0x61, 0x72, 0x21))
        assertEquals(ComicExtractor.ArchiveType.RAR, ComicExtractor.detectArchiveType(file) { FileInputStream(file) })
    }

    @Test
    fun `formato desconocido devuelve unknown`() {
        val file = createTempFile("hola mundo".toByteArray())
        assertEquals(ComicExtractor.ArchiveType.UNKNOWN, ComicExtractor.detectArchiveType(file) { FileInputStream(file) })
    }

    @Test
    fun `extensiones de imagen soportadas`() {
        assertEquals(true, ComicExtractor.isSupportedImage("pagina.jpg"))
        assertEquals(true, ComicExtractor.isSupportedImage("pagina.webp"))
        assertEquals(false, ComicExtractor.isSupportedImage("ComicInfo.xml"))
        assertEquals(false, ComicExtractor.isSupportedImage(".oculto.png"))
    }
}