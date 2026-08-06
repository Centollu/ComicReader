package com.centollu.comicreader.ui.library

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.centollu.comicreader.data.model.ComicDocument
import com.centollu.comicreader.data.repository.ComicRepository
import com.centollu.comicreader.util.ComicExtractor
import com.centollu.comicreader.util.ComicTitleParser
import com.centollu.comicreader.util.AppPrefs
import com.centollu.comicreader.util.ExtractedComicResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream
import java.io.IOException

data class LibraryUiState(
    val comics: List<ComicDocument> = emptyList(),
    val searchQuery: String = "",
    val filterType: String = "ALL", // ALL, TITLE, AUTHOR, SERIES, PUBLISHER, ARC
    val sortType: String = "NAME", // NAME (filename asc), PATH (ruta asc), DATE (added desc)
    val isLoading: Boolean = false,
    val isRescanning: Boolean = false,
    val selectedComicForCoverPicker: ComicDocument? = null,
    val extractedComicResult: ExtractedComicResult? = null,
    val isExtractingForCover: Boolean = false,
    val errorMessage: String? = null
)

class LibraryViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext = application.applicationContext
    private val repository = ComicRepository(appContext)

    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    init {
        loadComics()
    }

    private fun loadComics() {
        viewModelScope.launch {
            repository.getAllComicsFlow().collect { comicList ->
                _uiState.value = _uiState.value.copy(comics = comicList)
            }
        }
    }

    fun updateSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
    }

    fun updateFilterType(filterType: String) {
        _uiState.value = _uiState.value.copy(filterType = filterType)
    }

    fun updateSortType(sortType: String) {
        _uiState.value = _uiState.value.copy(sortType = sortType)
    }

    fun getFilteredComics(): List<ComicDocument> {
        val query = _uiState.value.searchQuery.trim().lowercase()
        var comics = _uiState.value.comics
        if (query.isNotEmpty()) {
            comics = when (_uiState.value.filterType) {
                "TITLE" -> comics.filter { it.title.lowercase().contains(query) }
                "AUTHOR" -> comics.filter { it.authors.lowercase().contains(query) }
                "SERIES" -> comics.filter { it.series.lowercase().contains(query) }
                "PUBLISHER" -> comics.filter { it.publisher.lowercase().contains(query) }
                "ARC" -> comics.filter { it.storyArc.lowercase().contains(query) }
                else -> comics.filter {
                    it.title.lowercase().contains(query) ||
                    it.filePath.lowercase().contains(query) ||
                    it.authors.lowercase().contains(query) ||
                    it.series.lowercase().contains(query) ||
                    it.publisher.lowercase().contains(query) ||
                    it.storyArc.lowercase().contains(query)
                }
            }
        }

        return when (_uiState.value.sortType) {
            "DATE" -> comics.sortedByDescending { it.addedTimestamp }
            "PATH" -> comics.sortedBy { it.filePath.lowercase() }
            else -> comics.sortedBy { it.filePath.substringAfterLast('/').lowercase() }
        }
    }

    fun addComicFromFile(context: Context, fileUri: Uri, fileName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            try {
                val destFile = File(context.filesDir, fileName)
                val input = context.contentResolver.openInputStream(fileUri)
                if (input == null) {
                    throw IOException("No se pudo abrir el archivo seleccionado.")
                }
                input.use { source ->
                    destFile.outputStream().use { output -> source.copyTo(output) }
                }

                val parsed = ComicTitleParser.parse(fileName.substringBeforeLast('.'))
                val comicDoc = ComicDocument().apply {
                    this.filePath = destFile.absolutePath
                    this.title = parsed.title
                    this.issueNumber = parsed.issueNumber
                    this.series = ""
                    this.authors = ""
                    this.publisher = ""
                    this.storyArc = ""
                }

                // Escaneo rápido: extrae SOLO la portada y calcula el total de páginas
                val scanResult = ComicExtractor.extractOnlyCover(
                    context = context,
                    comicId = comicDoc._id,
                    inputStreamProvider = { FileInputStream(destFile) },
                    sourceFile = destFile
                )

                comicDoc.pageCount = scanResult.pageCount
                comicDoc.coverFilename = scanResult.coverFilename

                repository.insertOrUpdateComic(comicDoc)
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.value = _uiState.value.copy(
                    errorMessage = "No se pudo añadir el cómic: ${e.message}"
                )
            }
            _uiState.value = _uiState.value.copy(isLoading = false)
        }
    }

    fun scanLocalPath(context: Context, folderPath: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            try {
                val folder = File(folderPath)
                if (folder.exists() && folder.isDirectory) {
                    AppPrefs.addScannedFolder(context, folder.absolutePath)
                    addComicFilesInFolder(context, folder)
                } else {
                    _uiState.value = _uiState.value.copy(
                        errorMessage = "La carpeta no existe o no es un directorio."
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Error al escanear la carpeta: ${e.message}"
                )
            }
            _uiState.value = _uiState.value.copy(isLoading = false)
        }
    }

    fun rescanFolders(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(isRescanning = true, errorMessage = null)
            try {
                val folders = AppPrefs.getScannedFolders(context)
                    .map { File(it) }
                    .filter { it.exists() && it.isDirectory }

                val existingOnDisk = mutableSetOf<String>()
                for (folder in folders) {
                    existingOnDisk += addComicFilesInFolder(context, folder)
                }

                val scannedRoots = folders.map { it.absolutePath }
                val knownComics = repository.getAllComics()
                for (comic in knownComics) {
                    val filePath = comic.filePath
                    val isManaged = scannedRoots.any { filePath.startsWith(it) }
                    if (isManaged && !File(filePath).exists()) {
                        ComicExtractor.deleteComicFiles(context, comic._id)
                        repository.deleteComic(comic._id)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Error al volver a escanear: ${e.message}"
                )
            }
            _uiState.value = _uiState.value.copy(isRescanning = false)
        }
    }

    private suspend fun addComicFilesInFolder(context: Context, folder: File): Set<String> {
        val addedPaths = mutableSetOf<String>()
        val files = try {
            folder.walkTopDown().filter {
                it.isFile && (it.extension.lowercase() == "cbz" || it.extension.lowercase() == "cbr")
            }.toList()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }

        for (file in files) {
            try {
                val existing = repository.findComicByFilePath(file.absolutePath)
                if (existing == null) {
                    val parsed = ComicTitleParser.parse(file.nameWithoutExtension)
                    val comicDoc = ComicDocument().apply {
                        this.filePath = file.absolutePath
                        this.title = parsed.title
                        this.issueNumber = parsed.issueNumber
                    }

                    val scanResult = ComicExtractor.extractOnlyCover(
                        context = context,
                        comicId = comicDoc._id,
                        inputStreamProvider = { FileInputStream(file) },
                        sourceFile = file
                    )

                    comicDoc.pageCount = scanResult.pageCount
                    comicDoc.coverFilename = scanResult.coverFilename

                    repository.insertOrUpdateComic(comicDoc)
                }
                addedPaths += file.absolutePath
            } catch (e: Exception) {
                // Un archivo corrupto no debe abortar el escaneo de la carpeta completa
                e.printStackTrace()
            }
        }
        return addedPaths
    }

    fun openCoverSelectionDialog(context: Context, comic: ComicDocument) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(
                selectedComicForCoverPicker = comic,
                isExtractingForCover = true
            )

            val file = File(comic.filePath)
            val comicId = comic._id

            try {
                val result = ComicExtractor.extractComic(
                    context = context,
                    comicId = comicId,
                    inputStreamProvider = { if (file.exists()) FileInputStream(file) else null },
                    targetCoverFilename = comic.coverFilename,
                    sourceFile = file,
                    maxPages = ComicExtractor.COVER_PREVIEW_MAX_PAGES
                )

                _uiState.value = _uiState.value.copy(
                    extractedComicResult = result,
                    isExtractingForCover = false
                )
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.value = _uiState.value.copy(
                    selectedComicForCoverPicker = null,
                    isExtractingForCover = false
                )
            }
        }
    }

    fun loadAllCoverPages(context: Context) {
        val comic = _uiState.value.selectedComicForCoverPicker ?: return
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(isExtractingForCover = true)
            val file = File(comic.filePath)
            try {
                val result = ComicExtractor.extractComic(
                    context = context,
                    comicId = comic._id,
                    inputStreamProvider = { if (file.exists()) FileInputStream(file) else null },
                    targetCoverFilename = comic.coverFilename,
                    sourceFile = file,
                    maxPages = null
                )
                _uiState.value = _uiState.value.copy(
                    extractedComicResult = result,
                    isExtractingForCover = false
                )
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.value = _uiState.value.copy(isExtractingForCover = false)
            }
        }
    }

    fun setSelectedCover(context: Context, selectedImageFile: File) {
        val comic = _uiState.value.selectedComicForCoverPicker ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val comicId = comic._id
                val newCoverName = selectedImageFile.name

                // Re-extract/generate thumbnail with the newly selected cover
                val file = File(comic.filePath)
                ComicExtractor.extractComic(
                    context = context,
                    comicId = comicId,
                    inputStreamProvider = { if (file.exists()) FileInputStream(file) else null },
                    targetCoverFilename = newCoverName,
                    sourceFile = file
                )

                repository.updateCoverFilename(comicId, newCoverName)
            } catch (e: Exception) {
                e.printStackTrace()
            }

            _uiState.value = _uiState.value.copy(
                selectedComicForCoverPicker = null,
                extractedComicResult = null
            )
        }
    }

    fun closeCoverSelectionDialog() {
        _uiState.value = _uiState.value.copy(
            selectedComicForCoverPicker = null,
            extractedComicResult = null
        )
    }

    fun updateComicMetadata(
        comicId: String,
        title: String,
        issueNumber: Int?,
        series: String,
        authors: String,
        publisher: String,
        storyArc: String
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repository.updateComicMetadata(comicId, title, issueNumber, series, authors, publisher, storyArc)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun deleteComic(comicId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repository.deleteComic(comicId)
                ComicExtractor.deleteComicFiles(appContext, comicId)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
