package com.centollu.comicreader.ui.library

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.centollu.comicreader.data.model.ComicDocument
import com.centollu.comicreader.data.repository.ComicRepository
import com.centollu.comicreader.util.ComicExtractor
import com.centollu.comicreader.util.ExtractedComicResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream

data class LibraryUiState(
    val comics: List<ComicDocument> = emptyList(),
    val searchQuery: String = "",
    val filterType: String = "ALL", // ALL, TITLE, AUTHOR, SERIES, PUBLISHER, ARC
    val sortType: String = "NAME", // NAME (filename asc), DATE (added desc)
    val isLoading: Boolean = false,
    val selectedComicForCoverPicker: ComicDocument? = null,
    val extractedComicResult: ExtractedComicResult? = null,
    val isExtractingForCover: Boolean = false
)

class LibraryViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ComicRepository(application.applicationContext)

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
            else -> comics.sortedBy { it.filePath.substringAfterLast('/').lowercase() }
        }
    }

    fun addComicFromFile(context: Context, fileUri: Uri, fileName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(isLoading = true)

            val destFile = File(context.filesDir, fileName)
            context.contentResolver.openInputStream(fileUri)?.use { input ->
                destFile.outputStream().use { output -> input.copyTo(output) }
            }

            val comicDoc = ComicDocument().apply {
                this.filePath = destFile.absolutePath
                this.title = fileName.substringBeforeLast('.')
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
                fileExtension = destFile.extension,
                sourceFile = destFile
            )

            comicDoc.pageCount = scanResult.pageCount
            comicDoc.coverFilename = scanResult.coverFilename

            repository.insertOrUpdateComic(comicDoc)

            _uiState.value = _uiState.value.copy(isLoading = false)
        }
    }

    fun scanLocalPath(context: Context, folderPath: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val folder = File(folderPath)
            if (folder.exists() && folder.isDirectory) {
                val files = folder.walkTopDown().filter {
                    it.isFile && (it.extension.lowercase() == "cbz" || it.extension.lowercase() == "cbr")
                }.toList()

                for (file in files) {
                    val existing = repository.findComicByFilePath(file.absolutePath)
                    if (existing == null) {
                        val comicDoc = ComicDocument().apply {
                            this.filePath = file.absolutePath
                            this.title = file.nameWithoutExtension
                        }

                        // Escaneo ultrarrápido: extrae SOLO la portada en filesDir/covers/
                        val scanResult = ComicExtractor.extractOnlyCover(
                            context = context,
                comicId = comicDoc._id,
                            inputStreamProvider = { FileInputStream(file) },
                            fileExtension = file.extension,
                            sourceFile = file
                        )

                        comicDoc.pageCount = scanResult.pageCount
                        comicDoc.coverFilename = scanResult.coverFilename

                        repository.insertOrUpdateComic(comicDoc)
                    }
                }
            }
            _uiState.value = _uiState.value.copy(isLoading = false)
        }
    }

    fun openCoverSelectionDialog(context: Context, comic: ComicDocument) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(
                selectedComicForCoverPicker = comic,
                isExtractingForCover = true
            )

            val file = File(comic.filePath)
            val comicId = comic._id

            val result = ComicExtractor.extractComic(
                context = context,
                comicId = comicId,
                inputStreamProvider = { if (file.exists()) FileInputStream(file) else null },
                fileExtension = file.extension,
                targetCoverFilename = comic.coverFilename,
                sourceFile = file
            )

            _uiState.value = _uiState.value.copy(
                extractedComicResult = result,
                isExtractingForCover = false
            )
        }
    }

    fun setSelectedCover(context: Context, selectedImageFile: File) {
        val comic = _uiState.value.selectedComicForCoverPicker ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val comicId = comic._id
            val newCoverName = selectedImageFile.name

            // Re-extract/generate thumbnail with the newly selected cover
            val file = File(comic.filePath)
            ComicExtractor.extractComic(
                context = context,
                comicId = comicId,
                inputStreamProvider = { if (file.exists()) FileInputStream(file) else null },
                fileExtension = file.extension,
                targetCoverFilename = newCoverName,
                sourceFile = file
            )

            repository.updateCoverFilename(comicId, newCoverName)

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
        series: String,
        authors: String,
        publisher: String,
        storyArc: String
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.updateComicMetadata(comicId, title, series, authors, publisher, storyArc)
        }
    }

    fun deleteComic(comicId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteComic(comicId)
        }
    }
}
