package com.foxplayer.ui.live

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.foxplayer.R
import com.foxplayer.model.LiveChannel

class LiveChannelAdapter(
    private val onClick: (LiveChannel) -> Unit
) : ListAdapter<LiveChannel, LiveChannelAdapter.VH>(Diff) {

    // 频道首字母头像背景色池
    private val avatarColors = intArrayOf(
        0xFFFF6B6B.toInt(), 0xFF4ECDC4.toInt(), 0xFF6BB8FF.toInt(),
        0xFFFFB800.toInt(), 0xFFA78BFA.toInt(), 0xFF34D399.toInt(),
        0xFFF472B6.toInt(), 0xFFFB923C.toInt(), 0xFF60A5FA.toInt(),
    )

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tvAvatar: TextView = view.findViewById(R.id.ivChannelLogo)
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

        // 频道名 — 去除多余前缀
        val displayName = ch.name
            .replace("CCTV-", "CCTV")
            .replace("cctv", "CCTV")
            .trim()
        holder.tvName.text = displayName

        // 分组名
        holder.tvGroup.text = ch.group

        // 首字母头像
        val firstChar = displayName.firstOrNull()?.uppercase() ?: "?"
        holder.tvAvatar.text = firstChar
        val colorIdx = position % avatarColors.size
        holder.tvAvatar.setBackgroundColor(avatarColors[colorIdx])

        // 点击
        holder.itemView.setOnClickListener { onClick(ch) }
    }

    object Diff : DiffUtil.ItemCallback<LiveChannel>() {
        override fun areItemsTheSame(a: LiveChannel, b: LiveChannel) = a.url == b.url
        override fun areContentsTheSame(a: LiveChannel, b: LiveChannel) = a == b
    }
}
