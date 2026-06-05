package com.foxplayer.ui.live

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.foxplayer.R
import com.foxplayer.model.LiveChannel

class LiveChannelAdapter(
    private val onClick: (LiveChannel) -> Unit
) : ListAdapter<LiveChannel, LiveChannelAdapter.VH>(Diff) {

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tvChannelName)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_live_channel, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val ch = getItem(position)
        // 直接显示完整频道名
        holder.tvName.text = ch.name.ifBlank { "未知频道" }
        holder.itemView.setOnClickListener { onClick(ch) }
    }

    object Diff : DiffUtil.ItemCallback<LiveChannel>() {
        override fun areItemsTheSame(a: LiveChannel, b: LiveChannel) = a.url == b.url
        override fun areContentsTheSame(a: LiveChannel, b: LiveChannel) = a == b
    }
}
