package com.jsalinas.miboxos.apps

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import androidx.compose.ui.graphics.asImageBitmap
import kotlin.math.cos
import kotlin.math.sin

/** "Apps" propias del launcher que viven en la grilla, como Ajustes en tvOS. */
object SystemTiles {
    const val SETTINGS = "miboxos.settings"
    const val ASSISTANT = "miboxos.assistant"

    fun settings() = TvApp(SETTINGS, "Ajustes", tile { c, w, h ->
        c.drawPaint(Paint().apply { shader = LinearGradient(0f, 0f, 0f, h, 0xFF9A9AA0.toInt(), 0xFF5A5A5F.toInt(), Shader.TileMode.CLAMP) })
        drawGear(c, w / 2, h / 2, h * 0.36f)
    }.asImageBitmap())

    fun assistant() = TvApp(ASSISTANT, "Asistente", tile { c, w, h ->
        c.drawColor(0xFF0B0B0F.toInt())
        val colors = intArrayOf(0xFF5E5CE6.toInt(), 0xFFBF5AF2.toInt(), 0xFFFF375F.toInt(), 0xFF0A84FF.toInt())
        val r = h * 0.30f
        colors.forEachIndexed { i, color ->
            val a = i * Math.PI / 2 + 0.6
            val cx = w / 2 + (cos(a) * r * 0.32).toFloat()
            val cy = h / 2 + (sin(a) * r * 0.32).toFloat()
            c.drawCircle(cx, cy, r, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = RadialGradient(cx, cy, r, color, color and 0x00FFFFFF, Shader.TileMode.CLAMP)
                xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SCREEN)
            })
        }
    }.asImageBitmap())

    private fun tile(draw: (Canvas, Float, Float) -> Unit): Bitmap {
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        draw(Canvas(bmp), W.toFloat(), H.toFloat())
        return bmp
    }

    /** Engranaje de 12 dientes, gris claro con el centro hueco. */
    fun drawGear(c: Canvas, cx: Float, cy: Float, radius: Float) {
        val teeth = 12
        val path = Path()
        for (i in 0 until teeth * 4) {
            val angle = Math.PI * 2 * i / (teeth * 4) - Math.PI / 2
            val r = if ((i / 2) % 2 == 0) radius else radius * 0.82f
            val x = cx + (cos(angle) * r).toFloat()
            val y = cy + (sin(angle) * r).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x33000000 }
        c.save(); c.translate(0f, radius * 0.04f); c.drawPath(path, shadow); c.restore()
        c.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, cy - radius, 0f, cy + radius, 0xFFF2F2F7.toInt(), 0xFFC7C7CC.toInt(), Shader.TileMode.CLAMP)
        })
        c.drawCircle(cx, cy, radius * 0.52f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF6E6E73.toInt() })
        c.drawCircle(cx, cy, radius * 0.36f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, cy - radius, 0f, cy + radius, 0xFFE5E5EA.toInt(), 0xFFAEAEB2.toInt(), Shader.TileMode.CLAMP)
        })
        c.drawCircle(cx, cy, radius * 0.16f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF636366.toInt() })
    }

    private const val W = 320
    private const val H = 180
}
