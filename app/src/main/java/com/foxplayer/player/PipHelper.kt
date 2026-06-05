package com.foxplayer.player

import android.app.PictureInPictureParams
import android.content.pm.PackageManager
import android.os.Build
import android.util.Rational
import androidx.appcompat.app.AppCompatActivity

object PipHelper {
    fun enterPip(activity: AppCompatActivity, aspectRatio: Rational = Rational(16, 9)): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
        if (!activity.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) return false
        return try {
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(aspectRatio)
                .build()
            activity.enterPictureInPictureMode(params)
            true
        } catch (_: Exception) { false }
    }
}
