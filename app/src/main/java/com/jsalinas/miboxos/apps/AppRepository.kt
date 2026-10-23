package com.jsalinas.miboxos.apps

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.jsalinas.miboxos.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class TvApp(
    val packageName: String,
    val label: String,
    /** Arte 16:9 con el logo: el banner oficial de Android TV o uno armado desde el ícono. */
    val tile: ImageBitmap,
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
                val tile = info.activityInfo.loadBanner(pm)?.render(TILE_W, TILE_H)
                    ?: tileFromIcon(info.loadIcon(pm))
                result += TvApp(pkg, info.loadLabel(pm).toString(), tile.asImageBitmap())
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

    /**
     * Apps sin banner (versiones de celular): con ícono adaptativo, la capa de fondo cubre la tarjeta
     * y el logo va centrado, como en tvOS. Con ícono clásico, el ícono va sobre el color de su borde.
     */
    private fun tileFromIcon(icon: Drawable?): Bitmap {
        val bmp = Bitmap.createBitmap(TILE_W, TILE_H, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        if (icon is AdaptiveIconDrawable) {
            icon.background?.apply { setBounds(0, 0, TILE_W, TILE_H); draw(canvas) }
            // La capa frontal mide 108dp y el logo ocupa los 72dp centrales: se dibuja a 1.5x la altura
            val size = (TILE_H * 1.5f).toInt()
            val left = (TILE_W - size) / 2
            val top = (TILE_H - size) / 2
            icon.foreground?.apply { setBounds(left, top, left + size, top + size); draw(canvas) }
        } else if (icon != null) {
            val small = icon.render(96, 96)
            canvas.drawColor(edgeColor(small))
            canvas.drawBitmap(small, (TILE_W - 96) / 2f, (TILE_H - 96) / 2f, null)
        } else {
            canvas.drawColor(Color.rgb(44, 44, 46))
        }
        return bmp
    }

    /** Color del borde del ícono; si es transparente, gris oscuro neutro. */
    private fun edgeColor(icon: Bitmap): Int {
        val px = icon.getPixel(icon.width / 2, 2)
        return if (Color.alpha(px) > 200) px else Color.rgb(44, 44, 46)
    }

    private companion object {
        const val TILE_W = 320
        const val TILE_H = 180
    }
}

private fun Drawable.render(width: Int, height: Int): Bitmap {
    val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    setBounds(0, 0, width, height)
    draw(Canvas(bmp))
    return bmp
}
