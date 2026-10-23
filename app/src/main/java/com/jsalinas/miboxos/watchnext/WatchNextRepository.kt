package com.jsalinas.miboxos.watchnext

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.tv.TvContract
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

data class WatchNextItem(
    val id: Long,
    val packageName: String,
    val title: String,
    /** "T2 E5 · Título del capítulo", o vacío. */
    val subtitle: String,
    val intent: Intent?,
    /** 0..1, o null si la app no informa la duración. */
    val progress: Float?,
    val image: ImageBitmap?,
    val lastEngagement: Long,
)

/**
 * "Seguir viendo": la fila Watch Next que publican las apps (Netflix, YouTube, etc.) en Android TV.
 * Con el permiso READ_TV_LISTINGS el sistema deja leer las entradas "searchable" de todas las apps.
 * Qué apps publican algo depende de cada app: si ninguna lo hace, la lista queda vacía.
 */
class WatchNextRepository(private val context: Context) {

    fun hasPermission() = context.checkSelfPermission(PERMISSION) == PackageManager.PERMISSION_GRANTED

    suspend fun load(limit: Int = 12): List<WatchNextItem> = withContext(Dispatchers.IO) {
        if (!hasPermission()) return@withContext emptyList()
        val cursor = runCatching {
            context.contentResolver.query(TvContract.WatchNextPrograms.CONTENT_URI, PROJECTION, null, null, null)
        }.getOrNull() ?: return@withContext emptyList()
        val rows = cursor.use { c -> buildList { while (c.moveToNext()) parse(c)?.let(::add) } }
        rows.sortedByDescending { it.second }.take(limit).map { (row, engagement) -> row.copy(lastEngagement = engagement, image = loadImage(row.imageUri)).toItem() }
    }

    private data class Row(
        val id: Long, val pkg: String, val title: String, val subtitle: String, val intent: Intent?,
        val progress: Float?, val imageUri: String?, val lastEngagement: Long = 0, val image: ImageBitmap? = null,
    ) {
        fun toItem() = WatchNextItem(id, pkg, title, subtitle, intent, progress, image, lastEngagement)
    }

    private fun parse(c: Cursor): Pair<Row, Long>? {
        fun str(col: String) = c.getColumnIndex(col).takeIf { it >= 0 }?.let { c.getString(it) }
        fun long(col: String) = c.getColumnIndex(col).takeIf { it >= 0 && !c.isNull(it) }?.let { c.getLong(it) }
        if (long(TvContract.WatchNextPrograms.COLUMN_BROWSABLE) == 0L) return null
        val title = str(TvContract.WatchNextPrograms.COLUMN_TITLE)?.takeIf { it.isNotBlank() } ?: return null
        val season = str(TvContract.WatchNextPrograms.COLUMN_SEASON_DISPLAY_NUMBER)
        val episode = str(TvContract.WatchNextPrograms.COLUMN_EPISODE_DISPLAY_NUMBER)
        val episodeTitle = str(TvContract.WatchNextPrograms.COLUMN_EPISODE_TITLE)
        val numbers = listOfNotNull(season?.let { "T$it" }, episode?.let { "E$it" }).joinToString(" ")
        val subtitle = listOf(numbers, episodeTitle.orEmpty()).filter { it.isNotBlank() }.joinToString(" · ")
        val position = long(TvContract.WatchNextPrograms.COLUMN_LAST_PLAYBACK_POSITION_MILLIS)
        val duration = long(TvContract.WatchNextPrograms.COLUMN_DURATION_MILLIS)
        val progress = if (position != null && duration != null && duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else null
        val intent = str(TvContract.WatchNextPrograms.COLUMN_INTENT_URI)?.let {
            runCatching { Intent.parseUri(it, Intent.URI_INTENT_SCHEME) }.getOrNull()
        }
        val row = Row(
            id = long(TvContract.WatchNextPrograms._ID) ?: 0,
            pkg = str(TvContract.WatchNextPrograms.COLUMN_PACKAGE_NAME).orEmpty(),
            title = title,
            subtitle = subtitle,
            intent = intent,
            progress = progress,
            imageUri = str(TvContract.WatchNextPrograms.COLUMN_THUMBNAIL_URI) ?: str(TvContract.WatchNextPrograms.COLUMN_POSTER_ART_URI),
        )
        return row to (long(TvContract.WatchNextPrograms.COLUMN_LAST_ENGAGEMENT_TIME_UTC_MILLIS) ?: 0)
    }

    /** Imagen 16:9 de la entrada (http o content://), achicada para no gastar memoria. */
    private fun loadImage(uri: String?): ImageBitmap? = runCatching {
        if (uri.isNullOrBlank()) return null
        val bytes = if (uri.startsWith("http")) {
            val conn = URL(uri).openConnection() as HttpURLConnection
            conn.connectTimeout = 4_000
            conn.readTimeout = 6_000
            try { conn.inputStream.use { it.readBytes() } } finally { conn.disconnect() }
        } else {
            context.contentResolver.openInputStream(Uri.parse(uri))?.use { it.readBytes() }
        } ?: return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= 480) sample *= 2
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
            ?.let { Bitmap.createScaledBitmap(it, 480, (480f * it.height / it.width).toInt().coerceAtLeast(1), true) }
            ?.asImageBitmap()
    }.getOrNull()

    companion object {
        // Lo define el proveedor de TV del sistema (no está en la API pública del SDK)
        const val PERMISSION = "android.permission.READ_TV_LISTINGS"

        private val PROJECTION = arrayOf(
            TvContract.WatchNextPrograms._ID,
            TvContract.WatchNextPrograms.COLUMN_PACKAGE_NAME,
            TvContract.WatchNextPrograms.COLUMN_TITLE,
            TvContract.WatchNextPrograms.COLUMN_EPISODE_TITLE,
            TvContract.WatchNextPrograms.COLUMN_SEASON_DISPLAY_NUMBER,
            TvContract.WatchNextPrograms.COLUMN_EPISODE_DISPLAY_NUMBER,
            TvContract.WatchNextPrograms.COLUMN_POSTER_ART_URI,
            TvContract.WatchNextPrograms.COLUMN_THUMBNAIL_URI,
            TvContract.WatchNextPrograms.COLUMN_INTENT_URI,
            TvContract.WatchNextPrograms.COLUMN_LAST_PLAYBACK_POSITION_MILLIS,
            TvContract.WatchNextPrograms.COLUMN_DURATION_MILLIS,
            TvContract.WatchNextPrograms.COLUMN_LAST_ENGAGEMENT_TIME_UTC_MILLIS,
            TvContract.WatchNextPrograms.COLUMN_BROWSABLE,
        )
    }
}
