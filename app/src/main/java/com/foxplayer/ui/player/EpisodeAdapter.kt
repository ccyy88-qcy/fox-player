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

        // 带源标记的用颜色区分
        if (name.contains("[") && name.contains("]")) {
            holder.tv.setTextColor(0xFFFF8A8A.toInt()) // accent light
        } else {
            holder.tv.setTextColor(0xFF8892A0.toInt()) // text secondary
        }
    }

    override fun getItemCount() = items.size
}
