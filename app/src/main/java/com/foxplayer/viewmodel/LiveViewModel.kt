package com.foxplayer.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.foxplayer.model.LiveChannel
import com.foxplayer.source.impl.M3uParser
import kotlinx.coroutines.launch

class LiveViewModel : ViewModel() {
    private val _channels = MutableLiveData<List<LiveChannel>>()
    val channels: LiveData<List<LiveChannel>> = _channels

    private val _groups = MutableLiveData<List<String>>()
    val groups: LiveData<List<String>> = _groups

    private val _selectedGroup = MutableLiveData("全部")
    val selectedGroup: LiveData<String> = _selectedGroup

    private val _loading = MutableLiveData(false)
    val loading: LiveData<Boolean> = _loading

    fun loadFromUrl(url: String) {
        _loading.value = true
        viewModelScope.launch {
            _channels.value = M3uParser.parseFromUrl(url)
            updateGroups()
            _loading.value = false
        }
    }

    fun loadFromText(text: String) {
        viewModelScope.launch {
            _channels.value = M3uParser.parseFromText(text)
            updateGroups()
        }
    }

    fun loadDefaultChannels() {
        if (_channels.value.isNullOrEmpty()) loadFromUrl(DEFAULT_LIVE_URL)
    }

    fun selectGroup(group: String) { _selectedGroup.value = group }

    private fun updateGroups() {
        val list = _channels.value ?: return
        _groups.value = listOf("全部") + list.map { it.group }.distinct()
    }

    companion object {
        // 默认直播源 — 央视+卫视
        const val DEFAULT_LIVE_URL = "https://live.fanmingming.com/tv/m3u/v6.m3u"
    }
}
