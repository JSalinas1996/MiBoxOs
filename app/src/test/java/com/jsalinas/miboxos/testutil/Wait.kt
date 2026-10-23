package com.jsalinas.miboxos.testutil

import android.os.Looper
import org.junit.Assert.fail
import org.robolectric.Shadows.shadowOf

/** Procesa el hilo principal hasta que se cumpla la condición (el trabajo de red corre en otros hilos). */
fun waitUntil(timeoutMs: Long = 10_000, what: String = "condición", condition: () -> Boolean) {
    val end = System.currentTimeMillis() + timeoutMs
    while (System.currentTimeMillis() < end) {
        shadowOf(Looper.getMainLooper()).idle()
        if (condition()) return
        Thread.sleep(10)
    }
    fail("Timeout esperando $what")
}
