package com.jsalinas.miboxos.screensaver

import android.app.Application
import android.content.Intent
import android.graphics.Bitmap
import android.service.dreams.DreamService
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import com.jsalinas.miboxos.Prefs
import com.jsalinas.miboxos.wallpaper.Wallpaper
import com.jsalinas.miboxos.wallpaper.Wallpapers
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w960dp-h540dp-land-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ScreensaverTest {
    @get:Rule val compose = createComposeRule()
    private val app: Application = ApplicationProvider.getApplicationContext()
    private val prefs = Prefs(app)
    private val wallpapers = Wallpapers(app)

    @After fun tearDown() { wallpapers.userFolder.deleteRecursively() }

    private val images = listOf("Uno", "Dos", "Tres").map { Wallpaper(it, it, Wallpaper.Asset("", "")) }
    private val fakeLoad: suspend (Wallpaper) -> androidx.compose.ui.graphics.ImageBitmap? =
        { Bitmap.createBitmap(16, 9, Bitmap.Config.ARGB_8888).asImageBitmap() }

    @Test fun `pasa a la siguiente imagen cada intervalo y muestra su nombre`() {
        compose.mainClock.autoAdvance = false
        compose.setContent { Screensaver(images, fakeLoad, secondsPerImage = 15) }
        compose.mainClock.advanceTimeBy(1_000)
        compose.onNodeWithText("Uno").assertExists()
        compose.mainClock.advanceTimeBy(15_000)
        compose.onNodeWithText("Dos").assertExists()
        compose.mainClock.advanceTimeBy(15_000)
        compose.onNodeWithText("Tres").assertExists()
        compose.mainClock.advanceTimeBy(15_000)
        compose.onNodeWithText("Uno").assertExists()
    }

    @Test fun `sin imagenes no rompe`() {
        compose.setContent { Screensaver(emptyList(), fakeLoad, secondsPerImage = 15) }
        compose.waitForIdle()
    }

    @Test fun `usa todas las fotos menos Minimal`() {
        val list = screensaverImages(wallpapers, prefs)
        assertEquals(7, list.size)
        assertTrue(list.none { it.id == "minimal" })
    }

    @Test fun `solo mis fotos si hay, y si no hay vuelve a todas`() {
        prefs.screensaverOnlyMine = true
        assertEquals(7, screensaverImages(wallpapers, prefs).size)
        wallpapers.userFolder.mkdirs()
        Bitmap.createBitmap(64, 36, Bitmap.Config.ARGB_8888)
            .compress(Bitmap.CompressFormat.JPEG, 80, wallpapers.userFolder.resolve("playa.jpg").outputStream())
        assertEquals(listOf("playa"), screensaverImages(wallpapers, prefs).map { it.name })
    }

    @Test fun `Android lo reconoce como protector de pantalla`() {
        val services = app.packageManager.queryIntentServices(Intent(DreamService.SERVICE_INTERFACE), 0)
        assertTrue(services.any { it.serviceInfo.name == ImageDream::class.java.name })
        assertEquals(android.Manifest.permission.BIND_DREAM_SERVICE,
            services.first { it.serviceInfo.name == ImageDream::class.java.name }.serviceInfo.permission)
        Robolectric.buildService(ImageDream::class.java).create().destroy()
    }
}
