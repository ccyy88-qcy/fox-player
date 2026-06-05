package com.foxplayer

import android.app.Application
import com.foxplayer.util.ThemeHelper

class FoxApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ThemeHelper.applyTheme(this)
    }
}
