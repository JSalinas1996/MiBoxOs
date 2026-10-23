package com.jsalinas.miboxos.assistant

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.coroutines.coroutineContext
import kotlin.math.sqrt

class Recording(
    val wav: ByteArray,
    /** Si nunca se superó el umbral de voz, el micrófono probablemente no está llegando a la app. */
    val heardSpeech: Boolean,
    val peakLevel: Float,
)

/**
 * Graba hasta detectar silencio después de hablar, o hasta [MAX_MS].
 * El nivel (0..1) se reporta en cada bloque para animar el orbe y diagnosticar el micrófono.
 */
class VoiceRecorder {
    @Volatile private var stopRequested = false

    fun stop() {
        stopRequested = true
    }

    @SuppressLint("MissingPermission") // el permiso se verifica antes de llamar
    suspend fun record(onLevel: (Float) -> Unit): Recording = withContext(Dispatchers.IO) {
        stopRequested = false
        val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val record = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            maxOf(minBuf, CHUNK_SAMPLES * 2 * 4),
        )
        check(record.state == AudioRecord.STATE_INITIALIZED) { "No se pudo abrir el micrófono" }

        val pcm = ByteArrayOutputStream()
        val chunk = ShortArray(CHUNK_SAMPLES)
        val bytes = ByteBuffer.allocate(CHUNK_SAMPLES * 2).order(ByteOrder.LITTLE_ENDIAN)
        var elapsedMs = 0
        var silenceMs = 0
        var heardSpeech = false
        var peak = 0f
        val chunkMs = CHUNK_SAMPLES * 1000 / SAMPLE_RATE

        record.startRecording()
        try {
            while (coroutineContext.isActive && !stopRequested && elapsedMs < MAX_MS) {
                val read = record.read(chunk, 0, chunk.size)
                if (read <= 0) continue
                var sum = 0.0
                bytes.clear()
                for (i in 0 until read) {
                    sum += chunk[i].toDouble() * chunk[i]
                    bytes.putShort(chunk[i])
                }
                pcm.write(bytes.array(), 0, read * 2)
                val level = (sqrt(sum / read) / 6000.0).toFloat().coerceIn(0f, 1f)
                peak = maxOf(peak, level)
                onLevel(level)

                elapsedMs += chunkMs
                if (level > SPEECH_THRESHOLD) {
                    heardSpeech = true
                    silenceMs = 0
                } else {
                    silenceMs += chunkMs
                }
                if (heardSpeech && silenceMs >= END_SILENCE_MS) break
                if (!heardSpeech && elapsedMs >= NO_SPEECH_TIMEOUT_MS) break
            }
        } finally {
            record.stop()
            record.release()
        }
        Recording(toWav(pcm.toByteArray()), heardSpeech, peak)
    }

    internal fun toWav(pcm: ByteArray): ByteArray {
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(36 + pcm.size); put("WAVE".toByteArray())
            put("fmt ".toByteArray()); putInt(16); putShort(1); putShort(1)
            putInt(SAMPLE_RATE); putInt(SAMPLE_RATE * 2); putShort(2); putShort(16)
            put("data".toByteArray()); putInt(pcm.size)
        }
        return header.array() + pcm
    }

    companion object {
        const val SAMPLE_RATE = 16_000
        private const val CHUNK_SAMPLES = 1_600 // 100 ms
        private const val MAX_MS = 10_000
        private const val END_SILENCE_MS = 1_200
        private const val NO_SPEECH_TIMEOUT_MS = 5_000
        private const val SPEECH_THRESHOLD = 0.08f
    }
}
