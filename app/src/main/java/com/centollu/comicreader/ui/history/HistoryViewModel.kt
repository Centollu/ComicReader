package com.centollu.comicreader.ui.history

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.centollu.comicreader.data.model.ReadingHistoryDocument
import com.centollu.comicreader.data.repository.ComicRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class HistoryUiState(
    val historyItems: List<ReadingHistoryDocument> = emptyList(),
    val isLoading: Boolean = false
)

class HistoryViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ComicRepository(application.applicationContext)

    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    init {
        loadHistory()
    }

    private fun loadHistory() {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isLoading = true)
                repository.getReadingHistoryFlow().collect { historyList ->
                    _uiState.value = HistoryUiState(historyItems = historyList, isLoading = false)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }

    fun deleteHistoryItem(id: String) {
        viewModelScope.launch {
            try {
                repository.deleteHistoryItem(id)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            try {
                repository.clearHistory()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
