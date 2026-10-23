package com.jsalinas.miboxos.global

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.snapshotFlow
import com.jsalinas.miboxos.GlobalAssistant
import com.jsalinas.miboxos.MainActivity
import com.jsalinas.miboxos.MiBoxApp
import com.jsalinas.miboxos.assistant.AssistantState
import com.jsalinas.miboxos.ui.GlobalLayers
import com.jsalinas.miboxos.ui.LocalColors
import com.jsalinas.miboxos.ui.appColors
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Asistente en cualquier app. Como servicio de accesibilidad recibe las teclas del control antes que
 * el sistema: el botón de voz (que antes abría Google Assistant) abre el asistente con Gemini encima
 * de lo que se esté viendo; mantenido apretado abre el Centro de control.
 */
class GlobalAssistantService : AccessibilityService() {
    private val app get() = application as MiBoxApp
    private val handler = Handler(Looper.getMainLooper())
    private var overlay: OverlayHost? = null
    private var focusJob: Job? = null
    private var voiceDown = false
    private var longPressFired = false
    private val longPress = Runnable {
        if (voiceDown) {
            longPressFired = true
            app.assistant.dismiss()
            app.controlCenter.open()
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        GlobalAssistant.globalAction = { performGlobalAction(it) }
        GlobalAssistant.connected = true
        if (!renderOverlay) return
        overlay = OverlayHost(this) {
            CompositionLocalProvider(LocalColors provides appColors(app.darkMode, photoWallpaper = false)) {
                GlobalLayers(app, onAssistant = ::startAssistant, onSettings = ::openSettings)
            }
        }
        // La capa toma el foco solo mientras haya algo navegable con el control
        focusJob = app.scope.launch {
            snapshotFlow { app.controlCenter.visible || app.controlCenter.screensaverVisible }
                .collect { overlay?.setFocusable(it) }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName || pkg == "com.android.systemui") return
        GlobalAssistant.foregroundLabel = runCatching {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
        }.getOrNull()
    }

    override fun onInterrupt() = Unit

    // Público para poder simular el control en tests
    public override fun onKeyEvent(event: KeyEvent): Boolean {
        val prefs = app.prefs
        if (AssistantKeys.learn(prefs, event)) return true
        val cc = app.controlCenter

        // Modo reposo: cualquier botón lo cierra y no hace nada más
        if (cc.screensaverVisible) {
            if (event.action == KeyEvent.ACTION_UP) cc.screensaverVisible = false
            return true
        }
        if (AssistantKeys.isAssistantKey(prefs, event.keyCode)) {
            onVoiceKey(event)
            return true
        }
        // Botón de apps del control Xiaomi (KEY_APPSELECT → APP_SWITCH): el sistema lo manda al inicio de
        // Xiaomi antes de que llegue a cualquier app, así que se toma acá para el Centro de control
        if (event.keyCode == KeyEvent.KEYCODE_APP_SWITCH) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                if (cc.visible) cc.close() else {
                    app.assistant.dismiss()
                    cc.open()
                }
            }
            return true
        }
        if (event.keyCode == KeyEvent.KEYCODE_BACK) {
            when {
                app.assistant.state != AssistantState.Idle -> {
                    if (event.action == KeyEvent.ACTION_UP) app.assistant.dismiss()
                    return true
                }
                cc.visible -> {
                    if (event.action == KeyEvent.ACTION_UP) cc.close()
                    return true
                }
            }
        }
        return false
    }

    /** Toque corto: asistente. Mantenido (0,6 s): Centro de control. */
    private fun onVoiceKey(event: KeyEvent) {
        when (event.action) {
            KeyEvent.ACTION_DOWN -> if (event.repeatCount == 0) {
                voiceDown = true
                longPressFired = false
                handler.postDelayed(longPress, LONG_PRESS_MS)
            }
            KeyEvent.ACTION_UP -> {
                voiceDown = false
                handler.removeCallbacks(longPress)
                // Si el temporizador no llegó a dispararse pero se mantuvo apretado, cuenta igual como "mantener"
                if (!longPressFired && event.eventTime - event.downTime >= LONG_PRESS_MS) {
                    longPressFired = true
                    app.assistant.dismiss()
                    app.controlCenter.open()
                }
                if (!longPressFired) {
                    if (app.controlCenter.visible) app.controlCenter.close()
                    startAssistant()
                }
            }
        }
    }

    private fun startAssistant() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            app.assistant.fail("Falta el permiso de micrófono: abrí MiBoxOs y elegí el ícono Asistente una vez para darlo.")
            return
        }
        app.assistant.toggle()
    }

    private fun openSettings() {
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra(MainActivity.EXTRA_OPEN_SETTINGS, true))
    }

    override fun onUnbind(intent: Intent?): Boolean {
        shutdown()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        shutdown()
        super.onDestroy()
    }

    private fun shutdown() {
        handler.removeCallbacks(longPress)
        focusJob?.cancel()
        overlay?.destroy()
        overlay = null
        GlobalAssistant.connected = false
        GlobalAssistant.globalAction = null
    }

    companion object {
        const val LONG_PRESS_MS = 600L

        /** Los tests de teclas no dibujan la capa (Robolectric no avanza el reloj con animaciones infinitas). */
        @androidx.annotation.VisibleForTesting
        var renderOverlay = true
    }
}
