package com.foxplayer.ui.live

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
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
        val ivLogo: ImageView = view.findViewById(R.id.ivChannelLogo)
        val tvName: TextView = view.findViewById(R.id.tvChannelName)
        val tvGroup: TextView = view.findViewById(R.id.tvChannelGroup)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_live_channel, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val ch = getItem(position)
        holder.tvName.text = ch.name
        holder.tvGroup.text = ch.group
        // 直播封面暂时用文字头像替代
        holder.ivLogo.setImageDrawable(null)
        holder.ivLogo.setBackgroundColor(0xFF2A2D35.toInt())
        holder.itemView.setOnClickListener { onClick(ch) }
    }

    object Diff : DiffUtil.ItemCallback<LiveChannel>() {
        override fun areItemsTheSame(a: LiveChannel, b: LiveChannel) = a.url == b.url
        override fun areContentsTheSame(a: LiveChannel, b: LiveChannel) = a == b
    }
}
