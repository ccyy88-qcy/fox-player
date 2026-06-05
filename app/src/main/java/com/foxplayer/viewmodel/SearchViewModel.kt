package com.foxplayer.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.foxplayer.model.Video
import com.foxplayer.source.SourceManager
import kotlinx.coroutines.launch

class SearchViewModel : ViewModel() {
    private val _results = MutableLiveData<List<Video>>()
    val results: LiveData<List<Video>> = _results

    private val _loading = MutableLiveData(false)
    val loading: LiveData<Boolean> = _loading

    private val _query = MutableLiveData("")
    val query: LiveData<String> = _query

    var sourceManager: SourceManager? = null

    fun search(keyword: String) {
        if (keyword.isBlank()) return
        _query.value = keyword
        _loading.value = true
        viewModelScope.launch {
            val manager = sourceManager
            _results.value = if (manager != null) {
                manager.searchAll(keyword)
            } else {
                (1..12).map { i ->
                    Video(id = "s_$i", title = "$keyword 结果 $i",
                        cover = "https://picsum.photos/300/420?random=${i+50}",
                        rating = (5..9).random() + (0..9).random() * 0.1f)
                }
            }
            _loading.value = false
        }
    }
}
