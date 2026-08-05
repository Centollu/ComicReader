package com.centollu.comicreader.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.core.content.edit
import com.github.junrar.Archive
import com.github.junrar.rarfile.FileHeader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
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

    private const val CACHE_PREFS = "comic_cache_prefs"
    private const val KEY_CACHE_MAX_SIZE = "cache_max_size_bytes"
    private const val DEFAULT_CACHE_MAX_SIZE: Long = 5L * 1024 * 1024 * 1024

    private val SUPPORTED_IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp")

    fun getMaxCacheSize(context: Context): Long {
        val prefs = context.getSharedPreferences(CACHE_PREFS, Context.MODE_PRIVATE)
        return prefs.getLong(KEY_CACHE_MAX_SIZE, DEFAULT_CACHE_MAX_SIZE).coerceAtLeast(1L)
    }

    fun setMaxCacheSize(context: Context, sizeBytes: Long) {
        context.getSharedPreferences(CACHE_PREFS, Context.MODE_PRIVATE)
            .edit {
                putLong(KEY_CACHE_MAX_SIZE, sizeBytes.coerceAtLeast(1L))
            }
    }

    suspend fun getCurrentCacheSize(context: Context): Long = withContext(Dispatchers.IO) {
        val root = File(context.cacheDir, "extracted")
        if (!root.exists()) return@withContext 0L
        root.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    /**
     * Desaloja cómics extraídos de la caché siguiendo FIFO (los más antiguos primero)
     * hasta que el tamaño total esté por debajo del límite configurado.
     */
    suspend fun trimCache(context: Context) = withContext(Dispatchers.IO) {
        enforceCacheLimit(context)
    }

    private fun enforceCacheLimit(context: Context, protectedDir: File? = null) {
        val root = File(context.cacheDir, "extracted")
        if (!root.exists()) return
        val maxSize = getMaxCacheSize(context)

        val dirs = root.listFiles()?.filter { it.isDirectory }?.toMutableList() ?: return
        var total = dirs.sumOf { it.totalSize() }

        // FIFO: borrar primero los directorios con menor lastModified (más antiguos)
        dirs.sortBy { it.lastModified() }

        for (dir in dirs) {
            if (total <= maxSize) break
            if (dir == protectedDir) continue
            total -= dir.totalSize()
            dir.deleteRecursively()
        }
    }

    private fun File.totalSize(): Long = walkTopDown().filter { it.isFile }.sumOf { it.length() }

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
        targetCoverFilename: String? = null,
        sourceFile: File? = null
    ): QuickScanResult = withContext(Dispatchers.IO) {

        val coversDir = File(context.filesDir, "covers")
        if (!coversDir.exists()) coversDir.mkdirs()

        val thumbnailFile = File(coversDir, "$comicId.jpg")
        val tempCoverFile = File(context.cacheDir, "temp_cover_$comicId.tmp")

        var pageCount = 0
        var foundCoverName = ""
        val ext = fileExtension.lowercase()

        if (ext == "cbz" || ext == "zip") {
            val coverEntries = mutableListOf<String>()
            if (sourceFile != null && sourceFile.exists()) {
                ZipFile(sourceFile).use { zipFile ->
                    var coverExtracted = false
                    val entries = zipFile.entries()
                    while (entries.hasMoreElements()) {
                        val entry = entries.nextElement()
                        if (!entry.isDirectory && isSupportedImage(entry.name)) {
                            coverEntries.add(entry.name)
                            pageCount++

                            val nameOnly = SimpleFileName(entry.name)
                            if (!coverExtracted && (targetCoverFilename.isNullOrEmpty() || nameOnly.equals(targetCoverFilename, ignoreCase = true))) {
                                foundCoverName = nameOnly
                                FileOutputStream(tempCoverFile).use { out ->
                                    zipFile.getInputStream(entry).use { it.copyTo(out) }
                                }
                                coverExtracted = true
                            }
                        }
                    }
                }
            } else {
                val stream = inputStreamProvider()
                if (stream != null) {
                    ZipInputStream(stream).use { zipStream ->
                        var entry: ZipEntry? = zipStream.nextEntry
                        var coverExtracted = false

                        while (entry != null) {
                            if (!entry.isDirectory && isSupportedImage(entry.name)) {
                                coverEntries.add(entry.name)
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
                    }
                }
            }

            if (foundCoverName.isEmpty() && coverEntries.isNotEmpty()) {
                // Fallback: use first image entry name
                foundCoverName = SimpleFileName(coverEntries.first())
            }
        } else if (ext == "cbr" || ext == "rar") {
            try {
                val archive = if (sourceFile != null && sourceFile.exists()) {
                    Archive(sourceFile)
                } else {
                    val stream = inputStreamProvider()
                    if (stream == null) {
                        return@withContext QuickScanResult(0, "", "")
                    }
                    Archive(stream)
                }
                var header: FileHeader? = archive.nextFileHeader()
                var coverExtracted = false
                val imageEntries = mutableListOf<String>()

                while (header != null) {
                    if (!header.isDirectory && isSupportedImage(header.fileName)) {
                        imageEntries.add(header.fileName)
                        pageCount++

                        val nameOnly = SimpleFileName(header.fileName)
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
        targetCoverFilename: String? = null,
        sourceFile: File? = null,
        onPageExtracted: ((File) -> Unit)? = null
    ): ExtractedComicResult = withContext(Dispatchers.IO) {

        val cacheBaseDir = File(context.cacheDir, "extracted/$comicId")

        // Reutilizar la extracción si ya está en caché
        var allImages = if (cacheBaseDir.exists()) {
            cacheBaseDir.walkTopDown()
                .filter { it.isFile && isSupportedImage(it.name) }
                .toList()
        } else {
            emptyList()
        }

        if (allImages.isEmpty()) {
            if (cacheBaseDir.exists()) {
                cacheBaseDir.deleteRecursively()
            }
            cacheBaseDir.mkdirs()

            val ext = fileExtension.lowercase()
            if (ext == "cbz" || ext == "zip") {
                extractZip(inputStreamProvider, cacheBaseDir, sourceFile, onPageExtracted)
            } else if (ext == "cbr" || ext == "rar") {
                extractRar(inputStreamProvider, cacheBaseDir, sourceFile, onPageExtracted)
            }

            allImages = cacheBaseDir.walkTopDown()
                .filter { it.isFile && isSupportedImage(it.name) }
                .sortedWith(NaturalOrderComparator())
                .toList()
        } else {
            allImages = allImages.sortedWith(NaturalOrderComparator())
        }

        // Aplicar evicción FIFO para no superar el límite de caché configurado
        enforceCacheLimit(context, protectedDir = cacheBaseDir)

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

    private fun extractZip(
        inputStreamProvider: () -> InputStream?,
        outputDir: File,
        sourceFile: File? = null,
        onPageExtracted: ((File) -> Unit)? = null
    ) {
        if (sourceFile != null && sourceFile.exists()) {
            ZipFile(sourceFile).use { zipFile ->
                val entries = zipFile.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    if (!entry.isDirectory && isSupportedImage(entry.name)) {
                        val fileName = SimpleFileName(entry.name)
                        val outputFile = File(outputDir, fileName)
                        FileOutputStream(outputFile).use { out ->
                            zipFile.getInputStream(entry).use { it.copyTo(out) }
                        }
                        onPageExtracted?.invoke(outputFile)
                    }
                }
            }
            return
        }
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
                    onPageExtracted?.invoke(outputFile)
                }
                zipStream.closeEntry()
                entry = zipStream.nextEntry
            }
        }
    }

    private fun extractRar(
        inputStreamProvider: () -> InputStream?,
        outputDir: File,
        sourceFile: File? = null,
        onPageExtracted: ((File) -> Unit)? = null
    ) {
        try {
            val archive = if (sourceFile != null && sourceFile.exists()) {
                Archive(sourceFile)
            } else {
                val stream = inputStreamProvider() ?: return
                Archive(stream)
            }
            var header: FileHeader? = archive.nextFileHeader()
            while (header != null) {
                if (!header.isDirectory && isSupportedImage(header.fileName)) {
                    val fileName = SimpleFileName(header.fileName)
                    val outputFile = File(outputDir, fileName)
                    FileOutputStream(outputFile).use { out ->
                        archive.extractFile(header, out)
                    }
                    onPageExtracted?.invoke(outputFile)
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

    fun deleteComicFiles(context: Context, comicId: String) {
        val coverThumb = File(context.filesDir, "covers/$comicId.jpg")
        if (coverThumb.exists()) coverThumb.delete()

        val extractedDir = File(context.cacheDir, "extracted/$comicId")
        if (extractedDir.exists()) extractedDir.deleteRecursively()

        val tempCover = File(context.cacheDir, "temp_cover_$comicId.tmp")
        if (tempCover.exists()) tempCover.delete()
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
