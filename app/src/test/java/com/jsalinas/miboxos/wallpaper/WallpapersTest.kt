package com.jsalinas.miboxos.wallpaper

import android.app.Application
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WallpapersTest {
    private val context: Application = ApplicationProvider.getApplicationContext()
    private val wallpapers = Wallpapers(context)

    @After fun tearDown() { wallpapers.userFolder.deleteRecursively() }

    @Test fun `todos los fondos incluidos cargan a 1920x1080 con miniatura`() = runBlocking {
        val builtIn = wallpapers.all()
        assertEquals(8, builtIn.size)
        assertEquals("minimal", builtIn.first().id)
        for (wp in builtIn.drop(1)) {
            val full = wallpapers.loadFull(wp)
            assertNotNull("falta ${wp.id}", full)
            assertEquals(1920, full!!.width)
            assertEquals(1080, full.height)
            assertEquals(480, wallpapers.loadThumb(wp)!!.width)
        }
        assertNull(wallpapers.loadFull(builtIn.first()))
    }

    @Test fun `id desconocido vuelve a Minimal`() {
        assertEquals("minimal", wallpapers.byId("borrado").id)
        assertEquals("saturno", wallpapers.byId("saturno").id)
    }

    @Test fun `fotos propias aparecen y una 4K se achica`() = runBlocking {
        wallpapers.userFolder.mkdirs()
        val file = wallpapers.userFolder.resolve("Vacaciones.jpg")
        Bitmap.createBitmap(3840, 2160, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.JPEG, 80, file.outputStream())
        wallpapers.userFolder.resolve("notas.txt").writeText("no es imagen")
        val user = wallpapers.all().last()
        assertEquals("Vacaciones", user.name)
        assertEquals(9, wallpapers.all().size)
        assertEquals(user.id, wallpapers.byId("file:${file.absolutePath}").id)
        val image = wallpapers.loadFull(user)!!
        assertEquals(1920, image.width)
        assertTrue(wallpapers.loadThumb(user)!!.width <= 960)
    }

    @Test fun `foto propia borrada vuelve a Minimal sin romper`() {
        assertEquals("minimal", wallpapers.byId("file:/sdcard/Pictures/MiBoxOs/borrada.jpg").id)
    }

    @Test fun `archivo corrupto no rompe`() = runBlocking {
        wallpapers.userFolder.mkdirs()
        wallpapers.userFolder.resolve("rota.jpg").writeText("basura")
        assertNull(wallpapers.loadFull(wallpapers.all().last()))
    }
}
