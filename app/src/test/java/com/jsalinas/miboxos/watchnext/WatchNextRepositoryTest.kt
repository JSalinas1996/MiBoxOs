package com.jsalinas.miboxos.watchnext

import android.app.Application
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
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
class WatchNextRepositoryTest {
    private val app: Application = ApplicationProvider.getApplicationContext()
    private val repo = WatchNextRepository(app)

    @Before fun setUp() = FakeTvProvider.install()

    @Test fun `sin permiso no lee nada`() = runBlocking {
        assertTrue(repo.load().isEmpty())
    }

    @Test fun `lee lo publicado, ordena por lo ultimo visto y oculta lo no navegable`() = runBlocking {
        shadowOf(app).grantPermissions(WatchNextRepository.PERMISSION)
        val items = repo.load()
        assertEquals(listOf("Receta de pizza", "Stranger Things"), items.map { it.title })
        val st = items.last()
        assertEquals("T2 E5 · Dig Dug", st.subtitle)
        assertEquals(0.5f, st.progress!!, 0.001f)
        assertEquals("nflx", st.intent!!.scheme)
        assertEquals("com.netflix.ninja", st.intent!!.`package`)
        assertEquals(Intent.ACTION_VIEW, items.first().intent!!.action)
    }
}
