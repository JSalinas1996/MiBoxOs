package com.jsalinas.miboxos

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.jsalinas.miboxos.apps.AppRepository
import com.jsalinas.miboxos.apps.TvApp
import com.jsalinas.miboxos.assistant.AssistantController
import com.jsalinas.miboxos.controlcenter.ControlCenterModel
import com.jsalinas.miboxos.global.MicForegroundService
import com.jsalinas.miboxos.hue.HueController
import com.jsalinas.miboxos.wallpaper.Wallpapers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Estado compartido por el launcher y el asistente global (servicio de accesibilidad):
 * un solo asistente, un solo Hue y los mismos ajustes, estén en la pantalla que estén.
 */
class MiBoxApp : Application() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val prefs by lazy { Prefs(this) }
    val repo by lazy { AppRepository(this, prefs) }
    val hue by lazy { HueController(prefs, scope) }
    val wallpapers by lazy { Wallpapers(this) }

    /** Última lista de apps cargada (para que el asistente sepa qué hay instalado). */
    @Volatile var apps: List<TvApp> = emptyList()

    /** Modo claro/oscuro, observable desde el launcher y desde las capas del servicio global. */
    var darkMode by mutableStateOf(true)

    val assistant by lazy {
        AssistantController(
            this, prefs, repo, hue, scope, apps = { apps },
            screenContext = { GlobalAssistant.screenContext() },
            onRecording = { recording -> if (GlobalAssistant.connected) MicForegroundService.set(this, recording) },
        )
    }

    val controlCenter by lazy { ControlCenterModel(hue, scope) }

    override fun onCreate() {
        super.onCreate()
        darkMode = prefs.darkMode
    }
}

/** Puente al servicio global si está activo (en tests y sin activar, todo devuelve null/false). */
object GlobalAssistant {
    @Volatile var foregroundLabel: String? = null
    /** Observable: el launcher deja de dibujar sus capas cuando el servicio global las dibuja. */
    var connected by mutableStateOf(false)
    @Volatile var globalAction: ((Int) -> Boolean)? = null

    fun screenContext(): String? = foregroundLabel?.let { "App en pantalla ahora: $it" }
}
