package com.foxplayer.viewmodel

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.foxplayer.model.Category
import com.foxplayer.model.Video
import com.foxplayer.source.BuiltinSources
import com.foxplayer.source.SourceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class HomeViewModel : ViewModel() {
    val videos = MutableLiveData<List<Video>>()
    val hotVideos = MutableLiveData<List<Video>>()
    val latestVideos = MutableLiveData<List<Video>>()
    val toast = MutableLiveData<String>()
    val loading = MutableLiveData(false)

    var sourceManager: SourceManager? = null
    var currentPage = 1
    var currentCategory = Category.MOVIE

    /** ★ 从指定源拉取最新列表 */
    private suspend fun fetchFromSource(apiUrl: String, page: Int = 1): List<Video> {
        return withContext(Dispatchers.IO) {
            try {
                val url = "$apiUrl?ac=videolist&pg=$page"
                val client = OkHttpClient.Builder()
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(15, TimeUnit.SECONDS)
                    .followRedirects(true)
                    .build()
                val req = Request.Builder().url(url)
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/120.0.0.0 Mobile Safari/537.36")
                    .build()
                val resp = client.newCall(req).execute()
                val body = resp.body?.string() ?: return@withContext emptyList()

                // 快速解析JSON提取视频列表
                val list = mutableListOf<Video>()
                try {
                    val json = com.google.gson.Gson().fromJson(body, com.google.gson.JsonObject::class.java)
                    val arr = json.getAsJsonArray("list") ?: return@withContext emptyList()
                    for (i in 0 until arr.size()) {
                        val obj = arr[i].asJsonObject
                        list.add(Video(
                            id = obj.get("vod_id")?.asString ?: "",
                            title = obj.get("vod_name")?.asString ?: "",
                            cover = (obj.get("vod_pic")?.asString ?: "")
                                .replace("\\/", "/").replace("\\u0026", "&"),
                            desc = obj.get("vod_content")?.asString?.take(100) ?: "",
                            year = obj.get("vod_year")?.asString ?: "",
                            area = obj.get("vod_area")?.asString ?: "",
                            type = obj.get("type_name")?.asString ?: "",
                            rating = obj.get("vod_score")?.asString?.toFloatOrNull() ?: 0f,
                            sourceKey = apiUrl,
                        ))
                    }
                } catch (_: Exception) { }
                list
            } catch (_: Exception) { emptyList() }
        }
    }

    /** ★ 首页初始化：从主源拉取热播+最新并排显示 */
    fun loadHomeData() {
        loading.value = true
        viewModelScope.launch {
            try {
                // 从所有主源并行拉取
                val primaryApis = BuiltinSources.getPrimarySources().map { it.api }

                val allResults = kotlinx.coroutines.coroutineScope {
                    primaryApis.map { api ->
                        async {
                            val list = fetchFromSource(api, 1)
                            android.util.Log.d("HomeVM", "fetch $api → ${list.size}条")
                            list
                        }
                    }.awaitAll().flatten().distinctBy { it.title }
                }

                // 热播：按评分排序取前20
                hotVideos.value = allResults.sortedByDescending { it.rating }.take(20)

                // 最新：最近添加的（就是列表前20）
                latestVideos.value = allResults.take(20)

                // 默认显示影视列表
                videos.value = allResults.take(30)

                android.util.Log.d("HomeVM", "首页加载完成: ${allResults.size}条")
            } catch (e: Exception) {
                android.util.Log.e("HomeVM", "首页加载失败", e)
                toast.value = "加载失败，请检查网络后重试"
            } finally {
                loading.value = false
            }
        }
    }

    /** ★ 分类切换 */
    fun setCategory(cat: Category) {
        currentCategory = cat
        currentPage = 1
        loadVideos(cat)
    }

    /** ★ 按分类加载影视 */
    fun loadVideos(cat: Category) {
        if (cat == Category.LIVE || cat == Category.FAVORITE) return
        loading.value = true
        viewModelScope.launch {
            try {
                val mgr = sourceManager
                if (mgr == null) {
                    // sourceManager还没初始化，直连API
                    val allApis = BuiltinSources.videoSources.map { it.api }
                    val results = kotlinx.coroutines.coroutineScope {
                        allApis.map { api ->
                            async { fetchFromSource(api, currentPage) }
                        }.awaitAll().flatten().distinctBy { it.title }
                    }

                    // 按分类名过滤
                    val filtered = when (cat) {
                        Category.MOVIE -> results.filter { it.type.contains("电影") || it.type.contains("片") }
                        Category.TV -> results.filter { it.type.contains("剧") || it.type.contains("电视") }
                        Category.ANIME -> results.filter { it.type.contains("动漫") || it.type.contains("动画") || it.type.contains("番") }
                        Category.VARIETY -> results.filter { it.type.contains("综艺") || it.type.contains("秀") }
                        else -> results
                    }.take(50)

                    videos.value = filtered
                } else {
                    val list = mgr.getCategoryAll(cat.name, currentPage)
                    videos.value = list
                }
            } catch (e: Exception) {
                toast.value = "分类加载出错: ${e.message}"
                videos.value = emptyList()
            } finally {
                loading.value = false
            }
        }
    }

    /** ★ 加载更多（分页） */
    fun loadMore(cat: Category) {
        currentPage++
        if (cat == Category.LIVE || cat == Category.FAVORITE) return
        viewModelScope.launch {
            try {
                val more = sourceManager?.getCategoryAll(cat.name, currentPage) ?: emptyList()
                val current = videos.value ?: emptyList()
                videos.value = current + more
            } catch (_: Exception) { }
        }
    }
}
