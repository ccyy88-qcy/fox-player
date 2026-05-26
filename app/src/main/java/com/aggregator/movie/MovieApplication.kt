package com.aggregator.movie

import android.app.Application
import androidx.room.Room
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
class MovieApplication : Application() {
    
    lateinit var repository: MovieRepository
        private set
    
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
        FfzyMovieSource(
            sourceId = "ffzy_02",
            sourceName = "非凡资源",
            priority = 5
        ),
        SuboMovieSource(
            sourceId = "subo_04",
            sourceName = "速播资源",
            priority = 9
        ),
    )
    
    companion object {
        lateinit var instance: MovieApplication
            private set
    }
}
