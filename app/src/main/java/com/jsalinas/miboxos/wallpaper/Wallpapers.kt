package com.jsalinas.miboxos.wallpaper

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Environment
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class Wallpaper(
    val id: String,
    val name: String,
    /** null = fondo minimalista generado (degradé según el modo claro/oscuro). */
    val source: Source?,
) {
    sealed interface Source
    data class Asset(val file: String, val thumb: String) : Source
    data class UserFile(val file: File) : Source
}

/** Fondos incluidos (NASA, dominio público; ver docs/WALLPAPER_CREDITS.md) + imágenes propias del usuario. */
class Wallpapers(private val context: Context) {

    /** Carpeta para fondos propios: se copian con `adb push foto.jpg /sdcard/Pictures/MiBoxOs/`. */
    val userFolder: File =
        File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "MiBoxOs")

    fun all(): List<Wallpaper> = BUILT_IN + userWallpapers()

    fun byId(id: String): Wallpaper = all().firstOrNull { it.id == id } ?: BUILT_IN.first()

    private fun userWallpapers(): List<Wallpaper> {
        val files = runCatching { userFolder.listFiles() }.getOrNull() ?: return emptyList()
        return files
            .filter { it.isFile && it.extension.lowercase() in setOf("jpg", "jpeg", "png", "webp") }
            .sortedBy { it.name.lowercase() }
            .map { Wallpaper("file:${it.absolutePath}", it.nameWithoutExtension, Wallpaper.UserFile(it)) }
    }

    suspend fun loadFull(wallpaper: Wallpaper): ImageBitmap? = load(wallpaper, 1920, 1080, thumb = false)

    /** Versión desenfocada para el material de la grilla; null para "Minimal". */
    suspend fun loadFrosted(wallpaper: Wallpaper): ImageBitmap? = withContext(Dispatchers.IO) {
        runCatching {
            when (val src = wallpaper.source) {
                null -> null
                is Wallpaper.Asset -> context.assets.open(src.thumb).use { BitmapFactory.decodeStream(it) }
                is Wallpaper.UserFile -> decodeSampled(src.file, 480, 270)
            }?.let { Blur.frosted(it).asImageBitmap() }
        }.getOrNull()
    }

    suspend fun loadThumb(wallpaper: Wallpaper): ImageBitmap? = load(wallpaper, 480, 270, thumb = true)

    private suspend fun load(wallpaper: Wallpaper, w: Int, h: Int, thumb: Boolean): ImageBitmap? =
        withContext(Dispatchers.IO) {
            runCatching {
                when (val src = wallpaper.source) {
                    null -> null
                    is Wallpaper.Asset -> context.assets.open(if (thumb) src.thumb else src.file).use {
                        BitmapFactory.decodeStream(it)
                    }
                    is Wallpaper.UserFile -> decodeSampled(src.file, w, h)
                }?.asImageBitmap()
            }.getOrNull()
        }

    /** Decodifica achicando en potencias de 2: una foto 4K a tamaño completo ocuparía ~33 MB de RAM. */
    private fun decodeSampled(file: File, w: Int, h: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= w && bounds.outHeight / (sample * 2) >= h) sample *= 2
        return BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    private companion object {
        fun asset(id: String, name: String) =
            Wallpaper(id, name, Wallpaper.Asset("wallpapers/$id.webp", "wallpapers/${id}_thumb.webp"))

        val BUILT_IN = listOf(
            Wallpaper("minimal", "Minimal", null),
            asset("tierra", "Tierra"),
            asset("tierra_desde_la_luna", "Tierra desde la Luna"),
            asset("luna", "Luna"),
            asset("saturno", "Saturno"),
            asset("nebulosa_carina", "Nebulosa de Carina"),
            asset("aurora", "Aurora"),
            asset("luces_nocturnas", "Luces nocturnas"),
        )
    }
}
