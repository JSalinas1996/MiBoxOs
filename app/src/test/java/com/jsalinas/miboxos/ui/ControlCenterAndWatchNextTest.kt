package com.jsalinas.miboxos.ui

import android.Manifest
import android.app.Application
import android.content.Intent
import android.view.KeyEvent
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.jsalinas.miboxos.MainActivity
import com.jsalinas.miboxos.MiBoxApp
import com.jsalinas.miboxos.testutil.FakeApps
import com.jsalinas.miboxos.testutil.FakeHueBridge
import com.jsalinas.miboxos.watchnext.FakeTvProvider
import com.jsalinas.miboxos.watchnext.WatchNextRepository
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w960dp-h540dp-land-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ControlCenterAndWatchNextTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val app: Application = ApplicationProvider.getApplicationContext()
    private val mibox get() = app as MiBoxApp
    private lateinit var scenario: ActivityScenario<MainActivity>
    private var bridge: FakeHueBridge? = null

    @Before fun setUp() {
        FakeApps.installDefaults(app)
        shadowOf(app).grantPermissions(Manifest.permission.RECORD_AUDIO, Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    @After fun tearDown() {
        if (::scenario.isInitialized) scenario.close()
        bridge?.shutdown()
    }

    private fun launch() {
        scenario = ActivityScenario.launch(Intent(app, MainActivity::class.java))
        compose.waitUntil(5_000) { runCatching { compose.onNodeWithContentDescription("Fotos").assertExists() }.isSuccess }
    }

    private fun activityKey(code: Int) = scenario.onActivity {
        it.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
        it.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
    }

    private fun select(text: String) = compose.onNode(hasText(text) and hasClickAction()).performSemanticsAction(SemanticsActions.OnClick)

    /** Botón redondo del Centro de control. */
    private fun quick(label: String) = compose.onNodeWithTag("cc:$label")

    private fun room(name: String) = compose.onNode(hasText(name) and hasClickAction())

    private fun waitText(text: String) = compose.waitUntil(10_000) { runCatching { compose.onNodeWithText(text).assertExists() }.isSuccess }

    private fun pairHue(): FakeHueBridge {
        val b = FakeHueBridge().also { bridge = it }
        mibox.prefs.hueBridgeIp = b.host
        mibox.prefs.hueBridgeId = b.bridgeId
        mibox.prefs.hueBridgePort = b.port
        mibox.prefs.hueAppKey = b.appKey
        return b
    }

    @Test fun centroDeControl_menuLoAbreConFocoYAtrasLoCierra() {
        launch()
        activityKey(KeyEvent.KEYCODE_MENU)
        compose.waitUntil(5_000) { runCatching { compose.onNodeWithTag("controlCenter").assertExists() }.isSuccess }
        compose.waitForIdle()
        // Al abrir, el foco queda en el primer botón (si no, el control no haría nada)
        quick("Reposo").assertIsFocused()
        compose.onNodeWithText("Vinculá Philips Hue", substring = true).assertExists()
        activityKey(KeyEvent.KEYCODE_BACK)
        compose.waitForIdle()
        compose.onNodeWithTag("controlCenter").assertDoesNotExist()
        compose.onNodeWithContentDescription("Fotos").assertIsFocused()
    }

    @Test fun centroDeControl_lucesYEscenasDeHue() {
        val b = pairHue()
        launch()
        activityKey(KeyEvent.KEYCODE_MENU)
        waitText("Dormitorio")
        compose.onNode(hasText("Living") and hasText("Encendida")).assertExists()
        compose.onNode(hasText("Dormitorio") and hasText("Apagada")).assertExists()

        // Prender el dormitorio (estaba apagado): cambia al instante y le avisa al bridge
        room("Dormitorio").performSemanticsAction(SemanticsActions.OnClick)
        compose.onNode(hasText("Dormitorio") and hasText("Encendida")).assertExists()
        compose.waitUntil(5_000) { b.puts().isNotEmpty() }
        val put = b.puts().single()
        assertEquals("/clip/v2/resource/grouped_light/gl-dorm", put.path)
        assertTrue(JSONObject(put.body.readUtf8()).getJSONObject("on").getBoolean("on"))

        // Escena
        select("Cine · Living")
        compose.waitUntil(5_000) { b.puts().size == 2 }
        assertEquals("/clip/v2/resource/scene/scene-3", b.puts().last().path)
    }

    @Test fun centroDeControl_modoClaroYReposo() {
        launch()
        activityKey(KeyEvent.KEYCODE_MENU)
        compose.waitUntil(5_000) { runCatching { quick("Oscuro").assertExists() }.isSuccess }
        quick("Oscuro").performSemanticsAction(SemanticsActions.OnClick)
        assertFalse(mibox.darkMode)
        assertFalse(mibox.prefs.darkMode)
        quick("Reposo").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitUntil(5_000) { runCatching { compose.onNodeWithTag("screensaver").assertExists() }.isSuccess }
        compose.onNodeWithTag("controlCenter").assertDoesNotExist()
        activityKey(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.waitForIdle()
        compose.onNodeWithTag("screensaver").assertDoesNotExist()
    }

    @Test fun centroDeControl_apagarYTemporizador() {
        launch()
        activityKey(KeyEvent.KEYCODE_MENU)
        compose.waitUntil(5_000) { runCatching { compose.onNodeWithTag("cc:timer").assertExists() }.isSuccess }
        compose.onNodeWithTag("cc:timer").performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithText("En 15 min").assertExists()
        assertEquals(15, mibox.sleep.timerMinutes)
        compose.onNodeWithTag("cc:timer").performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithText("En 30 min").assertExists()
        mibox.sleep.cancelTimer()
        // Sin el asistente global no se puede suspender: lo explica
        compose.onNodeWithTag("cc:apagar").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitUntil(5_000) { runCatching { compose.onNodeWithText("asistente en cualquier app", substring = true).assertExists() }.isSuccess }
    }

    @Test fun centroDeControl_ajustesAbreAjustesDelLauncher() {
        launch()
        activityKey(KeyEvent.KEYCODE_MENU)
        compose.waitUntil(5_000) { runCatching { quick("Ajustes").assertExists() }.isSuccess }
        quick("Ajustes").performSemanticsAction(SemanticsActions.OnClick)
        waitText("Philips Hue")
        compose.onNodeWithTag("controlCenter").assertDoesNotExist()
    }

    @Test fun seguirViendo_apareceEnElTopShelfYAbreElCapitulo() {
        FakeTvProvider.install()
        shadowOf(app).grantPermissions(WatchNextRepository.PERMISSION)
        launch()
        waitText("Seguir viendo")
        // Arriba desde la primera fila de apps entra al Top Shelf
        compose.onRoot().performKeyInput { pressKey(Key.DirectionUp) }
        compose.waitForIdle()
        compose.onNode(hasText("Receta de pizza") and hasClickAction()).assertIsFocused()
        compose.onRoot().performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNode(hasText("Stranger Things") and hasClickAction()).assertIsFocused()
        compose.onNodeWithText("T2 E5 · Dig Dug").assertExists()
        compose.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }
        val started = shadowOf(app).nextStartedActivity
        assertEquals("nflx", started.scheme)
        assertEquals("com.netflix.ninja", started.`package`)
    }

    @Test fun seguirViendo_sinPermisoNoSeMuestraYAjustesPermiteDarlo() {
        FakeTvProvider.install()
        launch()
        compose.waitForIdle()
        compose.onNodeWithText("Seguir viendo").assertDoesNotExist()
        compose.onNode(androidx.compose.ui.test.hasContentDescription("Ajustes") and hasClickAction()).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitUntil(5_000) { runCatching { compose.onNode(hasText("Seguir viendo") and hasText("Dar permiso")).assertExists() }.isSuccess }
        select("Seguir viendo")
        scenario.onActivity {
            assertEquals(WatchNextRepository.PERMISSION, shadowOf(it).lastRequestedPermission.requestedPermissions.single())
        }
    }
}
