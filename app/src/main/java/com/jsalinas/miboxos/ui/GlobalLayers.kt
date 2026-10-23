package com.jsalinas.miboxos.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.jsalinas.miboxos.MiBoxApp
import com.jsalinas.miboxos.screensaver.Screensaver
import com.jsalinas.miboxos.screensaver.screensaverImages

/**
 * Lo que puede aparecer encima de cualquier pantalla: modo reposo, Centro de control y asistente.
 * Lo dibuja el servicio global (sobre cualquier app) o, si no está activado, el launcher.
 */
@Composable
fun GlobalLayers(app: MiBoxApp, onAssistant: () -> Unit, onSettings: () -> Unit) {
    val cc = app.controlCenter
    Box(Modifier.fillMaxSize()) {
        if (cc.screensaverVisible) {
            val images = remember { screensaverImages(app.wallpapers, app.prefs) }
            Screensaver(images, app.wallpapers::loadFull, app.prefs.screensaverSeconds)
        }
        ControlCenter(
            visible = cc.visible,
            state = ControlCenterState(
                darkMode = app.darkMode,
                huePaired = app.hue.isPaired,
                rooms = cc.rooms,
                scenes = cc.scenes,
                nowPlaying = app.nowPlaying.current,
                sleepTimer = app.sleep.timerMinutes,
            ),
            actions = ControlCenterActions(
                onToggleRoom = cc::toggleRoom,
                onScene = cc::scene,
                onAssistant = { cc.close(); onAssistant() },
                onScreensaver = { cc.close(); cc.screensaverVisible = true },
                onDarkMode = { app.darkMode = it; app.prefs.darkMode = it },
                onSettings = { cc.close(); onSettings() },
                onPlayPause = app.nowPlaying::playPause,
                onNext = app.nowPlaying::next,
                onPrevious = app.nowPlaying::previous,
                onSleepNow = {
                    val error = app.sleep.sleepNow()
                    if (error != null) app.assistant.fail(error) else cc.close()
                },
                onCycleTimer = app.sleep::cycleTimer,
            ),
        )
        AssistantOverlay(app.assistant.state, app.assistant.level)
    }
}
