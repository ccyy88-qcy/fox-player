package com.foxplayer

import android.app.Application
import com.foxplayer.source.BuiltinSources
import com.foxplayer.source.SourceInitializer
import com.foxplayer.source.SourceManager
import com.foxplayer.util.ThemeHelper

class FoxApp : Application() {

    /** 全局源管理器 */
    val sourceManager: SourceManager by lazy {
        SourceManager().also { SourceInitializer.init(it) }
    }

    override fun onCreate() {
        super.onCreate()
        ThemeHelper.applyTheme(this)
    }
}
