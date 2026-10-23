package com.jsalinas.miboxos.assistant

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.jsalinas.miboxos.Prefs
import com.jsalinas.miboxos.apps.AppRepository
import com.jsalinas.miboxos.hue.HueController
import com.jsalinas.miboxos.testutil.FakeApps
import com.jsalinas.miboxos.testutil.FakeAudio
import com.jsalinas.miboxos.testutil.FakeGemini
import com.jsalinas.miboxos.testutil.waitUntil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AssistantControllerTest {
    private val context: Application = ApplicationProvider.getApplicationContext()
    private val prefs = Prefs(context)
    private val repo = AppRepository(context, prefs)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val gemini = FakeGemini()
    private lateinit var controller: AssistantController

    @Before fun setUp() {
        FakeApps.installDefaults(context)
        val apps = runBlocking { repo.loadApps() }
        controller = AssistantController(context, prefs, repo, HueController(prefs, scope), scope) { apps }
    }

    @After fun tearDown() = gemini.shutdown()

    private fun finished() = controller.state is AssistantState.Done || controller.state is AssistantState.Failed

    @Test fun `sin API key avisa como cargarla`() {
        controller.toggle()
        val state = controller.state as AssistantState.Failed
        assertTrue(state.message.contains("API key"))
    }

    @Test fun `si no se escucha nada lo dice con el nivel del microfono`() {
        prefs.geminiApiKey = "k"
        FakeAudio.silence()
        controller.toggle()
        assertEquals(AssistantState.Listening, controller.state)
        waitUntil(what = "fin") { finished() }
        assertTrue((controller.state as AssistantState.Failed).message.startsWith("No te escuché"))
        assertTrue(gemini.requests.isEmpty())
    }

    @Test fun `flujo completo - escucha, Gemini decide, abre Netflix`() {
        prefs.geminiApiKey = "k"
        FakeAudio.speech()
        gemini.response = FakeGemini.reply("abrí netflix", "Abriendo Netflix.", "abrir_app" to JSONObject().put("paquete", "com.netflix.ninja"))
        controller.toggle()
        waitUntil(what = "fin") { finished() }
        val done = controller.state as AssistantState.Done
        assertEquals("abrí netflix", done.transcript)
        assertEquals("Abriendo Netflix.", done.reply)
        assertEquals("com.netflix.ninja", shadowOf(context).nextStartedActivity.component!!.packageName)
    }

    @Test fun `errores de acciones se suman a la respuesta`() {
        prefs.geminiApiKey = "k"
        FakeAudio.speech()
        gemini.response = FakeGemini.reply("abrí disney", "Abriendo Disney.", "abrir_app" to JSONObject().put("paquete", "com.disney.plus"))
        controller.toggle()
        waitUntil(what = "fin") { finished() }
        assertEquals("Abriendo Disney.\nNo encontré la app com.disney.plus", (controller.state as AssistantState.Done).reply)
    }

    @Test fun `error de Gemini se muestra`() {
        prefs.geminiApiKey = "mala"
        FakeAudio.speech()
        gemini.response = FakeGemini.error(403, "Permission denied")
        controller.toggle()
        waitUntil(what = "fin") { finished() }
        assertEquals("Gemini respondió 403: Permission denied", (controller.state as AssistantState.Failed).message)
    }

    @Test fun `sin internet se muestra error y no se cuelga`() {
        prefs.geminiApiKey = "k"
        FakeAudio.speech()
        gemini.shutdown()
        controller.toggle()
        waitUntil(what = "fin") { finished() }
        assertTrue(controller.state is AssistantState.Failed)
    }

    @Test fun `segundo toque mientras escucha corta y procesa`() {
        prefs.geminiApiKey = "k"
        FakeAudio.speech(speechMs = 60_000)
        controller.toggle()
        controller.toggle()
        waitUntil(what = "fin") { finished() }
        assertTrue(controller.state is AssistantState.Done)
    }

    @Test fun `atras cierra el asistente`() {
        prefs.geminiApiKey = "k"
        FakeAudio.speech(speechMs = 60_000)
        controller.toggle()
        controller.dismiss()
        assertEquals(AssistantState.Idle, controller.state)
    }
}
