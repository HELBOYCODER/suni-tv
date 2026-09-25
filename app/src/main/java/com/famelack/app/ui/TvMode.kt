package com.famelack.app.ui

import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.runtime.staticCompositionLocalOf

/** Runtime Android TV (leanback) detection. The manifest already declares the
 *  LEANBACK_LAUNCHER filter; this tells the Compose tree which UX to render. */
object TvMode {
    fun isTvMode(context: Context): Boolean =
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
}

/** Provides `true` when running on an Android TV (leanback) device.
 *  Provided by MainActivity; all TV-specific behaviour must be gated on it. */
val LocalIsTv = staticCompositionLocalOf { false }
