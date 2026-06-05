package com.foxplayer.ui.favorite

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.foxplayer.R
import com.foxplayer.model.Video
import com.foxplayer.ui.home.VideoGridAdapter

class FavoriteFragment : Fragment(R.layout.fragment_favorite) {

    private lateinit var adapter: VideoGridAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = VideoGridAdapter { video ->
            val args = Bundle().apply { putSerializable("video", video) }
            findNavController().navigate(R.id.detailFragment, args)
        }

        val rv = view.findViewById<RecyclerView>(R.id.rvFavorites)
        rv.layoutManager = GridLayoutManager(requireContext(), 3)
        rv.adapter = adapter

        // TODO: 从Room数据库加载收藏
        // 暂时空列表, 显示空状态
        view.findViewById<View>(R.id.layoutEmpty).visibility = View.VISIBLE
    }
}
