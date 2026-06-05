package com.foxplayer.ui.player

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.foxplayer.R
import com.foxplayer.model.Episode

class EpisodeAdapter(
    private val onClick: (Episode) -> Unit
) : RecyclerView.Adapter<EpisodeAdapter.VH>() {

    private var items: List<Episode> = emptyList()

    fun submitList(list: List<Episode>) {
        items = list
        notifyDataSetChanged()
    }

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tv: TextView = view.findViewById(R.id.tvEpisode)
        init {
            view.setOnClickListener {
                val pos = adapterPosition
                if (pos >= 0 && pos < items.size) onClick(items[pos])
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_episode, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val ep = items[position]
        val name = ep.name.ifBlank { "第${position + 1}集" }
        holder.tv.text = name

        // 如果剧集名包含源标记，用颜色区分
        if (name.contains("[") && name.contains("]")) {
            holder.tv.setBackgroundColor(0xFF2A2D35.toInt())
        } else {
            holder.tv.setBackgroundColor(0xFF2A2D35.toInt())
        }
    }

    override fun getItemCount() = items.size
}
