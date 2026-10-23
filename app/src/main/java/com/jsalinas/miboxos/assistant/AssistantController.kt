package com.jsalinas.miboxos.assistant

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.jsalinas.miboxos.Prefs
import com.jsalinas.miboxos.apps.AppRepository
import com.jsalinas.miboxos.apps.TvApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

sealed interface AssistantState {
    data object Idle : AssistantState
    data object Listening : AssistantState
    data object Thinking : AssistantState
    data class Done(val transcript: String, val reply: String) : AssistantState
    data class Failed(val message: String) : AssistantState
}

class AssistantController(
    private val context: Context,
    private val prefs: Prefs,
    private val repo: AppRepository,
    private val scope: CoroutineScope,
    private val apps: () -> List<TvApp>,
) {
    var state: AssistantState by mutableStateOf(AssistantState.Idle)
        private set

    /** Nivel del micrófono (0..1) para animar el orbe. */
    var level by mutableFloatStateOf(0f)
        private set

    private val recorder = VoiceRecorder()
    private var job: Job? = null

    /** Primer toque empieza a escuchar; un segundo toque mientras escucha corta la grabación. */
    fun toggle() {
        when (state) {
            AssistantState.Listening -> recorder.stop()
            AssistantState.Thinking -> Unit
            else -> start()
        }
    }

    fun dismiss() {
        job?.cancel()
        recorder.stop()
        state = AssistantState.Idle
        level = 0f
    }

    private fun start() {
        val key = prefs.geminiApiKey
        if (key.isBlank()) {
            show(AssistantState.Failed("Falta la API key de Gemini. Cargala por ADB (ver README)."))
            return
        }
        job?.cancel()
        job = scope.launch {
            try {
                state = AssistantState.Listening
                val recording = recorder.record { level = it }
                level = 0f
                if (!recording.heardSpeech) {
                    show(AssistantState.Failed(
                        "No te escuché (nivel máximo ${"%.2f".format(recording.peakLevel)}). " +
                            "Si siempre da cerca de 0, el micrófono no le llega a la app."))
                    return@launch
                }
                state = AssistantState.Thinking
                val reply = GeminiClient(key, prefs.geminiModel).interpret(recording.wav, apps())
                val errors = Tools.execute(context, repo, reply.calls)
                val text = (listOf(reply.reply) + errors).filter { it.isNotBlank() }.joinToString("\n")
                show(AssistantState.Done(reply.transcript, text.ifBlank { "Listo." }))
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                level = 0f
                show(AssistantState.Failed(e.message ?: e.toString()))
            }
        }
    }

    private fun show(result: AssistantState) {
        state = result
        val hideAfter = if (result is AssistantState.Failed) 8_000L else 5_000L
        scope.launch {
            delay(hideAfter)
            if (state === result) state = AssistantState.Idle
        }
    }
}
