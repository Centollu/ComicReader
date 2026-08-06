package com.centollu.comicreader.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.core.content.edit
import com.github.junrar.Archive
import com.github.junrar.rarfile.FileHeader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.util.Enumeration
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

data class ExtractedComicResult(
    val comicId: String,
    val extractedDir: File,
    val pageFiles: List<File>, // sorted naturally/alphabetically
    val coverFile: File?,
    val coverFilename: String,
    val isPartial: Boolean = false
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

    private const val PARTIAL_MARKER_FILENAME = ".partial-extraction"
    private const val COMIC_INFO_FILENAME = "ComicInfo.xml"
    const val COVER_PREVIEW_MAX_PAGES = 6

    private val SUPPORTED_IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp")

    enum class ArchiveType { ZIP, RAR, UNKNOWN }

    /**
     * Detecta el tipo de archivo leyendo los magic bytes en lugar de confiar en la extensión.
     */
    fun detectArchiveType(sourceFile: File?, inputStreamProvider: () -> InputStream?): ArchiveType {
        if (sourceFile != null && sourceFile.exists()) {
            return try {
                FileInputStream(sourceFile).use { detectFromStream(it) }
            } catch (_: Exception) {
                ArchiveType.UNKNOWN
            }
        }
        val stream = inputStreamProvider() ?: return ArchiveType.UNKNOWN
        return try {
            stream.use { detectFromStream(it) }
        } catch (_: Exception) {
            ArchiveType.UNKNOWN
        }
    }

    private fun detectFromStream(stream: InputStream): ArchiveType {
        val magic = ByteArray(4)
        val count = stream.read(magic)
        if (count < 4) return ArchiveType.UNKNOWN
        val signature = magic.joinToString("") { "%02X".format(it) }
        return when {
            signature.startsWith("504B") -> ArchiveType.ZIP   // "PK"
            signature.startsWith("526172") -> ArchiveType.RAR  // "Rar!"
            else -> ArchiveType.UNKNOWN
        }
    }

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
        try {
            root.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        } catch (e: Exception) {
            e.printStackTrace()
            0L
        }
    }

    /**
     * Desaloja cómics extraídos de la caché siguiendo FIFO (los más antiguos primero)
     * hasta que el tamaño total esté por debajo del límite configurado.
     */
    suspend fun trimCache(context: Context) = withContext(Dispatchers.IO) {
        enforceCacheLimit(context)
    }

    private fun enforceCacheLimit(context: Context, protectedDir: File? = null) {
        try {
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
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun File.totalSize(): Long = try {
        walkTopDown().filter { it.isFile }.sumOf { it.length() }
    } catch (e: Exception) {
        e.printStackTrace()
        0L
    }

    private fun listImageFiles(dir: File): List<File> = try {
        dir.walkTopDown().filter { it.isFile && isSupportedImage(it.name) }.toList()
    } catch (e: Exception) {
        e.printStackTrace()
        emptyList()
    }

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
        targetCoverFilename: String? = null,
        sourceFile: File? = null
    ): QuickScanResult = withContext(Dispatchers.IO) {

        val coversDir = File(context.filesDir, "covers")
        if (!coversDir.exists()) coversDir.mkdirs()

        val thumbnailFile = File(coversDir, "$comicId.jpg")
        val tempCoverFile = File(context.cacheDir, "temp_cover_$comicId.tmp")

        var pageCount = 0
        var foundCoverName = ""
        val archiveType = detectArchiveType(sourceFile, inputStreamProvider)

        if (archiveType == ArchiveType.ZIP) {
            try {
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
            } catch (e: Exception) {
                e.printStackTrace()
            }
        } else if (archiveType == ArchiveType.RAR) {
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
     * Intenta leer el contenido del fichero ComicInfo.xml del archivo (CBZ/CBR),
     * devolviendo el XML en bruto o null si no existe o no se puede leer.
     */
    fun readComicInfoXml(sourceFile: File?, inputStreamProvider: () -> InputStream?): String? {
        val archiveType = detectArchiveType(sourceFile, inputStreamProvider)
        return try {
            when (archiveType) {
                ArchiveType.ZIP -> readZipComicInfo(sourceFile, inputStreamProvider)
                ArchiveType.RAR -> readRarComicInfo(sourceFile, inputStreamProvider)
                ArchiveType.UNKNOWN -> null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun readZipComicInfo(sourceFile: File?, inputStreamProvider: () -> InputStream?): String? {
        if (sourceFile != null && sourceFile.exists()) {
            ZipFile(sourceFile).use { zipFile ->
                val entry = findZipEntry(zipFile.entries(), COMIC_INFO_FILENAME)
                if (entry != null) {
                    return zipFile.getInputStream(entry).use { it.readBytes().toString(Charsets.UTF_8) }
                }
            }
            return null
        }
        val stream = inputStreamProvider() ?: return null
        return ZipInputStream(stream).use { zipStream ->
            var entry: ZipEntry? = zipStream.nextEntry
            while (entry != null) {
                if (entry.name.substringAfterLast('/').equals(COMIC_INFO_FILENAME, ignoreCase = true)) {
                    return@use zipStream.readBytes().toString(Charsets.UTF_8)
                }
                zipStream.closeEntry()
                entry = zipStream.nextEntry
            }
            null
        }
    }

    private fun readRarComicInfo(sourceFile: File?, inputStreamProvider: () -> InputStream?): String? {
        val archive = if (sourceFile != null && sourceFile.exists()) {
            Archive(sourceFile)
        } else {
            Archive(inputStreamProvider() ?: return null)
        }
        var xml: String? = null
        try {
            var header: FileHeader? = archive.nextFileHeader()
            while (header != null) {
                if (header.fileName.substringAfterLast('/').equals(COMIC_INFO_FILENAME, ignoreCase = true)) {
                    val bytes = ByteArrayOutputStream()
                    archive.extractFile(header, bytes)
                    xml = bytes.toString(Charsets.UTF_8.name())
                    break
                }
                header = archive.nextFileHeader()
            }
        } finally {
            archive.close()
        }
        return xml
    }

    private fun findZipEntry(entries: Enumeration<out ZipEntry>, filename: String): ZipEntry? {
        while (entries.hasMoreElements()) {
            val entry = entries.nextElement()
            if (entry.name.substringAfterLast('/').equals(filename, ignoreCase = true)) return entry
        }
        return null
    }

    /**
     * Descompresión completa de todas las páginas cuando el usuario abre el cómic para leer.
     * Si [maxPages] se especifica, solo se extraen las primeras N páginas (extracción parcial),
     * marcada con un fichero de marcador para poder ampliarla después.
     */
    suspend fun extractComic(
        context: Context,
        comicId: String,
        inputStreamProvider: () -> InputStream?,
        targetCoverFilename: String? = null,
        sourceFile: File? = null,
        onPageExtracted: ((File) -> Unit)? = null,
        maxPages: Int? = null
    ): ExtractedComicResult = withContext(Dispatchers.IO) {

        val cacheBaseDir = File(context.cacheDir, "extracted/$comicId")
        val partialMarker = File(cacheBaseDir, PARTIAL_MARKER_FILENAME)
        val requestFull = maxPages == null

        // Reutilizar la extracción si ya está en caché
        var allImages = if (cacheBaseDir.exists()) {
            listImageFiles(cacheBaseDir)
        } else {
            emptyList()
        }

        val hasFullCache = allImages.isNotEmpty() && !partialMarker.exists()
        val needExtraction = if (requestFull) {
            !hasFullCache
        } else {
            allImages.size < (maxPages ?: 1)
        }

        if (needExtraction) {
            if (cacheBaseDir.exists()) {
                cacheBaseDir.deleteRecursively()
            }
            cacheBaseDir.mkdirs()

            val archiveType = detectArchiveType(sourceFile, inputStreamProvider)
            when (archiveType) {
                ArchiveType.ZIP -> extractZip(inputStreamProvider, cacheBaseDir, sourceFile, maxPages, onPageExtracted)
                ArchiveType.RAR -> extractRar(inputStreamProvider, cacheBaseDir, sourceFile, maxPages, onPageExtracted)
                ArchiveType.UNKNOWN -> { /* no se pudo identificar el formato */ }
            }

            if (requestFull) {
                partialMarker.delete()
            } else {
                partialMarker.createNewFile()
            }

            allImages = listImageFiles(cacheBaseDir).sortedWith(NaturalOrderComparator())
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
            coverFilename = coverName,
            isPartial = requestFull.not() && partialMarker.exists()
        )
    }

    private fun extractZip(
        inputStreamProvider: () -> InputStream?,
        outputDir: File,
        sourceFile: File? = null,
        maxPages: Int? = null,
        onPageExtracted: ((File) -> Unit)? = null
    ) {
        val usedNames = mutableSetOf<String>()
        var extractedCount = 0
        try {
            if (sourceFile != null && sourceFile.exists()) {
                // Pasada 1: listar imágenes para determinar cuáles conservar (las PRIMERAS)
                val imageNames = mutableListOf<String>()
                ZipFile(sourceFile).use { zipFile ->
                    val entries = zipFile.entries()
                    while (entries.hasMoreElements()) {
                        val entry = entries.nextElement()
                        if (!entry.isDirectory && isSupportedImage(entry.name)) {
                            imageNames.add(entry.name)
                        }
                    }
                }

                val keepIndices = leadingIndices(imageNames, maxPages)

                // Pasada 2: extraer solo las conservadas
                ZipFile(sourceFile).use { zipFile ->
                    val entries = zipFile.entries()
                    var idx = 0
                    while (entries.hasMoreElements()) {
                        val entry = entries.nextElement()
                        if (!entry.isDirectory && isSupportedImage(entry.name)) {
                            if (keepIndices == null || idx in keepIndices) {
                                extractZipEntry(
                                    input = { zipFile.getInputStream(entry) },
                                    entryName = entry.name,
                                    outputDir = outputDir,
                                    usedNames = usedNames,
                                    onPageExtracted = onPageExtracted
                                )
                                extractedCount++
                            }
                            idx++
                        }
                    }
                }
            } else {
                // Flujo de transmisión sin fichero: no es posible retroceder, se extrae desde el inicio (fallback).
                val stream = inputStreamProvider() ?: return
                ZipInputStream(stream).use { zipStream ->
                    var entry: ZipEntry? = zipStream.nextEntry
                    while (entry != null) {
                        if (maxPages != null && extractedCount >= maxPages) break
                        if (!entry.isDirectory && isSupportedImage(entry.name)) {
                            extractZipEntry(
                                input = { zipStream },
                                entryName = entry.name,
                                outputDir = outputDir,
                                usedNames = usedNames,
                                onPageExtracted = onPageExtracted
                            )
                            extractedCount++
                        }
                        zipStream.closeEntry()
                        entry = zipStream.nextEntry
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun extractZipEntry(
        input: () -> InputStream,
        entryName: String,
        outputDir: File,
        usedNames: MutableSet<String>,
        onPageExtracted: ((File) -> Unit)?
    ) {
        try {
            val outputFile = File(outputDir, uniqueFileName(SimpleFileName(entryName), usedNames))
            FileOutputStream(outputFile).use { out ->
                input().use { it.copyTo(out) }
            }
            onPageExtracted?.invoke(outputFile)
        } catch (e: Exception) {
            // Una entrada corrupta no debe abortar la extracción del resto del archivo
            e.printStackTrace()
        }
    }

    private fun extractRar(
        inputStreamProvider: () -> InputStream?,
        outputDir: File,
        sourceFile: File? = null,
        maxPages: Int? = null,
        onPageExtracted: ((File) -> Unit)? = null
    ) {
        val usedNames = mutableSetOf<String>()
        var extractedCount = 0
        try {
            if (sourceFile != null && sourceFile.exists()) {
                // Pasada 1: listar imágenes para determinar cuáles conservar (las PRIMERAS)
                val imageNames = mutableListOf<String>()
                val countArchive = Archive(sourceFile)
                var countHeader = countArchive.nextFileHeader()
                while (countHeader != null) {
                    if (!countHeader.isDirectory && isSupportedImage(countHeader.fileName)) {
                        imageNames.add(countHeader.fileName)
                    }
                    countHeader = countArchive.nextFileHeader()
                }
                countArchive.close()

                val keepIndices = leadingIndices(imageNames, maxPages)

                // Pasada 2: extraer solo las conservadas
                val archive = Archive(sourceFile)
                var header = archive.nextFileHeader()
                var idx = 0
                while (header != null) {
                    if (!header.isDirectory && isSupportedImage(header.fileName)) {
                        if (keepIndices == null || idx in keepIndices) {
                            try {
                                val outputFile = File(outputDir, uniqueFileName(SimpleFileName(header.fileName), usedNames))
                                FileOutputStream(outputFile).use { out ->
                                    archive.extractFile(header, out)
                                }
                                onPageExtracted?.invoke(outputFile)
                                extractedCount++
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                        idx++
                    }
                    header = archive.nextFileHeader()
                }
                archive.close()
            } else {
                // Flujo de transmisión sin fichero: no es posible retroceder, se extrae desde el inicio (fallback).
                val stream = inputStreamProvider() ?: return
                val archive = Archive(stream)
                var header = archive.nextFileHeader()
                while (header != null) {
                    if (maxPages != null && extractedCount >= maxPages) break
                    if (!header.isDirectory && isSupportedImage(header.fileName)) {
                        try {
                            val outputFile = File(outputDir, uniqueFileName(SimpleFileName(header.fileName), usedNames))
                            FileOutputStream(outputFile).use { out ->
                                archive.extractFile(header, out)
                            }
                            onPageExtracted?.invoke(outputFile)
                            extractedCount++
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                    header = archive.nextFileHeader()
                }
                archive.close()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Dado el listado de imágenes en orden de archivo, devuelve los índices que deben
     * conservarse: las primeras [maxPages] páginas en orden natural. Si [maxPages] es null,
     * devuelve null (conservar todas).
     */
    private fun leadingIndices(imageNames: List<String>, maxPages: Int?): Set<Int>? {
        if (maxPages == null) return null
        if (imageNames.isEmpty()) return emptySet()
        val naturalOrder = imageNames.indices.sortedWith(
            compareBy(NATURAL_STRING_COMPARATOR) { imageNames[it] }
        )
        val keepCount = if (imageNames.size <= maxPages) imageNames.size else maxPages
        return naturalOrder.take(keepCount).toSet()
    }

    private val NATURAL_STRING_COMPARATOR = Comparator<String> { a, b ->
        NaturalOrderComparator().compare(File(a), File(b))
    }

    private fun SimpleFileName(path: String): String {
        return File(path).name.replace("[^a-zA-Z0-9._-]".toRegex(), "_")
    }

    /**
     * Devuelve un nombre de archivo único en [usedNames], añadiendo un sufijo numérico
     * cuando el nombre sanitizado ya ha sido usado (p. ej. entradas de subcarpetas
     * distintas con el mismo nombre de imagen).
     */
    private fun uniqueFileName(baseName: String, usedNames: MutableSet<String>): String {
        var candidate = baseName
        var counter = 1
        while (!usedNames.add(candidate)) {
            val dot = baseName.lastIndexOf('.')
            val stem = if (dot > 0) baseName.substring(0, dot) else baseName
            val ext = if (dot > 0) baseName.substring(dot) else ""
            candidate = "${stem}_$counter$ext"
            counter++
        }
        return candidate
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
