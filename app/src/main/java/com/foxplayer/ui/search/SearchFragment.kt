package com.foxplayer.ui.search

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import com.foxplayer.R
import com.foxplayer.databinding.FragmentSearchBinding
import com.foxplayer.ui.home.VideoGridAdapter
import com.foxplayer.viewmodel.SearchViewModel

class SearchFragment : Fragment(R.layout.fragment_search) {
    private var _binding: FragmentSearchBinding? = null
    private val binding get() = _binding!!
    private val vm: SearchViewModel by viewModels()
    private lateinit var adapter: VideoGridAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _binding = FragmentSearchBinding.bind(view)

        adapter = VideoGridAdapter { video ->
            val dir = SearchFragmentDirections.actionSearchToPlayer(video.id, video.title, video.cover)
            findNavController().navigate(dir)
        }
        binding.rvResults.layoutManager = GridLayoutManager(requireContext(), 3)
        binding.rvResults.adapter = adapter

        vm.results.observe(viewLifecycleOwner) { adapter.submitList(it) }
        vm.loading.observe(viewLifecycleOwner) { binding.progressBar.visibility = if (it) View.VISIBLE else View.GONE }

        binding.searchView.setOnQueryTextListener(object : androidx.appcompat.widget.SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(q: String): Boolean { vm.search(q); return true }
            override fun onQueryTextChange(q: String): Boolean = false
        })
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
