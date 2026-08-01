package com.centollu.comicreader.ui.reader

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.centollu.comicreader.data.model.ComicDocument
import com.centollu.comicreader.data.repository.ComicRepository
import com.centollu.comicreader.util.ComicExtractor
import com.centollu.comicreader.util.NfsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream

data class ReaderUiState(
    val comic: ComicDocument? = null,
    val pageFiles: List<File> = emptyList(),
    val currentPageIndex: Int = 0,
    val isLoading: Boolean = true,
    val errorMessage: String? = null
)

class ReaderViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ComicRepository(application.applicationContext)

    private val _uiState = MutableStateFlow(ReaderUiState())
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    fun loadComic(context: Context, comicId: String, initialPageIndex: Int = 0) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

            val comic = repository.getComicById(comicId)
            if (comic == null) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Cómic no encontrado en la base de datos."
                )
                return@launch
            }

            try {
                val file = File(comic.filePath)
                val result = ComicExtractor.extractComic(
                    context = context,
                    comicId = comicId,
                    inputStreamProvider = {
                        if (file.exists()) FileInputStream(file)
                        else NfsManager.getInputStreamForPath(comic.filePath)
                    },
                    fileExtension = file.extension.ifEmpty { "cbz" },
                    targetCoverFilename = comic.coverFilename
                )

                val startPage = if (initialPageIndex in result.pageFiles.indices) initialPageIndex else 0

                _uiState.value = _uiState.value.copy(
                    comic = comic,
                    pageFiles = result.pageFiles,
                    currentPageIndex = startPage,
                    isLoading = false
                )

                // Save initial progress to history
                saveProgress(startPage)

            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Error al descomprimir el cómic: ${e.message}"
                )
            }
        }
    }

    fun onPageChanged(newPageIndex: Int) {
        val pages = _uiState.value.pageFiles
        if (newPageIndex in pages.indices) {
            _uiState.value = _uiState.value.copy(currentPageIndex = newPageIndex)
            saveProgress(newPageIndex)
        }
    }

    private fun saveProgress(pageIndex: Int) {
        val comic = _uiState.value.comic ?: return
        val total = _uiState.value.pageFiles.size
        val comicId = comic._id

        viewModelScope.launch(Dispatchers.IO) {
            repository.saveReadingProgress(
                comicId = comicId,
                filePath = comic.filePath,
                title = comic.title,
                coverPath = comic.coverFilename,
                pageIndex = pageIndex,
                totalPages = total
            )
        }
    }
}
