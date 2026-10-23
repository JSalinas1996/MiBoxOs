package com.jsalinas.miboxos.hue

import android.app.Application
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.jsalinas.miboxos.Endpoints
import com.jsalinas.miboxos.Prefs
import com.jsalinas.miboxos.testutil.FakeHueBridge
import com.jsalinas.miboxos.testutil.waitUntil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class HueControllerTest {
    private val context: Application = ApplicationProvider.getApplicationContext()
    private val prefs = Prefs(context)
    private val bridge = FakeHueBridge()
    private val discovery = MockWebServer()
    private val controller = HueController(prefs, CoroutineScope(SupervisorJob() + Dispatchers.Main))

    @Before fun setUp() {
        discovery.enqueue(MockResponse().setBody(bridge.discoveryJson()))
        discovery.start()
        Endpoints.hueDiscovery = discovery.url("/").toString()
    }

    @After fun tearDown() {
        bridge.shutdown()
        discovery.shutdown()
    }

    @Test fun `vincular - busca, espera el boton y guarda la key`() {
        bridge.pairRejections = 2
        controller.pair()
        waitUntil(what = "pedido de botón") { controller.status is HueStatus.PressButton }
        // Los reintentos esperan 2 s entre sí: se adelanta el reloj del hilo principal
        repeat(3) {
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2))
            Thread.sleep(300)
        }
        waitUntil(what = "vinculado") { controller.status is HueStatus.Paired }
        assertEquals("3 luces · 3 habitaciones/zonas · 3 escenas", (controller.status as HueStatus.Paired).summary)
        assertEquals(bridge.appKey, prefs.hueAppKey)
        assertEquals(bridge.port, prefs.hueBridgePort)
        assertTrue(controller.isPaired)
    }

    @Test fun `sin bridge en la red avisa`() {
        discovery.shutdown()
        val empty = MockWebServer().apply { enqueue(MockResponse().setBody("[]")); start() }
        Endpoints.hueDiscovery = empty.url("/").toString()
        controller.pair()
        waitUntil(what = "falla") { controller.status is HueStatus.Failed }
        assertTrue((controller.status as HueStatus.Failed).message.startsWith("No encontré el bridge"))
        empty.shutdown()
    }

    @Test fun `si nadie aprieta el boton en 30 s se rinde`() {
        bridge.pairRejections = 1_000
        controller.pair()
        waitUntil(what = "pedido de botón") { controller.status is HueStatus.PressButton }
        repeat(16) {
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2))
            Thread.sleep(150)
        }
        waitUntil(what = "falla") { controller.status is HueStatus.Failed }
        assertTrue((controller.status as HueStatus.Failed).message.contains("botón"))
        assertTrue(!controller.isPaired)
    }

    @Test fun `bridge caido no demora cada pedido al asistente`() = kotlinx.coroutines.runBlocking {
        prefs.hueBridgeIp = bridge.host
        prefs.hueBridgePort = bridge.port
        prefs.hueAppKey = bridge.appKey
        bridge.shutdown()
        assertTrue(runCatching { controller.home() }.isFailure)
        val start = System.currentTimeMillis()
        assertEquals(null, controller.home())
        assertTrue(System.currentTimeMillis() - start < 100)
    }

    @Test fun `vinculado pero bridge apagado muestra error`() {
        prefs.hueBridgeIp = bridge.host
        prefs.hueBridgePort = bridge.port
        prefs.hueAppKey = bridge.appKey
        bridge.shutdown()
        controller.refreshSummary()
        waitUntil(what = "falla") { controller.status is HueStatus.Failed }
        assertTrue((controller.status as HueStatus.Failed).message.startsWith("Vinculado, pero no responde"))
    }
}
