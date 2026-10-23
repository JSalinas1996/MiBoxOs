package com.jsalinas.miboxos.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * Voz del asistente con el motor de texto a voz del sistema (sin servicios pagos).
 * Prefiere español de Argentina; si el motor no lo tiene, español genérico.
 */
class Speaker(context: Context) {
    private var ready = false
    private var pending: String? = null
    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        if (status == TextToSpeech.SUCCESS) {
            ready = true
            pickSpanish()
            pending?.let { speakNow(it) }
            pending = null
        }
    }

    fun speak(text: String) {
        if (text.isBlank()) return
        if (ready) speakNow(text) else pending = text
    }

    fun stop() {
        pending = null
        runCatching { tts.stop() }
    }

    private fun speakNow(text: String) {
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "assistant")
    }

    private fun pickSpanish() {
        for (locale in listOf(Locale.forLanguageTag("es-AR"), Locale.forLanguageTag("es-US"), Locale.forLanguageTag("es"))) {
            val result = tts.isLanguageAvailable(locale)
            if (result >= TextToSpeech.LANG_AVAILABLE) {
                tts.language = locale
                return
            }
        }
    }
}
