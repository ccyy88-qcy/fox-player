package com.foxplayer.ui.live

import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.foxplayer.R
import com.foxplayer.model.LiveChannel
import com.foxplayer.viewmodel.LiveViewModel
import com.google.android.material.chip.Chip

class LiveFragment : Fragment(R.layout.fragment_live) {
    private val vm: LiveViewModel by viewModels()
    private lateinit var adapter: LiveChannelAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = LiveChannelAdapter { channel ->
            val args = Bundle().apply {
                putString("url", channel.url)
                putString("title", channel.name)
            }
            findNavController().navigate(R.id.playerFragment, args)
        }

        val rv = view.findViewById<RecyclerView>(R.id.rvChannels)
        rv.setHasFixedSize(true)
        rv.layoutManager = GridLayoutManager(requireContext(), 4)
        rv.adapter = adapter

        val tvEmpty = view.findViewById<TextView>(R.id.tvEmpty)

        vm.channels.observe(viewLifecycleOwner) { list ->
            adapter.submitList(list)
            tvEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
            tvEmpty.text = if (list.isEmpty()) "📡 直播源加载中..." else ""
        }
        vm.groups.observe(viewLifecycleOwner) { groups ->
            val chipGroup = view.findViewById<com.google.android.material.chip.ChipGroup>(R.id.chipGroup)
            chipGroup.removeAllViews()
            groups.forEach { g ->
                val chip = Chip(requireContext()).apply {
                    text = g
                    isCheckable = true
                    setOnCheckedChangeListener { _, checked ->
                        if (checked) vm.selectGroup(g)
                    }
                }
                chipGroup.addView(chip)
            }
            if (groups.isNotEmpty()) {
                (chipGroup.getChildAt(0) as? Chip)?.isChecked = true
            }
        }

        vm.toast.observe(viewLifecycleOwner) { msg ->
            Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
        }

        vm.loadDefaultChannels()

        // 返回
        view.findViewById<View>(R.id.ivBack)?.setOnClickListener {
            findNavController().navigateUp()
        }
    }
}
