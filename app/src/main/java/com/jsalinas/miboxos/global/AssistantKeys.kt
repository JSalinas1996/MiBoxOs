package com.jsalinas.miboxos.global

import android.view.KeyEvent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.jsalinas.miboxos.Prefs

/**
 * Qué botón del control abre el asistente. Por defecto, los códigos que suelen mandar los botones
 * de voz; si el control de la Mi Box manda otro, se aprende desde Ajustes → Asistente.
 */
object AssistantKeys {
    val DEFAULTS = setOf(KeyEvent.KEYCODE_SEARCH, KeyEvent.KEYCODE_VOICE_ASSIST, KeyEvent.KEYCODE_ASSIST)

    /** true mientras Ajustes espera que se apriete el botón a aprender. */
    var learning by mutableStateOf(false)

    fun isAssistantKey(prefs: Prefs, keyCode: Int): Boolean =
        if (prefs.assistantKeyCode != 0) keyCode == prefs.assistantKeyCode else keyCode in DEFAULTS

    fun label(prefs: Prefs): String {
        val code = prefs.assistantKeyCode
        if (code == 0) return "Botón de voz"
        // Algunos sistemas devuelven solo el número para teclas sin nombre
        val name = KeyEvent.keyCodeToString(code)
        return if (name.startsWith("KEYCODE_")) name.removePrefix("KEYCODE_") else "Botón $code"
    }

    /** Devuelve true si el evento se usó para aprender. */
    fun learn(prefs: Prefs, event: KeyEvent): Boolean {
        if (!learning) return false
        // Atrás y Home no se pueden usar para el asistente
        if (event.keyCode == KeyEvent.KEYCODE_BACK || event.keyCode == KeyEvent.KEYCODE_HOME) {
            if (event.action == KeyEvent.ACTION_UP) learning = false
            return true
        }
        if (event.action == KeyEvent.ACTION_UP) {
            prefs.assistantKeyCode = event.keyCode
            learning = false
        }
        return true
    }
}
