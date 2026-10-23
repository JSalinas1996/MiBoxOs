package com.jsalinas.miboxos.assistant

import com.jsalinas.miboxos.testutil.FakeAudio
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.nio.ByteBuffer
import java.nio.ByteOrder

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class VoiceRecorderTest {
    @Test fun `corta solo despues de 1,2 s de silencio tras hablar`() = runBlocking {
        FakeAudio.speech(speechMs = 1_500)
        val rec = VoiceRecorder().record {}
        assertTrue(rec.heardSpeech)
        // 1,5 s de voz + 1,2 s de silencio = 2,7 s de audio a 16 kHz mono 16 bit
        val seconds = (rec.wav.size - 44) / 32_000.0
        assertEquals(2.7, seconds, 0.15)
    }

    @Test fun `sin voz se rinde a los 5 s y avisa`() = runBlocking {
        FakeAudio.silence()
        val rec = VoiceRecorder().record {}
        assertFalse(rec.heardSpeech)
        assertEquals(0f, rec.peakLevel, 0.001f)
        assertEquals(5.0, (rec.wav.size - 44) / 32_000.0, 0.15)
    }

    @Test fun `stop corta la grabacion`() = runBlocking {
        FakeAudio.speech(speechMs = 60_000)
        val recorder = VoiceRecorder()
        var chunks = 0
        val rec = recorder.record { if (++chunks == 10) recorder.stop() }
        assertEquals(1.0, (rec.wav.size - 44) / 32_000.0, 0.15)
    }

    @Test fun `el wav tiene cabecera valida`() {
        val wav = VoiceRecorder().toWav(ByteArray(3200))
        assertEquals("RIFF", String(wav, 0, 4))
        assertEquals("WAVE", String(wav, 8, 4))
        val b = ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(16_000, b.getInt(24))
        assertEquals(3200, b.getInt(40))
    }
}
