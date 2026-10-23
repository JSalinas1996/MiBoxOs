package com.jsalinas.miboxos.global

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import com.jsalinas.miboxos.GlobalAssistant
import com.jsalinas.miboxos.MiBoxApp
import com.jsalinas.miboxos.assistant.AssistantState
import com.jsalinas.miboxos.testutil.FakeApps
import com.jsalinas.miboxos.testutil.FakeAudio
import com.jsalinas.miboxos.testutil.FakeGemini
import com.jsalinas.miboxos.testutil.waitUntil
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class GlobalAssistantServiceTest {
    private lateinit var service: GlobalAssistantService
    private lateinit var app: MiBoxApp
    private val gemini = FakeGemini()

    @Before fun setUp() {
        // La capa visual (GlobalLayers) se prueba en los tests del launcher; acá, la lógica de teclas
        GlobalAssistantService.renderOverlay = false
        service = Robolectric.buildService(GlobalAssistantService::class.java).create().get()
        app = service.application as MiBoxApp
        shadowOf(app).grantPermissions(Manifest.permission.RECORD_AUDIO)
        FakeApps.installDefaults(app)
        // onServiceConnected es protegido: lo llama el sistema al activar el servicio
        AccessibilityService::class.java.getDeclaredMethod("onServiceConnected").apply { isAccessible = true }.invoke(service)
        shadowOf(Looper.getMainLooper()).idle()
    }

    @After fun tearDown() {
        service.onDestroy()
        gemini.shutdown()
        AssistantKeys.learning = false
    }

    private fun key(code: Int, action: Int, heldMs: Long = 0): Boolean {
        val now = SystemClock.uptimeMillis()
        return service.onKeyEvent(KeyEvent(now, now + if (action == KeyEvent.ACTION_UP) heldMs else 0, action, code, 0))
    }

    private fun press(code: Int): Boolean {
        val down = key(code, KeyEvent.ACTION_DOWN)
        val up = key(code, KeyEvent.ACTION_UP)
        return down && up
    }

    @Test fun `al activarse agrega la capa encima de todo como ventana de accesibilidad`() {
        GlobalAssistantService.renderOverlay = true
        val other = Robolectric.buildService(GlobalAssistantService::class.java).create().get()
        AccessibilityService::class.java.getDeclaredMethod("onServiceConnected").apply { isAccessible = true }.invoke(other)
        val wm = other.getSystemService(android.view.WindowManager::class.java)
        val views = org.robolectric.shadow.api.Shadow.extract<org.robolectric.shadows.ShadowWindowManagerImpl>(wm).views
        assertTrue(views.isNotEmpty())
        val params = views.last().layoutParams as android.view.WindowManager.LayoutParams
        assertEquals(android.view.WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, params.type)
        // Sin nada navegable abierto, la capa no toma el foco: el control sigue manejando la app de abajo
        assertTrue(params.flags and android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE != 0)
        other.onDestroy()
        GlobalAssistantService.renderOverlay = false
    }

    @Test fun `al activarse queda conectado y el launcher deja de dibujar sus capas`() {
        assertTrue(GlobalAssistant.connected)
        service.onDestroy()
        assertFalse(GlobalAssistant.connected)
    }

    @Test fun `toque corto del boton de voz abre el asistente encima de cualquier app`() {
        app.prefs.geminiApiKey = "k"
        FakeAudio.speech(speechMs = 60_000, realtime = true)
        assertTrue(press(KeyEvent.KEYCODE_SEARCH))
        assertEquals(AssistantState.Listening, app.assistant.state)
        assertFalse(app.controlCenter.visible)
        app.assistant.dismiss()
    }

    @Test fun `mantener el boton de voz abre el Centro de control`() {
        key(KeyEvent.KEYCODE_SEARCH, KeyEvent.ACTION_DOWN)
        key(KeyEvent.KEYCODE_SEARCH, KeyEvent.ACTION_UP, heldMs = 700)
        assertTrue(app.controlCenter.visible)
        assertEquals(AssistantState.Idle, app.assistant.state)
    }

    @Test fun `el boton de apps del control abre y cierra el Centro de control sin llegar al sistema`() {
        assertTrue(press(KeyEvent.KEYCODE_APP_SWITCH))
        assertTrue(app.controlCenter.visible)
        assertTrue(press(KeyEvent.KEYCODE_APP_SWITCH))
        assertFalse(app.controlCenter.visible)
    }

    @Test fun `atras cierra el Centro de control y no le llega a la app de abajo`() {
        app.controlCenter.open()
        assertTrue(press(KeyEvent.KEYCODE_BACK))
        assertFalse(app.controlCenter.visible)
        // Sin nada abierto, Atrás sigue de largo hacia la app
        assertFalse(press(KeyEvent.KEYCODE_BACK))
    }

    @Test fun `las demas teclas siguen de largo hacia la app`() {
        listOf(KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_VOLUME_UP)
            .forEach { assertFalse("tecla $it", press(it)) }
    }

    @Test fun `modo reposo - cualquier boton lo cierra`() {
        app.controlCenter.screensaverVisible = true
        assertTrue(press(KeyEvent.KEYCODE_DPAD_CENTER))
        assertFalse(app.controlCenter.screensaverVisible)
    }

    @Test fun `aprende otro boton para el asistente`() {
        AssistantKeys.learning = true
        assertTrue(press(KeyEvent.KEYCODE_BUTTON_A))
        assertFalse(AssistantKeys.learning)
        assertEquals(KeyEvent.KEYCODE_BUTTON_A, app.prefs.assistantKeyCode)
        assertTrue(AssistantKeys.label(app.prefs) in setOf("BUTTON_A", "Botón 96"))
        // El nuevo botón abre el asistente; el anterior ya no se intercepta
        assertTrue(press(KeyEvent.KEYCODE_BUTTON_A))
        assertTrue(app.assistant.state != AssistantState.Idle)
        app.assistant.dismiss()
        assertFalse(press(KeyEvent.KEYCODE_SEARCH))
    }

    @Test fun `aprender ignora Atras`() {
        AssistantKeys.learning = true
        press(KeyEvent.KEYCODE_BACK)
        assertFalse(AssistantKeys.learning)
        assertEquals(0, app.prefs.assistantKeyCode)
    }

    @Test fun `sin permiso de microfono avisa como darlo`() {
        shadowOf(app).denyPermissions(Manifest.permission.RECORD_AUDIO)
        press(KeyEvent.KEYCODE_SEARCH)
        assertTrue((app.assistant.state as AssistantState.Failed).message.contains("permiso de micrófono"))
    }

    @Test fun `Gemini sabe que app esta en pantalla y puede ir atras`() {
        val event = AccessibilityEvent.obtain(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED).apply { packageName = "com.netflix.ninja" }
        service.onAccessibilityEvent(event)
        assertEquals("App en pantalla ahora: Netflix", GlobalAssistant.screenContext())

        app.prefs.geminiApiKey = "k"
        FakeAudio.speech()
        gemini.response = FakeGemini.reply("volvé", "Listo.", "atras" to JSONObject())
        press(KeyEvent.KEYCODE_SEARCH)
        waitUntil(what = "respuesta") { app.assistant.state is AssistantState.Done }
        val body = gemini.lastBody()
        val prompt = body.getJSONArray("contents").getJSONObject(0).getJSONArray("parts").getJSONObject(0).getString("text")
        assertTrue(prompt.contains("App en pantalla ahora: Netflix"))
        val decls = body.getJSONArray("tools").getJSONObject(0).getJSONArray("functionDeclarations")
        assertTrue((0 until decls.length()).any { decls.getJSONObject(it).getString("name") == "atras" })
        assertTrue(shadowOf(service).globalActionsPerformed.contains(AccessibilityService.GLOBAL_ACTION_BACK))
    }
}
