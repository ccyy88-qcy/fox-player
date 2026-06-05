package com.foxplayer.ui.live

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import com.foxplayer.R
import com.foxplayer.databinding.FragmentLiveBinding
import com.foxplayer.viewmodel.LiveViewModel

class LiveFragment : Fragment(R.layout.fragment_live) {
    private var _binding: FragmentLiveBinding? = null
    private val binding get() = _binding!!
    private val vm: LiveViewModel by viewModels()
    private lateinit var adapter: LiveChannelAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _binding = FragmentLiveBinding.bind(view)

        adapter = LiveChannelAdapter { channel ->
            val dir = LiveFragmentDirections.actionLiveToPlayer(
                channel.url, channel.name, channel.logo
            )
            findNavController().navigate(dir)
        }
        binding.rvChannels.layoutManager = GridLayoutManager(requireContext(), 4)
        binding.rvChannels.adapter = adapter

        vm.channels.observe(viewLifecycleOwner) { list ->
            val group = vm.selectedGroup.value
            adapter.submitList(if (group == "全部") list else list.filter { it.group == group })
        }
        vm.groups.observe(viewLifecycleOwner) { groups ->
            binding.chipGroup.removeAllViews()
            groups.forEach { g ->
                val chip = com.google.android.material.chip.Chip(requireContext()).apply {
                    text = g
                    isCheckable = true
                    setOnCheckedChangeListener { _, checked -> if (checked) vm.selectGroup(g) }
                }
                binding.chipGroup.addView(chip)
            }
            (binding.chipGroup.getChildAt(0) as? com.google.android.material.chip.Chip)?.isChecked = true
        }

        vm.loadChannels()
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
