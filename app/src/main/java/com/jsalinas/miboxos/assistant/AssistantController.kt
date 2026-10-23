package com.jsalinas.miboxos.assistant

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.jsalinas.miboxos.GlobalAssistant
import com.jsalinas.miboxos.Prefs
import com.jsalinas.miboxos.apps.AppRepository
import com.jsalinas.miboxos.apps.TvApp
import com.jsalinas.miboxos.hue.HueController
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
    private val hue: HueController,
    private val scope: CoroutineScope,
    private val apps: () -> List<TvApp>,
    /** Qué se está viendo (lo informa el asistente global); ayuda a interpretar "pausá" o "subí". */
    private val screenContext: () -> String? = { null },
    /** Avisa cuándo empieza y termina de grabar (el asistente global sostiene el micrófono con un servicio en primer plano). */
    private val onRecording: (Boolean) -> Unit = {},
    /** Respuesta lista para leer en voz alta (si está activado en Ajustes). */
    private val onReply: (String) -> Unit = {},
    private val onDismiss: () -> Unit = {},
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
        onDismiss()
        job?.cancel()
        recorder.stop()
        state = AssistantState.Idle
        level = 0f
    }

    /** Muestra un aviso en el panel del asistente (por ejemplo, permiso de micrófono denegado). */
    fun fail(message: String) = show(AssistantState.Failed(message))

    private fun start() {
        val key = prefs.geminiApiKey
        if (key.isBlank()) {
            show(AssistantState.Failed("Falta la API key de Gemini. Cargala por ADB (ver README)."))
            return
        }
        job?.cancel()
        state = AssistantState.Listening
        job = scope.launch {
            try {
                onRecording(true)
                val recording = try {
                    recorder.record { level = it }
                } finally {
                    onRecording(false)
                }
                level = 0f
                if (!recording.heardSpeech) {
                    show(AssistantState.Failed(
                        "No te escuché (nivel máximo ${"%.2f".format(recording.peakLevel)}). " +
                            "Si siempre da cerca de 0, el micrófono no le llega a la app."))
                    return@launch
                }
                state = AssistantState.Thinking
                // Si el bridge no responde, el asistente sigue andando sin luces
                val hueContext = runCatching { hue.home()?.describe() }.getOrNull()
                val extra = listOfNotNull(screenContext(), hueContext).joinToString("\n\n").ifBlank { null }
                val reply = GeminiClient(key, prefs.geminiModel).interpret(
                    recording.wav, apps(), extra, withHue = hueContext != null, withNavigation = GlobalAssistant.connected,
                )
                val errors = Tools.execute(context, repo, hue, reply.calls)
                val text = (listOf(reply.reply) + errors).filter { it.isNotBlank() }.joinToString("\n")
                val shown = text.ifBlank { "Listo." }
                show(AssistantState.Done(reply.transcript, shown))
                onReply(shown)
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
