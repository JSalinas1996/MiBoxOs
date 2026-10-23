package com.jsalinas.miboxos.nowplaying

import android.app.Application
import android.app.NotificationManager
import android.content.ComponentName
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class NowPlayingModelTest {
    private val app: Application = ApplicationProvider.getApplicationContext()
    private val model = NowPlayingModel(app)
    private val listener = ComponentName(app, MediaListenerService::class.java)

    private fun playing(title: String, artist: String, state: Int): MediaController {
        val controller = MediaController(app, MediaSession(app, "test").sessionToken)
        shadowOf(controller).setMetadata(
            MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_TITLE, title).putString(MediaMetadata.METADATA_KEY_ARTIST, artist).build()
        )
        shadowOf(controller).setPlaybackState(PlaybackState.Builder().setState(state, 0, 1f).build())
        shadowOf(app.getSystemService(MediaSessionManager::class.java)).addController(controller)
        return controller
    }

    @Test fun `sin acceso no muestra nada`() {
        playing("Canción", "Artista", PlaybackState.STATE_PLAYING)
        model.refresh()
        assertFalse(model.hasAccess())
        assertNull(model.current)
    }

    @Test fun `con acceso muestra lo que suena, priorizando lo que esta reproduciendo`() {
        shadowOf(app.getSystemService(NotificationManager::class.java)).setNotificationListenerAccessGranted(listener, true)
        playing("Pausada", "Otra", PlaybackState.STATE_PAUSED)
        playing("Bohemian Rhapsody", "Queen", PlaybackState.STATE_PLAYING)
        model.refresh()
        val np = model.current!!
        assertEquals("Bohemian Rhapsody", np.title)
        assertEquals("Queen", np.subtitle)
        assertTrue(np.playing)
    }
}
