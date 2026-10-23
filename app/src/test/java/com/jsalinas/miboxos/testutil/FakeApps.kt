package com.jsalinas.miboxos.testutil

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import org.robolectric.Shadows.shadowOf

/** Instala apps falsas en el PackageManager de Robolectric. */
object FakeApps {
    fun install(context: Context, pkg: String, label: String, tv: Boolean = true) {
        val pm = shadowOf(context.packageManager)
        val component = ComponentName(pkg, "$pkg.Main")
        val app = ApplicationInfo().apply {
            packageName = pkg
            name = label
            nonLocalizedLabel = label
            flags = ApplicationInfo.FLAG_INSTALLED
            enabled = true
        }
        pm.installPackage(PackageInfo().apply { packageName = pkg; applicationInfo = app })
        val activity = ActivityInfo().apply {
            packageName = pkg
            name = component.className
            nonLocalizedLabel = label
            applicationInfo = app
            exported = true
            enabled = true
        }
        pm.addOrUpdateActivity(activity)
        val category = if (tv) Intent.CATEGORY_LEANBACK_LAUNCHER else Intent.CATEGORY_LAUNCHER
        pm.addIntentFilterForActivity(component, IntentFilter(Intent.ACTION_MAIN).apply { addCategory(category) })
    }

    fun installDefaults(context: Context) {
        install(context, "com.netflix.ninja", "Netflix")
        install(context, "com.google.android.youtube.tv", "YouTube")
        install(context, "com.spotify.tv.android", "Spotify")
        install(context, "com.example.phoneonly", "Fotos", tv = false)
    }
}
