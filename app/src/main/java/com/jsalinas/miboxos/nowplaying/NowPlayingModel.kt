package com.jsalinas.miboxos.nowplaying

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

data class NowPlaying(
    val title: String,
    val subtitle: String,
    val appLabel: String,
    val artwork: ImageBitmap?,
    val playing: Boolean,
)

/**
 * "Ahora suena" del Centro de control: lo que se reproduce en cualquier app (Spotify, YouTube,
 * Netflix…) leído de las sesiones multimedia del sistema, con play/pausa y anterior/siguiente.
 */
class NowPlayingModel(private val context: Context) {
    var current by mutableStateOf<NowPlaying?>(null)
        private set

    private var controller: MediaController? = null
    private val listener = ComponentName(context, MediaListenerService::class.java)

    private val callback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) = publish()
        override fun onMetadataChanged(metadata: MediaMetadata?) = publish()
        override fun onSessionDestroyed() = refresh()
    }

    fun hasAccess(): Boolean =
        context.getSystemService(NotificationManager::class.java).isNotificationListenerAccessGranted(listener)

    /** Busca la sesión activa (la que está sonando, o la última que sonó). */
    fun refresh() {
        controller?.unregisterCallback(callback)
        controller = null
        if (!hasAccess()) {
            current = null
            return
        }
        val sessions = runCatching {
            context.getSystemService(MediaSessionManager::class.java).getActiveSessions(listener)
        }.getOrDefault(emptyList())
        controller = sessions.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING } ?: sessions.firstOrNull()
        controller?.registerCallback(callback)
        publish()
    }

    fun playPause() {
        val c = controller ?: return
        if (c.playbackState?.state == PlaybackState.STATE_PLAYING) c.transportControls.pause() else c.transportControls.play()
    }

    fun next() { controller?.transportControls?.skipToNext() }

    fun previous() { controller?.transportControls?.skipToPrevious() }

    private fun publish() {
        val c = controller
        val meta = c?.metadata
        if (c == null || meta == null) {
            current = null
            return
        }
        val title = meta.getString(MediaMetadata.METADATA_KEY_TITLE) ?: meta.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
        if (title.isNullOrBlank()) {
            current = null
            return
        }
        val subtitle = meta.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: meta.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE)
            ?: meta.getString(MediaMetadata.METADATA_KEY_ALBUM).orEmpty()
        val art: Bitmap? = meta.getBitmap(MediaMetadata.METADATA_KEY_ART) ?: meta.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
        val label = runCatching {
            context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(c.packageName, 0)).toString()
        }.getOrDefault("")
        current = NowPlaying(
            title = title,
            subtitle = subtitle,
            appLabel = label,
            artwork = art?.let { Bitmap.createScaledBitmap(it, 160, (160f * it.height / it.width).toInt().coerceAtLeast(1), true).asImageBitmap() },
            playing = c.playbackState?.state == PlaybackState.STATE_PLAYING,
        )
    }
}
