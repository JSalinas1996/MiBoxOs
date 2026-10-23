package com.jsalinas.miboxos.apps

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.jsalinas.miboxos.Prefs
import com.jsalinas.miboxos.testutil.FakeApps
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AppRepositoryTest {
    private val context: Application = ApplicationProvider.getApplicationContext()
    private val prefs = Prefs(context)
    private val repo = AppRepository(context, prefs)

    @Test fun `lista apps de TV y de celular sin duplicar ni incluirse a si misma`() = runBlocking {
        FakeApps.installDefaults(context)
        FakeApps.install(context, "com.netflix.ninja", "Netflix", tv = false) // también como app de celular
        FakeApps.install(context, context.packageName, "MiBoxOs")
        val apps = repo.loadApps()
        assertEquals(listOf("Fotos", "Netflix", "Spotify", "YouTube"), apps.map { it.label })
        assertFalse(apps.any { it.packageName == context.packageName })
    }

    @Test fun `cada app tiene arte 16x9 aunque no traiga banner`() = runBlocking {
        FakeApps.installDefaults(context)
        repo.loadApps().forEach {
            assertEquals(320, it.tile.width)
            assertEquals(180, it.tile.height)
        }
    }

    @Test fun `las mas usadas van primero`() = runBlocking {
        FakeApps.installDefaults(context)
        assertTrue(repo.launch("com.spotify.tv.android"))
        assertTrue(repo.launch("com.spotify.tv.android"))
        assertTrue(repo.launch("com.google.android.youtube.tv"))
        assertEquals(listOf("Spotify", "YouTube", "Fotos", "Netflix"), repo.loadApps().map { it.label })
    }

    @Test fun `abrir app de celular usa su launcher normal`() {
        FakeApps.installDefaults(context)
        assertTrue(repo.launch("com.example.phoneonly"))
        assertFalse(repo.launch("com.no.existe"))
    }

    @Test fun `sin apps instaladas no rompe`() = runBlocking {
        assertTrue(repo.loadApps().isEmpty())
    }
}
