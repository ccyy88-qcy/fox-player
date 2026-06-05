package com.foxplayer.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.foxplayer.model.Category
import com.foxplayer.model.Video
import com.foxplayer.source.SourceManager
import kotlinx.coroutines.launch

class HomeViewModel : ViewModel() {
    private val _videos = MutableLiveData<List<Video>>()
    val videos: LiveData<List<Video>> = _videos

    private val _loading = MutableLiveData(false)
    val loading: LiveData<Boolean> = _loading

    private val _category = MutableLiveData(Category.MOVIE)
    val category: LiveData<Category> = _category

    var sourceManager: SourceManager? = null

    fun setCategory(cat: Category) {
        _category.value = cat
        loadVideos(cat)
    }

    fun loadVideos(cat: Category) {
        _loading.value = true
        viewModelScope.launch {
            val manager = sourceManager
            if (manager != null && cat != Category.LIVE && cat != Category.FAVORITE) {
                _videos.value = manager.getCategoryAll(cat.name, 1)
            } else {
                _videos.value = generatePlaceholder(cat)
            }
            _loading.value = false
        }
    }

    fun refresh() = loadVideos(_category.value ?: Category.MOVIE)

    private fun generatePlaceholder(cat: Category): List<Video> {
        return (1..18).map { i ->
            Video(id = "${cat.name}_$i", title = "${cat.label}推荐 $i",
                cover = "https://picsum.photos/300/420?random=$i",
                rating = (5..9).random() + (0..9).random() * 0.1f,
                type = cat.name.lowercase())
        }
    }
}
