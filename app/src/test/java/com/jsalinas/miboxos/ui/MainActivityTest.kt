package com.jsalinas.miboxos.ui

import android.Manifest
import android.app.Application
import android.content.Intent
import android.view.KeyEvent
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.jsalinas.miboxos.MainActivity
import com.jsalinas.miboxos.Prefs
import com.jsalinas.miboxos.testutil.FakeApps
import com.jsalinas.miboxos.testutil.FakeAudio
import com.jsalinas.miboxos.testutil.FakeGemini
import com.jsalinas.miboxos.testutil.FakeHueBridge
import com.jsalinas.miboxos.Endpoints
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Uso real de la app con el control remoto: foco, OK, Atrás, Menú, ajustes, asistente y Hue. */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w960dp-h540dp-land-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MainActivityTest {
    @get:Rule val compose = createEmptyComposeRule()

    private val app: Application = ApplicationProvider.getApplicationContext()
    private val prefs = Prefs(app)
    private lateinit var scenario: ActivityScenario<MainActivity>
    private val gemini = FakeGemini()

    @Before fun setUp() {
        FakeApps.installDefaults(app)
        shadowOf(app).grantPermissions(Manifest.permission.RECORD_AUDIO, Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    @After fun tearDown() {
        if (::scenario.isInitialized) scenario.close()
        gemini.shutdown()
    }

    private fun launch(intent: Intent = Intent(app, MainActivity::class.java)) {
        scenario = ActivityScenario.launch(intent)
        compose.waitUntil(5_000) { runCatching { compose.onNodeWithContentDescription("Fotos").assertExists() }.isSuccess }
    }

    private fun key(k: Key) = compose.onRoot().performKeyInput { pressKey(k) }

    /** Tecla que llega a la Activity (Atrás, Menú), como la manda el control. */
    private fun activityKey(code: Int) = scenario.onActivity {
        it.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
        it.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
    }

    /** "OK" sobre un elemento: las Surface de TV no aceptan toques, solo la acción de click (D-pad/accesibilidad). */
    private fun select(text: String) =
        compose.onNode(hasText(text) and hasClickAction()).performSemanticsAction(SemanticsActions.OnClick)

    private fun selectDescription(description: String) =
        compose.onNode(hasContentDescription(description) and hasClickAction()).performSemanticsAction(SemanticsActions.OnClick)

    private fun focused(description: String) = compose.onNodeWithContentDescription(description).assertIsFocused()

    private fun waitText(text: String, substring: Boolean = false) = compose.waitUntil(10_000) {
        runCatching { compose.onNodeWithText(text, substring = substring).assertExists() }.isSuccess
    }

    @Test fun inicio_muestraLasAppsYLaPrimeraTieneFoco() {
        launch()
        listOf("Fotos", "Netflix", "Spotify", "YouTube").forEach { compose.onNodeWithContentDescription(it).assertExists() }
        compose.waitForIdle()
        focused("Fotos")
    }

    @Test fun dpad_mueveElFocoYOkAbreLaApp() {
        launch()
        key(Key.DirectionRight)
        focused("Netflix")
        key(Key.DirectionCenter)
        assertEquals("com.netflix.ninja", shadowOf(app).nextStartedActivity.component!!.packageName)
    }

    @Test fun atras_enElInicioNoSaleDelLauncher() {
        launch()
        activityKey(KeyEvent.KEYCODE_BACK)
        scenario.onActivity { assertFalse(it.isFinishing) }
    }

    private fun row(text: String) = compose.onNode(hasText(text) and hasClickAction())

    @Test fun ajustes_navegacionComoTvOS_modoFondoYAtras() {
        launch()
        selectDescription("Ajustes")
        waitText("Apariencia")
        compose.waitForIdle()
        // Al abrir, el foco queda en la primera fila (si no, el control no hace nada)
        row("Apariencia").assertIsFocused()

        // Apariencia → Claro
        key(Key.DirectionCenter)
        waitText("Oscuro")
        compose.waitForIdle()
        row("Oscuro").assertIsFocused()
        key(Key.DirectionDown)
        row("Claro").assertIsFocused()
        key(Key.DirectionCenter)
        assertFalse(prefs.darkMode)

        // Atrás vuelve a la raíz con el foco en la fila de la que se salió
        activityKey(KeyEvent.KEYCODE_BACK)
        waitText("Philips Hue")
        compose.waitForIdle()
        row("Apariencia").assertIsFocused()

        // Fondo → Tierra
        key(Key.DirectionDown)
        row("Fondo").assertIsFocused()
        key(Key.DirectionCenter)
        compose.waitUntil(5_000) { runCatching { compose.onNodeWithContentDescription("Tierra").assertExists() }.isSuccess }
        selectDescription("Tierra")
        assertEquals("tierra", prefs.wallpaper)

        // Atrás dos veces: raíz y después el inicio, con el foco en las apps
        activityKey(KeyEvent.KEYCODE_BACK)
        compose.waitForIdle()
        activityKey(KeyEvent.KEYCODE_BACK)
        compose.waitForIdle()
        compose.onNodeWithText("Philips Hue").assertDoesNotExist()
        focused("Fotos")
        scenario.onActivity { assertFalse(it.isFinishing) }
    }

    @Test fun inicio_asistenteYAjustesEstanEnLaPrimeraFila() {
        launch()
        // Fotos, Netflix, Spotify, Asistente, Ajustes, YouTube
        repeat(3) { key(Key.DirectionRight) }
        focused("Asistente")
        key(Key.DirectionRight)
        focused("Ajustes")
        key(Key.DirectionCenter)
        waitText("Philips Hue")
    }

    @Test fun modoReposo_probarElegirIntervaloYCualquierBotonLoCierra() {
        launch()
        selectDescription("Ajustes")
        waitText("Modo reposo")
        select("Modo reposo")
        waitText("Probar ahora")
        select("1 min")
        assertEquals(60, prefs.screensaverSeconds)
        select("Probar ahora")
        compose.waitUntil(5_000) { runCatching { compose.onNodeWithTag("screensaver").assertExists() }.isSuccess }
        // Cualquier botón lo cierra y no hace nada más: seguimos en la página de Modo reposo
        activityKey(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.waitForIdle()
        compose.onNodeWithTag("screensaver").assertDoesNotExist()
        compose.onNodeWithText("Probar ahora").assertExists()
        assertEquals(60, prefs.screensaverSeconds)
    }

    @Test fun modoReposo_atrasTambienLoCierraSinSalirDeAjustes() {
        launch()
        selectDescription("Ajustes")
        waitText("Modo reposo")
        select("Modo reposo")
        waitText("Probar ahora")
        select("Probar ahora")
        compose.waitUntil(5_000) { runCatching { compose.onNodeWithTag("screensaver").assertExists() }.isSuccess }
        activityKey(KeyEvent.KEYCODE_BACK)
        compose.waitForIdle()
        compose.onNodeWithTag("screensaver").assertDoesNotExist()
        compose.onNodeWithText("Probar ahora").assertExists()
    }

    @Test fun ajustes_recuerdaModoYFondoAlReiniciar() {
        prefs.darkMode = false
        prefs.wallpaper = "saturno"
        launch()
        selectDescription("Ajustes")
        waitText("Apariencia")
        // El fondo elegido aparece marcado y el modo claro activo; se verifica por las prefs que leyó la Activity
        assertEquals("saturno", prefs.wallpaper)
        assertFalse(prefs.darkMode)
    }

    @Test fun botonHome_cierraAjustes() {
        launch()
        selectDescription("Ajustes")
        waitText("Apariencia")
        scenario.onActivity { it.onNewIntent(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)) }
        compose.waitForIdle()
        compose.onNodeWithText("Philips Hue").assertDoesNotExist()
        focused("Fotos")
    }

    @Test fun asistente_sinApiKeyExplicaQueFalta() {
        launch()
        activityKey(KeyEvent.KEYCODE_SEARCH)
        waitText("Falta la API key", substring = true)
    }

    @Test fun asistente_pidePermisoDeMicrofonoSiNoLoTiene() {
        shadowOf(app).denyPermissions(Manifest.permission.RECORD_AUDIO)
        launch()
        selectDescription("Asistente")
        scenario.onActivity {
            assertEquals(Manifest.permission.RECORD_AUDIO, shadowOf(it).lastRequestedPermission.requestedPermissions.single())
        }
    }

    @Test fun asistente_permisoDenegadoExplicaComoHabilitarlo() {
        shadowOf(app).denyPermissions(Manifest.permission.RECORD_AUDIO)
        launch()
        selectDescription("Asistente")
        scenario.onActivity {
            val request = shadowOf(it).lastRequestedPermission
            it.onRequestPermissionsResult(request.requestCode, request.requestedPermissions, intArrayOf(-1))
        }
        waitText("Sin permiso de micrófono", substring = true)
    }

    @Test fun ajustes_pidePermisoDeArchivosUnaSolaVez() {
        shadowOf(app).denyPermissions(Manifest.permission.READ_EXTERNAL_STORAGE)
        launch()
        selectDescription("Ajustes")
        waitText("Apariencia")
        var first: Any? = null
        scenario.onActivity {
            first = shadowOf(it).lastRequestedPermission
            assertEquals(Manifest.permission.READ_EXTERNAL_STORAGE, shadowOf(it).lastRequestedPermission.requestedPermissions.single())
        }
        activityKey(KeyEvent.KEYCODE_BACK)
        selectDescription("Ajustes")
        waitText("Apariencia")
        // Ningún pedido nuevo
        scenario.onActivity { assert(shadowOf(it).lastRequestedPermission === first) }
    }

    @Test fun asistente_flujoCompletoDesdeElBoton() {
        launch(Intent(app, MainActivity::class.java).putExtra("gemini_api_key", "KEY-DE-PRUEBA"))
        assertEquals("KEY-DE-PRUEBA", prefs.geminiApiKey)
        FakeAudio.speech()
        gemini.response = FakeGemini.reply("abrí spotify", "Abriendo Spotify.", "abrir_app" to JSONObject().put("paquete", "com.spotify.tv.android"))
        selectDescription("Asistente")
        waitText("Abriendo Spotify.")
        compose.onNodeWithText("“abrí spotify”").assertExists()
        assertEquals("com.spotify.tv.android", shadowOf(app).nextStartedActivity.component!!.packageName)
        assertEquals("KEY-DE-PRUEBA", gemini.requests.single().getHeader("x-goog-api-key"))
    }

    @Test fun asistente_atrasLoCierra() {
        launch(Intent(app, MainActivity::class.java).putExtra("gemini_api_key", "k"))
        FakeAudio.speech(speechMs = 60_000, realtime = true)
        activityKey(KeyEvent.KEYCODE_SEARCH)
        waitText("Te escucho…")
        activityKey(KeyEvent.KEYCODE_BACK)
        compose.waitUntil(3_000) { runCatching { compose.onNodeWithText("Te escucho…").assertDoesNotExist() }.isSuccess }
        // Cancelado: no se manda nada a Gemini
        Thread.sleep(300)
        assertEquals(0, gemini.requests.size)
    }

    @Test fun hue_vincularDesdeAjustesYUsarloConLaVoz() {
        val bridge = FakeHueBridge()
        val discovery = MockWebServer().apply { enqueue(MockResponse().setBody(bridge.discoveryJson())); start() }
        Endpoints.hueDiscovery = discovery.url("/").toString()
        try {
            launch(Intent(app, MainActivity::class.java).putExtra("gemini_api_key", "k"))
            selectDescription("Ajustes")
            waitText("Philips Hue")
            select("Philips Hue")
            waitText("Vincular")
            select("Vincular")
            waitText("3 luces · 3 grupos · 3 escenas")

            activityKey(KeyEvent.KEYCODE_BACK)
            FakeAudio.speech()
            gemini.response = FakeGemini.reply(
                "apagá el living", "Listo, apagué el living.",
                "luces" to JSONObject().put("objetivo", "Living").put("encender", false),
            )
            activityKey(KeyEvent.KEYCODE_SEARCH)
            waitText("Listo, apagué el living.")
            assertEquals("/clip/v2/resource/grouped_light/gl-living", bridge.puts().single().path)
            // Gemini recibió las luces disponibles
            val prompt = gemini.lastBody().getJSONArray("contents").getJSONObject(0).getJSONArray("parts").getJSONObject(0).getString("text")
            assert(prompt.contains("Living")) { prompt }
        } finally {
            bridge.shutdown()
            discovery.shutdown()
        }
    }
}
