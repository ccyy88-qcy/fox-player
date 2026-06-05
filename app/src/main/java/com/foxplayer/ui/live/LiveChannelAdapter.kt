package com.foxplayer.ui.live

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.foxplayer.databinding.ItemChannelBinding
import com.foxplayer.model.LiveChannel

class LiveChannelAdapter(private val onClick: (LiveChannel) -> Unit) :
    ListAdapter<LiveChannel, LiveChannelAdapter.VH>(DIFF) {

    inner class VH(val binding: ItemChannelBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemChannelBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, pos: Int) {
        val ch = getItem(pos)
        holder.binding.apply {
            tvName.text = ch.name
            ivLogo.load(ch.logo) { crossfade(true) }
            root.setOnClickListener { onClick(ch) }
        }
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<LiveChannel>() {
            override fun areItemsTheSame(a: LiveChannel, b: LiveChannel) = a.name == b.name
            override fun areContentsTheSame(a: LiveChannel, b: LiveChannel) = a == b
        }
    }
}
