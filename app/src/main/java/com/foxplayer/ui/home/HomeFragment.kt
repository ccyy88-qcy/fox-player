package com.foxplayer.ui.home

import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.foxplayer.R
import com.foxplayer.model.Category
import com.foxplayer.model.Video
import com.foxplayer.viewmodel.HomeViewModel
import com.google.android.material.chip.Chip

class HomeFragment : Fragment(R.layout.fragment_home) {
    private val vm: HomeViewModel by viewModels()
    private lateinit var adapter: VideoGridAdapter
    private lateinit var hotAdapter: VideoGridAdapter
    private lateinit var latestAdapter: VideoGridAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // ★ 注入 SourceManager
        vm.sourceManager = (requireActivity().application as com.foxplayer.FoxApp).sourceManager

        val tvEmpty = view.findViewById<TextView>(R.id.tvEmpty)
        val progressLoading = view.findViewById<ProgressBar>(R.id.progressLoading)
        val progressMore = view.findViewById<ProgressBar>(R.id.progressMore)

        // ── 影视网格（3列） ──
        adapter = VideoGridAdapter { video -> navigateToDetail(video) }
        val rvVideos = view.findViewById<RecyclerView>(R.id.rvVideos)
        rvVideos.setHasFixedSize(true)
        rvVideos.layoutManager = GridLayoutManager(requireContext(), 3)
        rvVideos.adapter = adapter

        // ── 热播榜单（横向） ──
        hotAdapter = VideoGridAdapter { video -> navigateToDetail(video) }
        val rvHot = view.findViewById<RecyclerView>(R.id.rvHotRank)
        rvHot.setHasFixedSize(true)
        rvHot.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        rvHot.adapter = hotAdapter

        // ── 最新上线（横向） ──
        latestAdapter = VideoGridAdapter { video -> navigateToDetail(video) }
        val rvLatest = view.findViewById<RecyclerView>(R.id.rvLatest)
        rvLatest.setHasFixedSize(true)
        rvLatest.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        rvLatest.adapter = latestAdapter

        // ── 分类Chips ──
        val chipGroup = view.findViewById<com.google.android.material.chip.ChipGroup>(R.id.chipCategory)
        val cats = listOf("🎬 电影", "📺 电视剧", "🎭 动漫", "🎤 综艺", "📡 直播", "📑 全部")
        cats.forEachIndexed { idx, label ->
            val chip = Chip(requireContext()).apply {
                text = label
                isCheckable = true
                setOnCheckedChangeListener { _, checked ->
                    if (checked) {
                        when (idx) {
                            4 -> findNavController().navigate(R.id.action_home_to_live)
                            5 -> {
                                vm.currentCategory = Category.MOVIE
                                val combined = (vm.hotVideos.value ?: emptyList()) +
                                    (vm.latestVideos.value ?: emptyList())
                                vm.videos.value = combined.distinctBy { it.title }.take(50)
                            }
                            else -> vm.setCategory(when (idx) {
                                0 -> Category.MOVIE; 1 -> Category.TV
                                2 -> Category.ANIME; 3 -> Category.VARIETY
                                else -> Category.MOVIE
                            })
                        }
                    }
                }
            }
            chipGroup.addView(chip)
        }

        // ── 观察数据 ──
        vm.videos.observe(viewLifecycleOwner) { list ->
            adapter.submitList(list)
            tvEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        }
        vm.hotVideos.observe(viewLifecycleOwner) { list ->
            hotAdapter.submitList(list)
            android.util.Log.d("HomeFragment", "hotVideos: ${list.size}")
        }
        vm.latestVideos.observe(viewLifecycleOwner) { list ->
            latestAdapter.submitList(list)
            android.util.Log.d("HomeFragment", "latestVideos: ${list.size}")
        }
        vm.loading.observe(viewLifecycleOwner) { isLoading ->
            progressLoading.visibility = if (isLoading && adapter.itemCount == 0) View.VISIBLE else View.GONE
            progressMore.visibility = if (isLoading && adapter.itemCount > 0) View.VISIBLE else View.GONE
        }
        vm.toast.observe(viewLifecycleOwner) { msg ->
            android.widget.Toast.makeText(requireContext(), msg, android.widget.Toast.LENGTH_LONG).show()
        }

        // ── 分页加载 ──
        rvVideos.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                if (dy <= 0) return
                val lm = rv.layoutManager as GridLayoutManager
                if (lm.findLastVisibleItemPosition() >= adapter.itemCount - 3) {
                    vm.loadMore(vm.currentCategory)
                }
            }
        })

        // ★ 初始加载首页数据
        vm.loadHomeData()

        // ── 搜索框 ──
        val searchView = view.findViewById<androidx.appcompat.widget.SearchView>(R.id.searchView)
        searchView?.setOnQueryTextListener(object : androidx.appcompat.widget.SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(q: String): Boolean {
                if (q.isNotBlank()) {
                    val args = Bundle().apply { putString("query", q) }
                    try {
                        findNavController().navigate(R.id.action_home_to_search, args)
                    } catch (_: Exception) {
                        findNavController().navigate(R.id.searchFragment, args)
                    }
                }
                return true
            }
            override fun onQueryTextChange(q: String): Boolean = false
        })
        // 让搜索框可点击
        searchView?.isFocusable = true
        searchView?.isFocusableInTouchMode = true
        searchView?.setIconifiedByDefault(false)
    }

    private fun navigateToDetail(video: Video) {
        val args = Bundle().apply { putSerializable("video", video) }
        try {
            findNavController().navigate(R.id.action_home_to_detail, args)
        } catch (e: Exception) {
            findNavController().navigate(R.id.detailFragment, args)
        }
    }
}
