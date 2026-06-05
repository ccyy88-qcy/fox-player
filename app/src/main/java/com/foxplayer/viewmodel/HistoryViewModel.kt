package com.foxplayer.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.ViewModel
import com.foxplayer.db.*
import kotlinx.coroutines.launch

class HistoryViewModel(private val dao: HistoryDao) : ViewModel() {
    private val _history = MutableLiveData<List<HistoryEntity>>()
    val history: LiveData<List<HistoryEntity>> = _history

    init {
        viewModelScope.launch {
            dao.getRecent().collect { _history.value = it }
        }
    }

    fun record(entity: HistoryEntity) = viewModelScope.launch { dao.upsert(entity) }
    fun delete(vid: String, sk: String) = viewModelScope.launch { dao.delete(vid, sk) }
    fun clearAll() = viewModelScope.launch { dao.clearAll() }
}
