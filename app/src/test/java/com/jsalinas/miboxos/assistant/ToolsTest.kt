package com.jsalinas.miboxos.assistant

import android.app.Application
import android.app.SearchManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import com.jsalinas.miboxos.Prefs
import com.jsalinas.miboxos.apps.AppRepository
import com.jsalinas.miboxos.hue.HueController
import com.jsalinas.miboxos.testutil.FakeApps
import com.jsalinas.miboxos.testutil.FakeHueBridge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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
class ToolsTest {
    private val context: Application = ApplicationProvider.getApplicationContext()
    private val prefs = Prefs(context)
    private val repo = AppRepository(context, prefs)
    private val hue = HueController(prefs, CoroutineScope(Dispatchers.Main))
    private var bridge: FakeHueBridge? = null

    @Before fun setUp() = FakeApps.installDefaults(context)

    @After fun tearDown() { bridge?.shutdown() }

    private fun run(vararg calls: Pair<String, JSONObject>) = runBlocking {
        Tools.execute(context, repo, hue, calls.map { ToolCall(it.first, it.second) })
    }

    private fun nextStarted(): Intent? = shadowOf(context).nextStartedActivity

    @Test fun `abrir app instalada la lanza y cuenta el uso`() {
        val errors = run("abrir_app" to JSONObject().put("paquete", "com.netflix.ninja"))
        assertTrue(errors.isEmpty())
        assertEquals("com.netflix.ninja", nextStarted()!!.component!!.packageName)
        assertEquals(1, prefs.launchCount("com.netflix.ninja"))
    }

    @Test fun `abrir app que no existe avisa`() {
        val errors = run("abrir_app" to JSONObject().put("paquete", "com.no.existe"))
        assertEquals(listOf("No encontré la app com.no.existe"), errors)
    }

    @Test fun `buscar en YouTube usa la busqueda de la app`() {
        shadowOf(context.packageManager).addIntentFilterForActivity(
            ComponentName("com.google.android.youtube.tv", "com.google.android.youtube.tv.Main"),
            IntentFilter(Intent.ACTION_SEARCH),
        )
        val errors = run("buscar_en_youtube" to JSONObject().put("consulta", "recetas de pizza"))
        assertTrue(errors.isEmpty())
        val intent = nextStarted()!!
        assertEquals(Intent.ACTION_SEARCH, intent.action)
        assertEquals("recetas de pizza", intent.getStringExtra(SearchManager.QUERY))
    }

    @Test fun `buscar en YouTube sin YouTube avisa claro`() {
        shadowOf(context.packageManager).deletePackage("com.google.android.youtube.tv")
        val errors = run("buscar_en_youtube" to JSONObject().put("consulta", "x"))
        assertEquals(listOf("YouTube no está instalado"), errors)
    }

    @Test fun `multimedia y volumen no fallan y el volumen cambia`() {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, 5, 0)
        val errors = run(
            "control_multimedia" to JSONObject().put("accion", "pausa"),
            "control_multimedia" to JSONObject().put("accion", "siguiente"),
            "volumen" to JSONObject().put("accion", "subir").put("pasos", 3),
        )
        assertTrue(errors.toString(), errors.isEmpty())
        assertEquals(8, audio.getStreamVolume(AudioManager.STREAM_MUSIC))
        assertTrue(run("volumen" to JSONObject().put("accion", "bajar")).isEmpty())
        assertEquals(6, audio.getStreamVolume(AudioManager.STREAM_MUSIC))
        assertTrue(run("volumen" to JSONObject().put("accion", "silenciar")).isEmpty())
    }

    @Test fun `abrir ajustes del sistema`() {
        assertTrue(run("abrir_ajustes" to JSONObject()).isEmpty())
        assertEquals(Settings.ACTION_SETTINGS, nextStarted()!!.action)
    }

    @Test fun `accion desconocida avisa`() {
        assertEquals(listOf("Acción desconocida: hackear"), run("hackear" to JSONObject()))
    }

    @Test fun `luces sin Hue vinculado avisa`() {
        assertEquals(listOf("Philips Hue no está vinculado"), run("luces" to JSONObject().put("objetivo", "Living").put("encender", true)))
    }

    private fun pairBridge(): FakeHueBridge {
        val b = FakeHueBridge().also { bridge = it }
        prefs.hueBridgeIp = b.host
        prefs.hueBridgeId = b.bridgeId
        prefs.hueBridgePort = b.port
        prefs.hueAppKey = b.appKey
        return b
    }

    @Test fun `luces del living en calido al 40`() {
        val b = pairBridge()
        val errors = run("luces" to JSONObject().put("objetivo", "living").put("brillo", 40).put("color", "#FFB46B"))
        assertTrue(errors.toString(), errors.isEmpty())
        assertEquals(listOf("/clip/v2/resource/light/light-1", "/clip/v2/resource/light/light-2"), b.puts().map { it.path })
        val body = JSONObject(b.puts().first().body.readUtf8())
        assertEquals(40.0, body.getJSONObject("dimming").getDouble("brightness"), 0.0)
        assertTrue(body.getJSONObject("on").getBoolean("on"))
    }

    @Test fun `apagar todas las luces`() {
        val b = pairBridge()
        assertTrue(run("luces" to JSONObject().put("objetivo", "Todas").put("encender", false)).isEmpty())
        assertEquals("/clip/v2/resource/grouped_light/gl-all", b.puts().single().path)
    }

    @Test fun `luz o grupo inexistente avisa`() {
        pairBridge()
        assertEquals(listOf("No encontré \"Cocina\" en Hue"), run("luces" to JSONObject().put("objetivo", "Cocina").put("encender", true)))
    }

    @Test fun `escena por nombre y por habitacion`() {
        val b = pairBridge()
        assertTrue(run("escena_hue" to JSONObject().put("escena", "relajarse").put("grupo", "Dormitorio")).isEmpty())
        assertEquals("/clip/v2/resource/scene/scene-2", b.puts().last().path)
        assertTrue(run("escena_hue" to JSONObject().put("escena", "Cine")).isEmpty())
        assertEquals("/clip/v2/resource/scene/scene-3", b.puts().last().path)
        assertEquals(listOf("No encontré la escena \"Fiesta\""), run("escena_hue" to JSONObject().put("escena", "Fiesta")))
    }

    @Test fun `bridge apagado da error y no cuelga`() {
        val b = pairBridge()
        b.shutdown()
        val errors = run("luces" to JSONObject().put("objetivo", "Living").put("encender", true))
        assertEquals(1, errors.size)
    }
}
