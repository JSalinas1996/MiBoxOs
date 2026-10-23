package com.jsalinas.miboxos.wallpaper

import android.graphics.Bitmap

/**
 * Desenfoque del fondo para el "material" detrás de la grilla, como en tvOS.
 * Android 9 no tiene RenderEffect: se achica la imagen y se aplica un box blur de 3 pasadas
 * (aproxima un gaussiano). Al mostrarse escalada con filtrado queda suave y cuesta muy poca memoria.
 */
object Blur {
    fun frosted(src: Bitmap, width: Int = 192, height: Int = 108, radius: Int = 5): Bitmap {
        val small = Bitmap.createScaledBitmap(src, width, height, true)
        val px = IntArray(width * height)
        small.getPixels(px, 0, width, 0, 0, width, height)
        repeat(3) {
            boxPass(px, width, height, radius, horizontal = true)
            boxPass(px, width, height, radius, horizontal = false)
        }
        return Bitmap.createBitmap(px, width, height, Bitmap.Config.ARGB_8888)
    }

    private fun boxPass(px: IntArray, w: Int, h: Int, r: Int, horizontal: Boolean) {
        val lines = if (horizontal) h else w
        val len = if (horizontal) w else h
        val line = IntArray(len)
        for (l in 0 until lines) {
            for (i in 0 until len) line[i] = px[index(l, i, w, horizontal)]
            var sr = 0; var sg = 0; var sb = 0
            for (k in -r..r) {
                val c = line[k.coerceIn(0, len - 1)]
                sr += c shr 16 and 0xFF; sg += c shr 8 and 0xFF; sb += c and 0xFF
            }
            val n = 2 * r + 1
            for (i in 0 until len) {
                px[index(l, i, w, horizontal)] = (0xFF shl 24) or ((sr / n) shl 16) or ((sg / n) shl 8) or (sb / n)
                val out = line[(i - r).coerceIn(0, len - 1)]
                val inn = line[(i + r + 1).coerceIn(0, len - 1)]
                sr += (inn shr 16 and 0xFF) - (out shr 16 and 0xFF)
                sg += (inn shr 8 and 0xFF) - (out shr 8 and 0xFF)
                sb += (inn and 0xFF) - (out and 0xFF)
            }
        }
    }

    private fun index(line: Int, i: Int, w: Int, horizontal: Boolean) = if (horizontal) line * w + i else i * w + line
}
