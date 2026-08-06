package com.centollu.comicreader.ui.folders

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.centollu.comicreader.data.model.ComicDocument
import com.centollu.comicreader.data.repository.ComicRepository
import com.centollu.comicreader.util.ComicExtractor
import com.centollu.comicreader.util.ComicTitleParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream

data class FoldersUiState(
    val isOpeningComic: Boolean = false,
    val errorMessage: String? = null
)

class FoldersViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ComicRepository(application.applicationContext)

    private val _uiState = MutableStateFlow(FoldersUiState())
    val uiState: StateFlow<FoldersUiState> = _uiState.asStateFlow()

    fun openComic(context: Context, file: File, onOpened: (ComicDocument) -> Unit) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isOpeningComic = true, errorMessage = null)
            try {
                val comic = withContext(Dispatchers.IO) { ensureComicInLibrary(context, file) }
                onOpened(comic)
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.value = _uiState.value.copy(
                    errorMessage = "No se pudo abrir el cómic: ${e.message}"
                )
            }
            _uiState.value = _uiState.value.copy(isOpeningComic = false)
        }
    }

    private suspend fun ensureComicInLibrary(context: Context, file: File): ComicDocument {
        val existing = repository.findComicByFilePath(file.absolutePath)
        if (existing != null) return existing

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
        return comicDoc
    }
}
