package com.jsalinas.miboxos.hue

import com.jsalinas.miboxos.testutil.FakeHueBridge
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class HueClientTest {
    private val bridge = FakeHueBridge()
    private val info = BridgeInfo(bridge.host, bridge.bridgeId, bridge.port)

    @After fun tearDown() = bridge.shutdown()

    @Test fun `vincular devuelve null hasta que se aprieta el boton y despues la app key`() = runBlocking {
        bridge.pairRejections = 1
        val client = HueClient(info)
        assertNull(client.pair())
        assertEquals(bridge.appKey, client.pair())
        val body = JSONObject(bridge.requests.last().body.readUtf8())
        assertEquals("miboxos#mibox", body.getString("devicetype"))
    }

    @Test fun `lee habitaciones, zonas, luces y escenas`() = runBlocking {
        val home = HueClient(info, bridge.appKey).home()
        assertEquals(listOf("Todas", "Living", "Dormitorio", "Planta baja"), home.groups.map { it.name })
        val living = home.groups.first { it.name == "Living" }
        assertEquals("gl-living", living.groupedLightId)
        assertEquals(listOf("light-1", "light-2"), living.lightIds)
        assertEquals(listOf("light-1", "light-2", "light-3"), home.groups.first().lightIds)
        assertEquals("gl-all", home.groups.first().groupedLightId)
        assertEquals(listOf("light-1", "light-2"), home.groups.first { it.name == "Planta baja" }.lightIds)
        assertEquals(3, home.lights.size)
        assertEquals(listOf("Living", "Dormitorio", "Living"), home.scenes.map { it.groupName })
        assertTrue(home.describe().contains("Relajarse (Dormitorio)"))
    }

    @Test fun `apagar un grupo usa grouped_light`() = runBlocking {
        val client = HueClient(info, bridge.appKey)
        val living = client.home().groups.first { it.name == "Living" }
        client.set(living, on = false, brightness = null, colorHex = null)
        val put = bridge.puts().single()
        assertEquals("/clip/v2/resource/grouped_light/gl-living", put.path)
        assertEquals(false, JSONObject(put.body.readUtf8()).getJSONObject("on").getBoolean("on"))
    }

    @Test fun `brillo sin encender explicito tambien prende`() = runBlocking {
        val client = HueClient(info, bridge.appKey)
        val living = client.home().groups.first { it.name == "Living" }
        client.set(living, on = null, brightness = 30, colorHex = null)
        val body = JSONObject(bridge.puts().single().body.readUtf8())
        assertEquals(30.0, body.getJSONObject("dimming").getDouble("brightness"), 0.0)
        assertTrue(body.getJSONObject("on").getBoolean("on"))
    }

    @Test fun `el color se manda luz por luz con xy`() = runBlocking {
        val client = HueClient(info, bridge.appKey)
        val living = client.home().groups.first { it.name == "Living" }
        client.set(living, on = null, brightness = null, colorHex = "#FF0000")
        val puts = bridge.puts()
        assertEquals(listOf("/clip/v2/resource/light/light-1", "/clip/v2/resource/light/light-2"), puts.map { it.path })
        val xy = JSONObject(puts.first().body.readUtf8()).getJSONObject("color").getJSONObject("xy")
        assertEquals(0.64, xy.getDouble("x"), 0.01)
        assertEquals(0.33, xy.getDouble("y"), 0.01)
    }

    @Test fun `una luz individual sin grupo se controla directo`() = runBlocking {
        val client = HueClient(info, bridge.appKey)
        val velador = client.home().lights.first { it.name == "Velador" }
        client.set(velador, on = true, brightness = null, colorHex = null)
        assertEquals("/clip/v2/resource/light/light-3", bridge.puts().single().path)
    }

    @Test fun `activar escena`() = runBlocking {
        val client = HueClient(info, bridge.appKey)
        client.recall(client.home().scenes.first { it.name == "Cine" })
        val put = bridge.puts().single()
        assertEquals("/clip/v2/resource/scene/scene-3", put.path)
        assertEquals("active", JSONObject(put.body.readUtf8()).getJSONObject("recall").getString("action"))
    }

    @Test fun `app key invalida da error claro`() = runBlocking {
        try {
            HueClient(info, "otra").home()
            fail("debería fallar")
        } catch (e: IllegalStateException) {
            assertTrue(e.message!!.contains("403"))
        }
    }

    @Test fun `rechaza un servidor que no es un Hue Bridge`() = runBlocking {
        val impostor = FakeHueBridge(bridgeId = "otro", issuerOrg = "Evil Corp")
        try {
            HueClient(BridgeInfo(impostor.host, bridge.bridgeId, impostor.port), "x").home()
            fail("debería rechazar el certificado")
        } catch (e: javax.net.ssl.SSLException) {
            // esperado
        } finally {
            impostor.shutdown()
        }
    }

    @Test fun `conversion de colores a xy`() {
        val white = hexToXy("#FFFFFF")!!
        assertEquals(0.3127, white.first, 0.002)
        assertEquals(0.329, white.second, 0.002)
        val blue = hexToXy("0000FF")!!
        assertEquals(0.15, blue.first, 0.01)
        assertNull(hexToXy("#000000"))
        assertNull(hexToXy("rojo"))
    }
}
