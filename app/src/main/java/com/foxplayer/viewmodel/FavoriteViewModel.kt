package com.foxplayer.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.foxplayer.db.*
import com.foxplayer.model.Video
import kotlinx.coroutines.launch

class FavoriteViewModel(private val dao: FavoriteDao) : ViewModel() {
    private val _favorites = MutableLiveData<List<Video>>()
    val favorites: LiveData<List<Video>> = _favorites

    init {
        viewModelScope.launch {
            dao.getAll().collect { list ->
                _favorites.value = list.map { it.toVideo() }
            }
        }
    }

    fun toggle(video: Video) = viewModelScope.launch {
        if (dao.isFavorite(video.id, video.sourceKey)) {
            dao.delete(video.id, video.sourceKey)
        } else {
            dao.insert(video.toFavorite())
        }
    }

    fun isFavorite(video: Video, callback: (Boolean) -> Unit) = viewModelScope.launch {
        callback(dao.isFavorite(video.id, video.sourceKey))
    }
}
