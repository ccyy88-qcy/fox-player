package com.foxplayer.viewmodel

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.foxplayer.model.LiveChannel
import com.foxplayer.source.BuiltinSources
import com.foxplayer.source.impl.M3uParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LiveViewModel : ViewModel() {
    val channels = MutableLiveData<List<LiveChannel>>()
    val groups = MutableLiveData<List<String>>()
    val toast = MutableLiveData<String>()
    private val selectedGroup = MutableLiveData("全部")
    private var allChannels = listOf<LiveChannel>()

    fun loadDefaultChannels() {
        viewModelScope.launch {
            try {
                val list = withContext(Dispatchers.IO) {
                    M3uParser.parseFromUrl(BuiltinSources.DEFAULT_LIVE_URL)
                }
                allChannels = list
                channels.value = list
                groups.value = listOf("全部") + list.map { it.group }.distinct()
                toast.value = "直播源加载成功: ${list.size}个频道"
            } catch (e: Exception) {
                toast.value = "直播源加载失败: ${e.message}"
                // 用内置频道兜底
                val fallback = listOf(
                    LiveChannel(name = "CCTV-1 综合", url = "https://tv.cctv.com/live/cctv1/", group = "央视"),
                    LiveChannel(name = "CCTV-5 体育", url = "https://tv.cctv.com/live/cctv5/", group = "央视"),
                    LiveChannel(name = "湖南卫视", url = "https://tv.cctv.com/live/hunan/", group = "卫视"),
                )
                allChannels = fallback
                channels.value = fallback
                groups.value = listOf("全部", "央视", "卫视")
            }
        }
    }

    fun selectGroup(group: String) {
        selectedGroup.value = group
        channels.value = if (group == "全部") allChannels else allChannels.filter { it.group == group }
    }
}
