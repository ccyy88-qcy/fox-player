package com.foxplayer.ui.home

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import com.foxplayer.R
import com.foxplayer.databinding.FragmentHomeBinding
import com.foxplayer.model.Category
import com.foxplayer.viewmodel.HomeViewModel

class HomeFragment : Fragment(R.layout.fragment_home) {
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private val vm: HomeViewModel by viewModels()
    private lateinit var adapter: VideoGridAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _binding = FragmentHomeBinding.bind(view)

        adapter = VideoGridAdapter { video ->
            val dir = HomeFragmentDirections.actionHomeToPlayer(video.id, video.title, video.cover)
            findNavController().navigate(dir)
        }
        binding.rvVideos.layoutManager = GridLayoutManager(requireContext(), 3)
        binding.rvVideos.adapter = adapter

        // Category chips
        val cats = Category.values().filter { it != Category.FAVORITE }
        cats.forEach { cat ->
            val chip = com.google.android.material.chip.Chip(requireContext()).apply {
                text = "${cat.icon} ${cat.label}"
                isCheckable = true
                setOnCheckedChangeListener { _, checked ->
                    if (checked) vm.setCategory(cat)
                }
            }
            binding.chipGroup.addView(chip)
        }
        (binding.chipGroup.getChildAt(0) as? com.google.android.material.chip.Chip)?.isChecked = true

        vm.videos.observe(viewLifecycleOwner) { adapter.submitList(it) }
        vm.loading.observe(viewLifecycleOwner) {
            binding.swipeRefresh.isRefreshing = it
        }

        binding.swipeRefresh.setOnRefreshListener { vm.loadVideos(vm.category.value ?: Category.MOVIE) }
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
