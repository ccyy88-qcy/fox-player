package com.aggregator.movie

import android.app.Application
import androidx.room.Room
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.aggregator.movie.data.api.MovieSource
import com.aggregator.movie.data.api.SourceManager
import com.aggregator.movie.data.api.ZuidaMovieSource
import com.aggregator.movie.data.api.FfzyMovieSource
import com.aggregator.movie.data.api.SuboMovieSource
import com.aggregator.movie.data.local.MovieDatabase
import com.aggregator.movie.data.repository.MovieRepository

/**
 * Application - 全局单例
 */
class MovieApplication : Application(), ImageLoaderFactory {
    
    lateinit var repository: MovieRepository
        private set
    
    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)  // 25% of app memory for image cache
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("coil_cache"))
                    .maxSizeBytes(100 * 1024 * 1024)  // 100MB disk cache
                    .build()
            }
            .crossfade(true)
            .build()
    }
    
    override fun onCreate() {
        super.onCreate()
        instance = this
        
        val database = Room.databaseBuilder(
            applicationContext,
            MovieDatabase::class.java,
            "movie_db"
        ).build()
        
        val sources = createSources()
        val sourceManager = SourceManager(sources)
        repository = MovieRepository(sourceManager, database.movieDao())
    }
    
    private fun createSources(): List<MovieSource> = listOf(
        ZuidaMovieSource(
            sourceId = "zuida_01",
            sourceName = "最大资源",
            priority = 10
        ),
        SuboMovieSource(
            sourceId = "subo_04",
            sourceName = "速播资源",
            priority = 9
        ),
        FfzyMovieSource(
            sourceId = "ffzy_02",
            sourceName = "非凡资源",
            priority = 5
        ),
    )
    
    companion object {
        lateinit var instance: MovieApplication
            private set
    }
}
