package com.jsalinas.miboxos.live

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.jsalinas.miboxos.Prefs
import com.jsalinas.miboxos.apps.TvApp
import com.jsalinas.miboxos.assistant.GeminiClient
import com.jsalinas.miboxos.assistant.VoiceRecorder
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Prueba contra la API real de Gemini. Solo corre si existe la variable GEMINI_LIVE_KEY
 * (nunca en CI ni con la key en el repo): verifica que Google acepte el pedido y las funciones.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class GeminiLiveTest {
    @Test fun `Gemini real entiende el pedido y llama a las funciones`() = runBlocking {
        val key = System.getenv("GEMINI_LIVE_KEY").orEmpty()
        assumeTrue("Sin GEMINI_LIVE_KEY: se saltea", key.isNotBlank())
        val apps = listOf("com.google.android.youtube.tv" to "YouTube", "com.netflix.ninja" to "Netflix").map { (pkg, label) ->
            TvApp(pkg, label, Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888).asImageBitmap())
        }
        // Medio segundo de silencio como audio; el pedido va como texto para que la prueba sea determinista
        val wav = VoiceRecorder().toWav(ByteArray(16_000))
        val reply = GeminiClient(key, System.getenv("GEMINI_LIVE_MODEL") ?: Prefs.DEFAULT_MODEL).interpret(
            wav, apps, "Prueba automática: ignorá el audio y tomá como pedido del usuario este texto: \"abrí YouTube\".",
            withHue = false, withNavigation = true,
        )
        println("transcripción=${reply.transcript} | respuesta=${reply.reply} | llamadas=${reply.calls.map { it.name + it.args }}")
        assertTrue(reply.calls.any { it.name == "abrir_app" && it.args.optString("paquete") == "com.google.android.youtube.tv" })
    }
}
