package com.foxplayer.ui.favorite

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import com.foxplayer.R
import com.foxplayer.databinding.FragmentFavoriteBinding
import com.foxplayer.model.Video
import com.foxplayer.ui.home.VideoGridAdapter

class FavoriteFragment : Fragment(R.layout.fragment_favorite) {
    private var _binding: FragmentFavoriteBinding? = null
    private val binding get() = _binding!!

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _binding = FragmentFavoriteBinding.bind(view)
        val adapter = VideoGridAdapter { /* navigate to player */ }
        binding.rvFavorites.layoutManager = GridLayoutManager(requireContext(), 3)
        binding.rvFavorites.adapter = adapter
        // Phase 2: load from Room DB
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
