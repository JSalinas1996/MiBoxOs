package com.jsalinas.miboxos.safety

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import com.jsalinas.miboxos.Prefs

/**
 * Red de seguridad del launcher. Si MiBoxOs se cierra 3 veces en 2 minutos, el botón Home pasa a
 * una pantalla de emergencia simple (sin Compose ni fondos) en vez de dejar la TV en negro en un
 * bucle de cierres. Desde ahí se reintenta MiBoxOs o se abren las apps.
 */
object CrashGuard {
    private const val WINDOW_MS = 2 * 60_000L
    private const val MAX_CRASHES = 3

    fun install(context: Context, prefs: Prefs) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { record(context, prefs, System.currentTimeMillis()) }
            previous?.uncaughtException(thread, error)
        }
    }

    /** Registra un cierre; devuelve true si pasó a modo seguro. */
    fun record(context: Context, prefs: Prefs, now: Long): Boolean {
        val recent = (prefs.crashTimes + now).filter { now - it < WINDOW_MS }
        prefs.crashTimes = recent
        if (recent.size >= MAX_CRASHES) {
            setSafeMode(context, true)
            return true
        }
        return false
    }

    fun isSafeMode(context: Context): Boolean =
        context.packageManager.getComponentEnabledSetting(safeHome(context)) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED

    /** Cambia qué pantalla atiende el botón Home: la de emergencia o la normal. */
    fun setSafeMode(context: Context, safe: Boolean) {
        val pm = context.packageManager
        val (on, off) = if (safe) safeHome(context) to normalHome(context) else normalHome(context) to safeHome(context)
        pm.setComponentEnabledSetting(on, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
        pm.setComponentEnabledSetting(off, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
    }

    fun reset(context: Context, prefs: Prefs) {
        prefs.crashTimes = emptyList()
        setSafeMode(context, false)
    }

    private fun normalHome(context: Context) = ComponentName(context, "com.jsalinas.miboxos.HomeAlias")
    private fun safeHome(context: Context) = ComponentName(context, "com.jsalinas.miboxos.SafeHomeAlias")
}
