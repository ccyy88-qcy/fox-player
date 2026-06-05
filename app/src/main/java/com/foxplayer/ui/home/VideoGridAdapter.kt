package com.foxplayer.ui.home

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.foxplayer.databinding.ItemVideoBinding
import com.foxplayer.model.Video

class VideoGridAdapter(private val onClick: (Video) -> Unit) :
    ListAdapter<Video, VideoGridAdapter.VH>(DIFF) {

    inner class VH(val binding: ItemVideoBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemVideoBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, pos: Int) {
        val v = getItem(pos)
        holder.binding.apply {
            tvTitle.text = v.title
            tvRating.text = if (v.rating > 0) "⭐ %.1f".format(v.rating) else ""
            ivCover.load(v.cover) {
                crossfade(true)
                placeholder(com.foxplayer.R.drawable.ic_placeholder)
                error(com.foxplayer.R.drawable.ic_placeholder)
            }
            root.setOnClickListener { onClick(v) }
        }
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<Video>() {
            override fun areItemsTheSame(a: Video, b: Video) = a.id == b.id
            override fun areContentsTheSame(a: Video, b: Video) = a == b
        }
    }
}
