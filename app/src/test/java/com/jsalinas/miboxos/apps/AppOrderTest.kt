package com.jsalinas.miboxos.apps

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asImageBitmap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AppOrderTest {
    private fun app(pkg: String) = TvApp(pkg, pkg, Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888).asImageBitmap())
    private val default by lazy { listOf("a", "b", "c", SystemTiles.ASSISTANT, SystemTiles.SETTINGS, "d").map(::app) }
    private fun names(list: List<TvApp>) = list.map { it.packageName }

    @Test fun `sin orden guardado queda el automatico`() {
        assertEquals(names(default), names(AppOrder.arrange(default, emptyList(), emptySet())))
    }

    @Test fun `mover y las apps nuevas van al final`() {
        val moved = AppOrder.move(default, "d", -5)
        assertEquals(listOf("d", "a", "b", "c", SystemTiles.ASSISTANT, SystemTiles.SETTINGS), moved)
        val withNew = default + app("nueva")
        assertEquals(moved + "nueva", names(AppOrder.arrange(withNew, moved, emptySet())))
    }

    @Test fun `mover no se pasa de los bordes`() {
        assertEquals("a", AppOrder.move(default, "a", -1).first())
        assertEquals("d", AppOrder.move(default, "d", 5).last())
    }

    @Test fun `ocultar saca la app pero Ajustes nunca se oculta`() {
        val arranged = AppOrder.arrange(default, emptyList(), setOf("b", SystemTiles.SETTINGS))
        assertFalse("b" in names(arranged))
        assertTrue(SystemTiles.SETTINGS in names(arranged))
        assertFalse(AppOrder.canHide(app(SystemTiles.SETTINGS)))
        assertTrue(AppOrder.canHide(app(SystemTiles.ASSISTANT)))
    }
}
