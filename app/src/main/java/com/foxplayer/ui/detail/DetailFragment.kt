package com.foxplayer.ui.detail

import android.app.AlertDialog
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.foxplayer.FoxApp
import com.foxplayer.R
import com.foxplayer.model.Episode
import com.foxplayer.model.Video
import com.foxplayer.source.BuiltinSources
import com.foxplayer.source.SourceManager
import com.foxplayer.ui.player.EpisodeAdapter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import com.google.gson.Gson
import com.google.gson.JsonObject
import java.util.concurrent.TimeUnit

class DetailFragment : Fragment(R.layout.fragment_detail) {

    private var video: Video? = null
    private lateinit var episodeAdapter: EpisodeAdapter
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // 源切换相关
    private val availableSources = BuiltinSources.getPrimarySources()
    private var currentSourceIdx = 0
    private var allEpisodesBySource = mutableMapOf<String, List<Episode>>()
    private var episodesLoaded = false

    // 正倒序
    private var sortAscending = true
    private var rawEpisodes = listOf<Episode>()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        @Suppress("DEPRECATION")
        video = arguments?.getSerializable("video") as? Video

        episodeAdapter = EpisodeAdapter { ep ->
            playEpisode(ep.url)
        }

        val rvEpisodes = view.findViewById<RecyclerView>(R.id.rvEpisodes)
        rvEpisodes.layoutManager = GridLayoutManager(requireContext(), 4)
        rvEpisodes.adapter = episodeAdapter

        // 先展示基本信息
        bindBasicInfo(view)

        // 异步加载完整详情
        loadDetail(view)

        // 返回
        view.findViewById<View>(R.id.ivBack)?.setOnClickListener {
            findNavController().navigateUp()
        }

        // 正倒序切换
        val btnSort = view.findViewById<TextView>(R.id.btnSortOrder)
        btnSort?.setOnClickListener {
            sortAscending = !sortAscending
            btnSort.text = if (sortAscending) "正序 ▼" else "倒序 ▲"
            applySort()
        }
    }

    /** 应用排序到剧集列表 */
    private fun applySort() {
        val sorted = if (sortAscending) rawEpisodes else rawEpisodes.reversed()
        episodeAdapter.submitList(sorted)
    }

    private fun bindBasicInfo(view: View) {
        val v = video ?: return

        view.findViewById<ImageView>(R.id.ivDetailCover).load(v.cover) { crossfade(true) }
        view.findViewById<TextView>(R.id.tvDetailTitle).text = v.title
        view.findViewById<TextView>(R.id.tvDetailYear).text = v.year.ifBlank { "--" }
        view.findViewById<TextView>(R.id.tvDetailArea).text = v.area.ifBlank { "--" }
        view.findViewById<TextView>(R.id.tvDetailType).text = v.type.ifBlank { "--" }

        // 评分
        val layoutStars = view.findViewById<LinearLayout>(R.id.layoutDetailStars)
        layoutStars.removeAllViews()
        val fullStars = (v.rating / 2).toInt().coerceIn(0, 5)
        for (i in 0 until 5) {
            val star = TextView(requireContext()).apply {
                text = if (i < fullStars) "★" else "☆"
                setTextColor(if (i < fullStars) 0xFFFF6B6B.toInt() else 0xFF666666.toInt())
                textSize = 16f
            }
            layoutStars.addView(star)
        }
        view.findViewById<TextView>(R.id.tvDetailRating).text =
            if (v.rating > 0) String.format("%.1f", v.rating) else "暂无评分"

        // 显示当前源
        updateSourceDisplay(view)

        view.findViewById<TextView>(R.id.tvDetailActors).text = v.desc.take(100)
        view.findViewById<TextView>(R.id.tvDetailDesc).text = v.desc

        // 展开/收起
        val tvDesc = view.findViewById<TextView>(R.id.tvDetailDesc)
        val tvExpand = view.findViewById<TextView>(R.id.tvExpandDesc)
        var expanded = false
        tvExpand.setOnClickListener {
            expanded = !expanded
            tvDesc.maxLines = if (expanded) Int.MAX_VALUE else 4
            tvExpand.text = if (expanded) "收起 ▲" else "展开 ▼"
        }

        // 收藏
        view.findViewById<View>(R.id.btnFavorite).setOnClickListener {
            android.widget.Toast.makeText(requireContext(), "收藏成功", android.widget.Toast.LENGTH_SHORT).show()
        }

        // 分享
        view.findViewById<View>(R.id.btnShare).setOnClickListener {
            val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(android.content.Intent.EXTRA_TEXT, "推荐《${v.title}》- FoxPlayer 🦊")
            }
            startActivity(android.content.Intent.createChooser(shareIntent, "分享"))
        }

        // 小窗口播放
        view.findViewById<View>(R.id.btnPip).setOnClickListener {
            val url = video?.episodes?.firstOrNull()?.url ?: return@setOnClickListener
            val intent = android.content.Intent(requireContext(), com.foxplayer.ui.player.PlayerActivity::class.java).apply {
                putExtra("url", url)
                putExtra("title", video?.title ?: "")
            }
            startActivity(intent)
        }

        // ★ 源切换按钮
        view.findViewById<View>(R.id.btnSourceMenu).setOnClickListener {
            showSourcePicker(view)
        }
        view.findViewById<View>(R.id.btnSwitchSource).setOnClickListener {
            showSourcePicker(view)
        }

        // 播放
        view.findViewById<View>(R.id.btnPlay).setOnClickListener {
            playEpisode(video?.episodes?.firstOrNull()?.url ?: "")
        }
    }

    /** ★ 弹出播放源选择菜单 */
    private fun showSourcePicker(view: View) {
        val v = video ?: return
        val sources = availableSources.map { it.name }
        AlertDialog.Builder(requireContext())
            .setTitle("选择播放源")
            .setSingleChoiceItems(sources.toTypedArray(), currentSourceIdx) { dialog, which ->
                dialog.dismiss()
                if (which != currentSourceIdx) {
                    currentSourceIdx = which
                    val selectedSource = availableSources[which]
                    updateSourceDisplay(view)

                    // 尝试从该源加载该影片的剧集列表
                    scope.launch {
                        val episodes = searchAndGetEpisodes(v.title, selectedSource.api)
                        if (episodes.isNotEmpty()) {
                            allEpisodesBySource[selectedSource.api] = episodes
                            video = video?.copy(episodes = episodes)
                            rawEpisodes = episodes
                            applySort()
                            android.widget.Toast.makeText(requireContext(),
                                "已切换至 ${selectedSource.name}，找到 ${episodes.size} 集",
                                android.widget.Toast.LENGTH_SHORT).show()
                        } else {
                            android.widget.Toast.makeText(requireContext(),
                                "${selectedSource.name} 暂无该片源",
                                android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    /** ★ 搜索该片在其他源的剧集 */
    private suspend fun searchAndGetEpisodes(title: String, apiUrl: String): List<Episode> {
        return withContext(Dispatchers.IO) {
            try {
                val searchUrl = "$apiUrl?ac=videolist&wd=${java.net.URLEncoder.encode(title, "UTF-8")}&pg=1"
                val client = OkHttpClient.Builder()
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(15, TimeUnit.SECONDS)
                    .build()
                val resp = client.newCall(Request.Builder().url(searchUrl)
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
                    .build()).execute()
                val body = resp.body?.string() ?: return@withContext emptyList()

                val json = Gson().fromJson(body, JsonObject::class.java)
                val list = json.getAsJsonArray("list") ?: return@withContext emptyList()
                if (list.size() == 0) return@withContext emptyList()

                // 找匹配的片
                var matched: JsonObject? = null
                for (i in 0 until list.size()) {
                    val obj = list[i].asJsonObject
                    val name = obj.get("vod_name")?.asString ?: ""
                    if (name.contains(title) || title.contains(name)) {
                        matched = obj
                        break
                    }
                }
                if (matched == null) matched = list[0].asJsonObject
                val finalMatched = matched!!

                val detailUrl = "$apiUrl?ac=detail&ids=${finalMatched.get("vod_id")?.asString ?: ""}"
                val detailResp = client.newCall(Request.Builder().url(detailUrl).build()).execute()
                val detailBody = detailResp.body?.string() ?: return@withContext emptyList()
                val detailJson = Gson().fromJson(detailBody, JsonObject::class.java)
                val detailList = detailJson.getAsJsonArray("list") ?: return@withContext emptyList()
                if (detailList.size() == 0) return@withContext emptyList()

                parseEpisodes(detailList[0].asJsonObject)
            } catch (_: Exception) { emptyList() }
        }
    }

    /** 解析vod_play_url为剧集列表 */
    private fun parseEpisodes(detail: JsonObject): List<Episode> {
        val vodPlayUrl = detail.get("vod_play_url")?.asString ?: return emptyList()
        val vodPlayFrom = detail.get("vod_play_from")?.asString ?: ""
        val lines = vodPlayUrl.split("$$$")
        val lineNames = vodPlayFrom.split("$$$")
        val episodes = mutableListOf<Episode>()

        lines.forEachIndexed { idx, line ->
            val lineName = lineNames.getOrElse(idx) { "线路${idx + 1}" }
            line.split("#").forEach { epStr ->
                val parts = epStr.split("$")
                if (parts.size >= 2) {
                    val rawUrl = parts[1].replace("\\/", "/").replace("\\u0026", "&").trim()
                    val cleanUrl = if (rawUrl.startsWith("//")) "https:$rawUrl" else rawUrl
                    if (cleanUrl.isNotBlank() && cleanUrl.startsWith("http")) {
                        episodes.add(Episode(
                            name = "[${lineName}] ${parts[0].trim()}",
                            url = cleanUrl
                        ))
                    }
                }
            }
        }
        return episodes
    }

    /** 更新当前源显示 */
    private fun updateSourceDisplay(view: View) {
        val sourceName = availableSources.getOrNull(currentSourceIdx)?.name ?: "自动"
        view.findViewById<TextView>(R.id.tvCurrentSource).text = sourceName
    }

    private fun loadDetail(view: View) {
        val v = video ?: return
        val sourceKey = v.sourceKey
        if (sourceKey.isBlank()) return

        scope.launch {
            try {
                val app = requireActivity().application as FoxApp
                val manager = app.sourceManager
                val detail = withContext(Dispatchers.IO) {
                    manager.getDetailWithFallback(v)
                }
                if (detail != null && detail.episodes.isNotEmpty()) {
                    video = detail
                    view.findViewById<TextView>(R.id.tvDetailDesc).text = detail.desc
                    episodeAdapter.submitList(detail.episodes)
                    rawEpisodes = detail.episodes
                    applySort()
                    allEpisodesBySource[sourceKey] = detail.episodes
                    episodesLoaded = true
                }
            } catch (e: Exception) {
                android.util.Log.e("DetailFragment", "loadDetail error", e)
            }
        }
    }

    private fun playEpisode(url: String) {
        val v = video ?: return
        if (url.isBlank()) return

        android.util.Log.d("DetailFragment", "playEpisode: $url")
        val args = Bundle().apply {
            putString("url", url)
            putString("title", v.title)
        }
        try {
            findNavController().navigate(R.id.playerFragment, args)
        } catch (_: Exception) {
            // Fallback: use PlayerActivity
            val intent = android.content.Intent(requireContext(), com.foxplayer.ui.player.PlayerActivity::class.java).apply {
                putExtra("url", url)
                putExtra("title", v.title)
            }
            startActivity(intent)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
    }
}
