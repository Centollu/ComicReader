package com.centollu.comicreader.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.centollu.comicreader.data.model.ReadingHistoryDocument
import com.centollu.comicreader.data.repository.MongoRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class HistoryUiState(
    val historyItems: List<ReadingHistoryDocument> = emptyList(),
    val isLoading: Boolean = false
)

class HistoryViewModel(private val repository: MongoRepository = MongoRepository()) : ViewModel() {

    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    init {
        loadHistory()
    }

    private fun loadHistory() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            repository.getReadingHistoryFlow().collect { historyList ->
                _uiState.value = HistoryUiState(historyItems = historyList, isLoading = false)
            }
        }
    }
}
