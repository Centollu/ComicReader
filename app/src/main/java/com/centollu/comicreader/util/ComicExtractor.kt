package com.centollu.comicreader.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.github.junrar.Archive
import com.github.junrar.rarfile.FileHeader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

data class ExtractedComicResult(
    val comicId: String,
    val extractedDir: File,
    val pageFiles: List<File>, // sorted naturally/alphabetically
    val coverFile: File?,
    val coverFilename: String
)

data class QuickScanResult(
    val pageCount: Int,
    val coverFilename: String,
    val coverThumbnailPath: String
)

object ComicExtractor {

    private val SUPPORTED_IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp")

    fun isSupportedImage(filename: String): Boolean {
        val ext = filename.substringAfterLast('.', "").lowercase()
        return SUPPORTED_IMAGE_EXTENSIONS.contains(ext) && !filename.contains("__MACOSX") && !filename.startsWith(".")
    }

    /**
     * Extrae ÚNICAMENTE la portada y cuenta el total de páginas sin descomprimir el archivo completo.
     * Guarda la miniatura en `context.filesDir/covers/` para persistencia duradera.
     */
    suspend fun extractOnlyCover(
        context: Context,
        comicId: String,
        inputStreamProvider: () -> InputStream?,
        fileExtension: String,
        targetCoverFilename: String? = null
    ): QuickScanResult = withContext(Dispatchers.IO) {

        val coversDir = File(context.filesDir, "covers")
        if (!coversDir.exists()) coversDir.mkdirs()

        val thumbnailFile = File(coversDir, "$comicId.jpg")
        val tempCoverFile = File(context.cacheDir, "temp_cover_$comicId.tmp")

        var pageCount = 0
        var foundCoverName = ""
        val ext = fileExtension.lowercase()

        if (ext == "cbz" || ext == "zip") {
            val stream = inputStreamProvider()
            if (stream != null) {
                ZipInputStream(stream).use { zipStream ->
                    var entry: ZipEntry? = zipStream.nextEntry
                    var coverExtracted = false

                    val imageEntries = mutableListOf<String>()

                    while (entry != null) {
                        if (!entry.isDirectory && isSupportedImage(entry.name)) {
                            imageEntries.add(entry.name)
                            pageCount++

                            val nameOnly = SimpleFileName(entry.name)
                            if (!coverExtracted && (targetCoverFilename.isNullOrEmpty() || nameOnly.equals(targetCoverFilename, ignoreCase = true))) {
                                foundCoverName = nameOnly
                                FileOutputStream(tempCoverFile).use { out ->
                                    zipStream.copyTo(out)
                                }
                                coverExtracted = true
                            }
                        }
                        zipStream.closeEntry()
                        entry = zipStream.nextEntry
                    }

                    if (!coverExtracted && imageEntries.isNotEmpty()) {
                        // Fallback: use first image entry name
                        foundCoverName = SimpleFileName(imageEntries.first())
                    }
                }
            }
        } else if (ext == "cbr" || ext == "rar") {
            val stream = inputStreamProvider()
            if (stream != null) {
                try {
                    val archive = Archive(stream)
                    var header: FileHeader? = archive.nextFileHeader()
                    var coverExtracted = false
                    val imageEntries = mutableListOf<String>()

                    while (header != null) {
                        if (!header.isDirectory && isSupportedImage(header.fileNameString)) {
                            imageEntries.add(header.fileNameString)
                            pageCount++

                            val nameOnly = SimpleFileName(header.fileNameString)
                            if (!coverExtracted && (targetCoverFilename.isNullOrEmpty() || nameOnly.equals(targetCoverFilename, ignoreCase = true))) {
                                foundCoverName = nameOnly
                                FileOutputStream(tempCoverFile).use { out ->
                                    archive.extractFile(header, out)
                                }
                                coverExtracted = true
                            }
                        }
                        header = archive.nextFileHeader()
                    }
                    archive.close()

                    if (!coverExtracted && imageEntries.isNotEmpty()) {
                        foundCoverName = SimpleFileName(imageEntries.first())
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        // Generar miniatura recortada en `filesDir/covers/`
        if (tempCoverFile.exists() && tempCoverFile.length() > 0) {
            saveScaledThumbnail(tempCoverFile, thumbnailFile)
            tempCoverFile.delete()
        }

        QuickScanResult(
            pageCount = pageCount,
            coverFilename = foundCoverName,
            coverThumbnailPath = if (thumbnailFile.exists()) thumbnailFile.absolutePath else ""
        )
    }

    /**
     * Descompresión completa de todas las páginas cuando el usuario abre el cómic para leer.
     */
    suspend fun extractComic(
        context: Context,
        comicId: String,
        inputStreamProvider: () -> InputStream?,
        fileExtension: String,
        targetCoverFilename: String? = null
    ): ExtractedComicResult = withContext(Dispatchers.IO) {

        val cacheBaseDir = File(context.cacheDir, "extracted/$comicId")
        if (cacheBaseDir.exists()) {
            cacheBaseDir.deleteRecursively()
        }
        cacheBaseDir.mkdirs()

        val ext = fileExtension.lowercase()
        if (ext == "cbz" || ext == "zip") {
            extractZip(inputStreamProvider, cacheBaseDir)
        } else if (ext == "cbr" || ext == "rar") {
            extractRar(inputStreamProvider, cacheBaseDir)
        }

        // Obtener todas las imágenes ordenadas alfabéticamente/naturalmente
        val allImages = cacheBaseDir.walkTopDown()
            .filter { it.isFile && isSupportedImage(it.name) }
            .sortedWith(NaturalOrderComparator())
            .toList()

        val coverFile = if (!targetCoverFilename.isNullOrEmpty()) {
            allImages.find { it.name.equals(targetCoverFilename, ignoreCase = true) } ?: allImages.firstOrNull()
        } else {
            allImages.firstOrNull()
        }

        val coverName = coverFile?.name ?: ""

        // Guardar thumbnail en almacenamiento persistente filesDir/covers/
        if (coverFile != null) {
            val coversDir = File(context.filesDir, "covers")
            if (!coversDir.exists()) coversDir.mkdirs()
            val thumbnailFile = File(coversDir, "$comicId.jpg")
            saveScaledThumbnail(coverFile, thumbnailFile)
        }

        ExtractedComicResult(
            comicId = comicId,
            extractedDir = cacheBaseDir,
            pageFiles = allImages,
            coverFile = coverFile,
            coverFilename = coverName
        )
    }

    private fun extractZip(inputStreamProvider: () -> InputStream?, outputDir: File) {
        val stream = inputStreamProvider() ?: return
        ZipInputStream(stream).use { zipStream ->
            var entry: ZipEntry? = zipStream.nextEntry
            while (entry != null) {
                if (!entry.isDirectory && isSupportedImage(entry.name)) {
                    val fileName = SimpleFileName(entry.name)
                    val outputFile = File(outputDir, fileName)
                    FileOutputStream(outputFile).use { out ->
                        zipStream.copyTo(out)
                    }
                }
                zipStream.closeEntry()
                entry = zipStream.nextEntry
            }
        }
    }

    private fun extractRar(inputStreamProvider: () -> InputStream?, outputDir: File) {
        val stream = inputStreamProvider() ?: return
        try {
            val archive = Archive(stream)
            var header: FileHeader? = archive.nextFileHeader()
            while (header != null) {
                if (!header.isDirectory && isSupportedImage(header.fileNameString)) {
                    val fileName = SimpleFileName(header.fileNameString)
                    val outputFile = File(outputDir, fileName)
                    FileOutputStream(outputFile).use { out ->
                        archive.extractFile(header, out)
                    }
                }
                header = archive.nextFileHeader()
            }
            archive.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun SimpleFileName(path: String): String {
        return File(path).name.replace("[^a-zA-Z0-9._-]".toRegex(), "_")
    }

    private fun saveScaledThumbnail(sourceImageFile: File, targetThumbnailFile: File) {
        try {
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(sourceImageFile.absolutePath, options)

            val targetSize = 400
            var sampleSize = 1
            while (options.outWidth / sampleSize > targetSize || options.outHeight / sampleSize > targetSize) {
                sampleSize *= 2
            }

            val decodeOpts = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
            }
            val bitmap = BitmapFactory.decodeFile(sourceImageFile.absolutePath, decodeOpts)

            if (bitmap != null) {
                FileOutputStream(targetThumbnailFile).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
                }
                bitmap.recycle()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun getCoverThumbnailFile(context: Context, comicId: String): File? {
        val persistentFile = File(context.filesDir, "covers/$comicId.jpg")
        if (persistentFile.exists()) return persistentFile
        
        val cacheFile = File(context.cacheDir, "covers/$comicId.jpg")
        if (cacheFile.exists()) return cacheFile
        
        return null
    }
}

class NaturalOrderComparator : Comparator<File> {
    override fun compare(f1: File, f2: File): Int {
        val s1 = f1.name
        val s2 = f2.name

        var i1 = 0
        var i2 = 0

        while (i1 < s1.length && i2 < s2.length) {
            val c1 = s1[i1]
            val c2 = s2[i2]

            if (c1.isDigit() && c2.isDigit()) {
                var num1Str = ""
                while (i1 < s1.length && s1[i1].isDigit()) {
                    num1Str += s1[i1]
                    i1++
                }
                var num2Str = ""
                while (i2 < s2.length && s2[i2].isDigit()) {
                    num2Str += s2[i2]
                    i2++
                }
                val n1 = num1Str.toLongOrNull() ?: 0L
                val n2 = num2Str.toLongOrNull() ?: 0L
                if (n1 != n2) {
                    return n1.compareTo(n2)
                }
            } else {
                if (c1 != c2) {
                    return c1.lowercaseChar().compareTo(c2.lowercaseChar())
                }
                i1++
                i2++
            }
        }
        return s1.length.compareTo(s2.length)
    }
}
