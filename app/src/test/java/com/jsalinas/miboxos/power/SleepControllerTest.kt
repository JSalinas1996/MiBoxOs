package com.jsalinas.miboxos.power

import android.accessibilityservice.AccessibilityService
import android.os.Looper
import com.jsalinas.miboxos.GlobalAssistant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class SleepControllerTest {
    private val sleep = SleepController(CoroutineScope(SupervisorJob() + Dispatchers.Main))
    private val actions = mutableListOf<Int>()

    @After fun tearDown() {
        GlobalAssistant.globalAction = null
        GlobalAssistant.connected = false
    }

    private fun connect() {
        GlobalAssistant.connected = true
        GlobalAssistant.globalAction = { actions += it; true }
    }

    @Test fun `sin el asistente global explica como habilitar el apagado`() {
        assertTrue(sleep.sleepNow()!!.contains("asistente en cualquier app"))
    }

    @Test fun `apagar ahora suspende el equipo`() {
        connect()
        assertNull(sleep.sleepNow())
        assertEquals(listOf(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN), actions)
    }

    @Test fun `el temporizador apaga a los minutos indicados y se puede cancelar`() {
        connect()
        sleep.startTimer(30)
        assertEquals(30, sleep.timerMinutes)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMinutes(29))
        assertTrue(actions.isEmpty())
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMinutes(2))
        assertEquals(listOf(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN), actions)
        assertNull(sleep.timerMinutes)

        sleep.startTimer(15)
        sleep.cancelTimer()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMinutes(20))
        assertEquals(1, actions.size)
    }

    @Test fun `el Centro de control rota no, 15, 30, 60, 90, no`() {
        val seen = (1..6).map { sleep.cycleTimer(); sleep.timerMinutes }
        assertEquals(listOf(15, 30, 60, 90, null, 15), seen)
        sleep.cancelTimer()
    }
}
