package com.jsalinas.miboxos.update

import android.app.Application
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import com.jsalinas.miboxos.Endpoints
import com.jsalinas.miboxos.Prefs
import com.jsalinas.miboxos.testutil.waitUntil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class UpdaterTest {
    private val app: Application = ApplicationProvider.getApplicationContext()
    private val server = MockWebServer()
    private val updater by lazy { Updater(app, Prefs(app), CoroutineScope(SupervisorJob() + Dispatchers.Main)) }

    @Before fun setUp() {
        server.start()
        Endpoints.latestRelease = server.url("/releases/latest").toString()
    }

    @After fun tearDown() = server.shutdown()

    private fun release(tag: String) = MockResponse().setBody(
        """{"tag_name":"$tag","assets":[{"name":"notas.txt","browser_download_url":"x"},{"name":"MiBoxOs-$tag.apk","browser_download_url":"https://github.com/x/MiBoxOs-$tag.apk"}]}"""
    )

    private fun finished() = updater.status !is UpdateStatus.Checking && updater.status !is UpdateStatus.Idle

    @Test fun `compara versiones numero por numero`() {
        assertTrue(Updater.isNewer("0.10.0", "0.9.9"))
        assertTrue(Updater.isNewer("1.0", "0.9.9"))
        assertFalse(Updater.isNewer("0.6.0", "0.6.0"))
        assertFalse(Updater.isNewer("0.5.9", "0.6.0"))
    }

    @Test fun `version nueva publicada aparece disponible con su APK`() {
        server.enqueue(release("v99.0.0"))
        updater.check(force = true)
        waitUntil(what = "consulta") { finished() }
        val available = updater.status as UpdateStatus.Available
        assertEquals("99.0.0", available.release.version)
        assertTrue(available.release.apkUrl.endsWith("MiBoxOs-v99.0.0.apk"))
    }

    @Test fun `misma version o sin versiones publicadas esta al dia`() {
        server.enqueue(release("v0.0.1"))
        updater.check(force = true)
        waitUntil(what = "consulta") { finished() }
        assertEquals(UpdateStatus.UpToDate, updater.status)
        server.enqueue(MockResponse().setResponseCode(404))
        updater.check(force = true)
        waitUntil(what = "consulta") { finished() }
        assertEquals(UpdateStatus.UpToDate, updater.status)
    }

    @Test fun `busca solo una vez por dia salvo que se pida`() {
        server.enqueue(release("v0.0.1"))
        updater.check()
        waitUntil(what = "consulta") { finished() }
        updater.check()
        assertEquals(1, server.requestCount)
    }

    @Test fun `sin permiso para instalar abre la pantalla para darlo`() {
        updater.install(Release("99.0.0", "https://x/y.apk"))
        assertEquals(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, shadowOf(app).nextStartedActivity.action)
    }
}
