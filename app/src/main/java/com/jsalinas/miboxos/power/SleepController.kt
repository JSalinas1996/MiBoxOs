package com.jsalinas.miboxos.power

import android.accessibilityservice.AccessibilityService
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.jsalinas.miboxos.GlobalAssistant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Apagar ahora y temporizador. Una app sin permisos de sistema no puede apagar la Mi Box: se usa
 * la acción "bloquear pantalla" que Android 9 le da al servicio de accesibilidad, que en Android TV
 * suspende el equipo (y con HDMI-CEC activado, la TV). Requiere el asistente en cualquier app activo.
 */
class SleepController(private val scope: CoroutineScope) {
    /** Minutos del temporizador activo, o null. */
    var timerMinutes by mutableStateOf<Int?>(null)
        private set

    private var job: Job? = null

    val available: Boolean get() = GlobalAssistant.connected

    /** Devuelve null si se pudo, o el motivo. */
    fun sleepNow(): String? {
        val perform = GlobalAssistant.globalAction ?: return "Para apagar, activá el asistente en cualquier app (Ajustes → Asistente)"
        cancelTimer()
        return if (perform(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN)) null else "No se pudo apagar"
    }

    fun startTimer(minutes: Int) {
        job?.cancel()
        timerMinutes = minutes
        job = scope.launch {
            delay(minutes * 60_000L)
            timerMinutes = null
            sleepNow()
        }
    }

    fun cancelTimer() {
        job?.cancel()
        timerMinutes = null
    }

    /** Para el Centro de control: no → 15 → 30 → 60 → 90 → no. */
    fun cycleTimer() {
        val next = when (timerMinutes) { null -> 15; 15 -> 30; 30 -> 60; 60 -> 90; else -> null }
        if (next == null) cancelTimer() else startTimer(next)
    }
}
