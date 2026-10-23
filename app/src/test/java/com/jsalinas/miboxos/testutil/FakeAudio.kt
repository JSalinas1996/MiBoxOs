package com.jsalinas.miboxos.testutil

import org.robolectric.shadows.ShadowAudioRecord
import java.nio.ByteBuffer
import kotlin.math.PI
import kotlin.math.sin

/** Reemplaza el micrófono: [speechMs] de un tono fuerte (voz) y después silencio. */
object FakeAudio {
    /** [realtime]: entrega el audio a la velocidad de un micrófono real (para ver el estado "Te escucho…"). */
    fun speech(speechMs: Int = 1_500, realtime: Boolean = false) = install(speechMs, amplitude = 9_000.0, realtime)

    fun silence() = install(0, amplitude = 0.0)

    private fun install(speechMs: Int, amplitude: Double, realtime: Boolean = false) {
        var sample = 0L
        val speechSamples = speechMs * 16L
        ShadowAudioRecord.setSourceProvider {
            object : ShadowAudioRecord.AudioRecordSource {
                override fun readInShortArray(audioData: ShortArray, offsetInShorts: Int, sizeInShorts: Int, isBlocking: Boolean): Int {
                    for (i in 0 until sizeInShorts) {
                        val v = if (sample < speechSamples) amplitude * sin(2 * PI * 220 * sample / 16_000.0) else 0.0
                        audioData[offsetInShorts + i] = v.toInt().toShort()
                        sample++
                    }
                    if (realtime) Thread.sleep(sizeInShorts / 16L)
                    return sizeInShorts
                }

                override fun readInByteArray(audioData: ByteArray, offsetInBytes: Int, sizeInBytes: Int, isBlocking: Boolean) = sizeInBytes
                override fun readInFloatArray(audioData: FloatArray, offsetInFloats: Int, sizeInFloats: Int, isBlocking: Boolean) = sizeInFloats
                override fun readInDirectBuffer(buffer: ByteBuffer, sizeInBytes: Int, isBlocking: Boolean) = sizeInBytes
            }
        }
    }
}
