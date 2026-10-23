package com.jsalinas.miboxos.assistant

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.jsalinas.miboxos.apps.TvApp
import com.jsalinas.miboxos.testutil.FakeGemini
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class GeminiClientTest {
    private val gemini = FakeGemini()
    private val apps by lazy {
        listOf(TvApp("com.netflix.ninja", "Netflix", Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888).asImageBitmap()))
    }

    @After fun tearDown() = gemini.shutdown()

    @Test fun `manda audio, apps, api key y funciones`() = runBlocking {
        GeminiClient("KEY123", "gemini-3.8-flash").interpret(byteArrayOf(1, 2, 3), apps, hueContext = null)
        val req = gemini.requests.single()
        assertEquals("/v1beta/models/gemini-3.8-flash:generateContent", req.path)
        assertEquals("KEY123", req.getHeader("x-goog-api-key"))
        val body = gemini.lastBody()
        val parts = body.getJSONArray("contents").getJSONObject(0).getJSONArray("parts")
        assertTrue(parts.getJSONObject(0).getString("text").contains("Netflix (com.netflix.ninja)"))
        assertEquals("audio/wav", parts.getJSONObject(1).getJSONObject("inlineData").getString("mimeType"))
        val names = functionNames(body)
        assertEquals(listOf("mostrar_respuesta", "abrir_app", "buscar_en_youtube", "control_multimedia", "volumen", "abrir_ajustes", "apagar_tv", "cancelar_apagado"), names)
        assertEquals("ANY", body.getJSONObject("toolConfig").getJSONObject("functionCallingConfig").getString("mode"))
    }

    @Test fun `con Hue vinculado agrega funciones de luces y el contexto`() = runBlocking {
        GeminiClient("k", "m").interpret(byteArrayOf(), apps, hueContext = "Grupos: Living")
        val body = gemini.lastBody()
        assertTrue(functionNames(body).containsAll(listOf("luces", "escena_hue")))
        val text = body.getJSONArray("contents").getJSONObject(0).getJSONArray("parts").getJSONObject(0).getString("text")
        assertTrue(text.contains("Grupos: Living"))
    }

    @Test fun `interpreta transcripcion, respuesta y acciones ignorando el razonamiento`() = runBlocking {
        gemini.response = FakeGemini.reply("abrí netflix", "Abriendo Netflix.", "abrir_app" to JSONObject().put("paquete", "com.netflix.ninja"))
        val reply = GeminiClient("k", "m").interpret(byteArrayOf(), apps, null)
        assertEquals("abrí netflix", reply.transcript)
        assertEquals("Abriendo Netflix.", reply.reply)
        assertEquals("abrir_app", reply.calls.single().name)
        assertEquals("com.netflix.ninja", reply.calls.single().args.getString("paquete"))
    }

    @Test fun `error de la API se muestra con el mensaje de Google`() = runBlocking {
        gemini.response = FakeGemini.error(400, "API key not valid. Please pass a valid API key.")
        try {
            GeminiClient("mala", "m").interpret(byteArrayOf(), apps, null)
            fail("debería fallar")
        } catch (e: IllegalStateException) {
            assertEquals("Gemini respondió 400: API key not valid. Please pass a valid API key.", e.message)
        }
    }

    @Test fun `respuesta sin candidatos no rompe`() = runBlocking {
        gemini.response = okhttp3.mockwebserver.MockResponse().setBody("""{"candidates":[]}""")
        val reply = GeminiClient("k", "m").interpret(byteArrayOf(), apps, null)
        assertEquals("", reply.reply)
        assertTrue(reply.calls.isEmpty())
    }

    private fun functionNames(body: JSONObject): List<String> {
        val decls = body.getJSONArray("tools").getJSONObject(0).getJSONArray("functionDeclarations")
        return (0 until decls.length()).map { decls.getJSONObject(it).getString("name") }
    }
}
