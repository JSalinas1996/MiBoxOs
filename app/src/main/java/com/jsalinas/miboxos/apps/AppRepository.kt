package com.jsalinas.miboxos.apps

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.jsalinas.miboxos.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class TvApp(
    val packageName: String,
    val label: String,
    /** Banner 16:9 de Android TV; null si la app solo trae ícono (apps de celular). */
    val banner: ImageBitmap?,
    val icon: ImageBitmap?,
    /** Versión diminuta del banner/ícono: escalada a pantalla completa con filtrado queda como un desenfoque barato. */
    val backdrop: ImageBitmap?,
)

class AppRepository(private val context: Context, private val prefs: Prefs) {
    private val pm: PackageManager = context.packageManager

    suspend fun loadApps(): List<TvApp> = withContext(Dispatchers.IO) {
        val seen = mutableSetOf<String>()
        val result = mutableListOf<TvApp>()
        // Primero las apps de TV (con banner), después las de celular que no tengan versión TV
        for (category in listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)) {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(category)
            for (info in pm.queryIntentActivities(intent, 0)) {
                val pkg = info.activityInfo.packageName
                if (pkg == context.packageName || !seen.add(pkg)) continue
                val banner = info.activityInfo.loadBanner(pm)?.toBitmap(320, 180)
                val icon = info.loadIcon(pm)?.toBitmap(128, 128)
                val source = banner ?: icon
                result += TvApp(
                    packageName = pkg,
                    label = info.loadLabel(pm).toString(),
                    banner = banner?.asImageBitmap(),
                    icon = icon?.asImageBitmap(),
                    backdrop = source?.let { Bitmap.createScaledBitmap(it, 24, 14, true).asImageBitmap() },
                )
            }
        }
        result.sortedWith(
            compareByDescending<TvApp> { prefs.launchCount(it.packageName) }
                .thenBy { it.label.lowercase() }
        )
    }

    fun launch(packageName: String): Boolean {
        val intent = pm.getLeanbackLaunchIntentForPackage(packageName)
            ?: pm.getLaunchIntentForPackage(packageName)
            ?: return false
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        prefs.recordLaunch(packageName)
        return true
    }
}

private fun Drawable.toBitmap(width: Int, height: Int): Bitmap {
    if (this is BitmapDrawable && bitmap != null && bitmap.width == width && bitmap.height == height) return bitmap
    val w = if (intrinsicWidth > 0) intrinsicWidth.coerceAtMost(width * 2) else width
    val h = if (intrinsicHeight > 0) intrinsicHeight.coerceAtMost(height * 2) else height
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    setBounds(0, 0, w, h)
    draw(canvas)
    return bmp
}
