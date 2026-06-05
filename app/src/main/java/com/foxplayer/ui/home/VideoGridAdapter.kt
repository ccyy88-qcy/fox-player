package com.foxplayer.ui.home

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.foxplayer.R
import com.foxplayer.model.Video

class VideoGridAdapter(
    private val onClick: (Video) -> Unit
) : ListAdapter<Video, VideoGridAdapter.VH>(Diff) {

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val ivCover: ImageView = view.findViewById(R.id.ivCover)
        val tvTitle: TextView = view.findViewById(R.id.tvTitle)
        val tvYear: TextView = view.findViewById(R.id.tvYear)
        val tvType: TextView = view.findViewById(R.id.tvType)
        val tvRating: TextView = view.findViewById(R.id.tvRating)
        val tvQuality: TextView = view.findViewById(R.id.tvQuality)
        val tvUpdate: TextView = view.findViewById(R.id.tvUpdate)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_video, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val video = getItem(position)

        // 封面
        if (video.cover.isNotBlank()) {
            holder.ivCover.load(video.cover) {
                crossfade(true)
                placeholder(android.R.color.darker_gray)
            }
        } else {
            holder.ivCover.setImageResource(android.R.color.darker_gray)
        }

        // 标题
        holder.tvTitle.text = video.title.ifBlank { "未知" }

        // 年份·类型
        holder.tvYear.text = video.year.ifBlank { "--" }
        holder.tvType.text = video.type.ifBlank { "--" }

        // 评分（覆盖在封面上）
        val rating = video.rating
        if (rating > 0) {
            val stars = (rating / 2).toInt().coerceIn(0, 5)
            holder.tvRating.text = "★".repeat(stars) + " ${String.format("%.1f", rating)}"
            holder.tvRating.visibility = View.VISIBLE
        } else {
            holder.tvRating.visibility = View.GONE
        }

        // 品质标签
        val desc = video.desc
        when {
            desc.contains("4K") -> { holder.tvQuality.text = "4K"; holder.tvQuality.visibility = View.VISIBLE }
            desc.contains("HD") || desc.contains("高清") -> { holder.tvQuality.text = "高清"; holder.tvQuality.visibility = View.VISIBLE }
            desc.contains("独播") -> { holder.tvQuality.text = "独播"; holder.tvQuality.visibility = View.VISIBLE }
            else -> holder.tvQuality.visibility = View.GONE
        }

        // 更新到XX集
        val remark = video.remark
        if (remark.isNotBlank() && (remark.contains("集") || remark.contains("期") || remark.contains("更新"))) {
            holder.tvUpdate.text = remark
            holder.tvUpdate.visibility = View.VISIBLE
        } else {
            holder.tvUpdate.visibility = View.GONE
        }

        holder.itemView.setOnClickListener { onClick(video) }
    }

    object Diff : DiffUtil.ItemCallback<Video>() {
        override fun areItemsTheSame(a: Video, b: Video) = a.id == b.id
        override fun areContentsTheSame(a: Video, b: Video) = a == b
    }
}
