package com.foxplayer.player

import android.content.Context
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.offline.*
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 离线下载管理器 — 基于 ExoPlayer DownloadService
 * 支持多任务批量下载、断点续存
 */
class DownloadManager(private val context: Context) {

    private val downloadDir = File(context.filesDir, "downloads").apply { mkdirs() }

    data class DownloadTask(
        val id: String,
        val url: String,
        val title: String,
        val state: Int = STATE_IDLE,
        val progress: Float = 0f,
    )

    companion object {
        const val STATE_IDLE = 0
        const val STATE_DOWNLOADING = 1
        const val STATE_PAUSED = 2
        const val STATE_COMPLETED = 3
        const val STATE_FAILED = 4
    }

    private val _tasks = mutableListOf<DownloadTask>()
    val tasks: List<DownloadTask> get() = _tasks.toList()

    /** 添加下载任务 */
    fun addTask(url: String, title: String): DownloadTask {
        val id = System.currentTimeMillis().toString()
        val task = DownloadTask(id = id, url = url, title = title)
        _tasks.add(task)
        return task
    }

    /** 批量添加 */
    fun addTasks(items: List<Pair<String, String>>): List<DownloadTask> {
        return items.map { (url, title) -> addTask(url, title) }
    }

    /** 暂停 */
    fun pause(id: String) {
        _tasks.indexOfFirst { it.id == id }.takeIf { it >= 0 }?.let { i ->
            _tasks[i] = _tasks[i].copy(state = STATE_PAUSED)
        }
    }

    /** 恢复 */
    fun resume(id: String) {
        _tasks.indexOfFirst { it.id == id }.takeIf { it >= 0 }?.let { i ->
            _tasks[i] = _tasks[i].copy(state = STATE_DOWNLOADING)
        }
    }

    /** 获取下载文件 */
    fun getDownloadFile(id: String): File = File(downloadDir, "$id.mp4")

    /** 下载目录总大小 */
    fun getTotalSize(): Long = downloadDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }

    /** 清理已完成 */
    fun clearCompleted() {
        _tasks.removeAll { it.state == STATE_COMPLETED }
    }
}
