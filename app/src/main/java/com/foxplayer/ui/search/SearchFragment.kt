package com.foxplayer.ui.search

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.foxplayer.R
import com.foxplayer.model.Video
import com.foxplayer.ui.home.VideoGridAdapter
import com.foxplayer.viewmodel.SearchViewModel
import com.google.android.material.chip.Chip

class SearchFragment : Fragment(R.layout.fragment_search) {
    private val vm: SearchViewModel by viewModels()
    private lateinit var adapter: VideoGridAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // ★ 先注入SourceManager
        vm.sourceManager = (requireActivity().application as com.foxplayer.FoxApp).sourceManager

        adapter = VideoGridAdapter { video -> navigateToDetail(video) }
        val rvResults = view.findViewById<RecyclerView>(R.id.rvResults)
        rvResults.layoutManager = GridLayoutManager(requireContext(), 3)
        rvResults.adapter = adapter

        // 热门搜索关键词
        val hotWords = listOf("斗破苍穹", "庆余年", "繁花", "三体", "狂飙",
            "长相思", "与凤行", "追风者", "玫瑰的故事", "哈尔滨1944")
        val flexHot = view.findViewById<com.google.android.flexbox.FlexboxLayout>(R.id.flexHot)
        hotWords.forEach { word ->
            val chip = Chip(requireContext()).apply {
                text = word
                isCheckable = false
                setOnClickListener { doSearch(word) }
            }
            flexHot.addView(chip)
        }

        // 搜索
        val searchView = view.findViewById<androidx.appcompat.widget.SearchView>(R.id.searchViewMain)
        searchView.setOnQueryTextListener(object : androidx.appcompat.widget.SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(q: String): Boolean { doSearch(q); return true }
            override fun onQueryTextChange(q: String): Boolean = false
        })

        // 初始查询（在监听器设置后触发）
        val initialQuery = arguments?.getString("query") ?: ""
        if (initialQuery.isNotBlank()) {
            searchView.setQuery(initialQuery, true)
        }

        // 清空搜索历史
        view.findViewById<View>(R.id.tvClearHistory).setOnClickListener {
            view.findViewById<RecyclerView>(R.id.rvHistory).adapter = null
        }

        // 观察结果
        vm.results.observe(viewLifecycleOwner) { adapter.submitList(it) }

        // 返回
        view.findViewById<View>(R.id.ivBack)?.setOnClickListener {
            findNavController().navigateUp()
        }
    }

    private fun doSearch(q: String) {
        if (q.isNotBlank()) vm.search(q)
    }

    private fun navigateToDetail(video: Video) {
        val args = Bundle().apply { putSerializable("video", video) }
        findNavController().navigate(R.id.detailFragment, args)
    }
}
