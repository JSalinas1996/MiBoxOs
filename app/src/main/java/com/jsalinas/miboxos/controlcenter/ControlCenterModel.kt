package com.jsalinas.miboxos.controlcenter

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.jsalinas.miboxos.hue.HueController
import com.jsalinas.miboxos.hue.HueScene
import com.jsalinas.miboxos.hue.HueTarget
import com.jsalinas.miboxos.ui.RoomState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Estado del Centro de control y del modo reposo "a pedido", compartido por el launcher y el
 * asistente global: el que esté en pantalla lo dibuja.
 */
class ControlCenterModel(private val hue: HueController, private val scope: CoroutineScope) {
    var visible by mutableStateOf(false)
        private set
    var screensaverVisible by mutableStateOf(false)
    var rooms by mutableStateOf<List<RoomState>?>(null)
        private set
    var scenes by mutableStateOf<List<String>>(emptyList())
        private set

    private var targets: Map<String, HueTarget> = emptyMap()
    private var sceneMap: Map<String, HueScene> = emptyMap()

    fun open() {
        visible = true
        if (!hue.isPaired) return
        rooms = null
        scope.launch {
            val loaded = runCatching { hue.rooms() }.getOrDefault(emptyList())
            targets = loaded.associate { (t, _) -> t.name to t }
            rooms = loaded.map { (t, on) -> RoomState(t.name, on) }
            val home = runCatching { hue.home() }.getOrNull()
            // Nombre visible "Escena · Habitación" para distinguir escenas con el mismo nombre
            sceneMap = home?.scenes.orEmpty().associateBy { if (it.groupName.isBlank()) it.name else "${it.name} · ${it.groupName}" }
            scenes = sceneMap.keys.toList()
        }
    }

    fun close() {
        visible = false
    }

    /** Cambia al instante en pantalla y después le avisa al bridge; si falla, vuelve atrás. */
    fun toggleRoom(name: String) {
        val target = targets[name] ?: return
        val current = rooms?.firstOrNull { it.name == name } ?: return
        val next = !current.on
        rooms = rooms?.map { if (it.name == name) it.copy(on = next) else it }
        scope.launch {
            runCatching { hue.setOn(target, next) }.onFailure {
                rooms = rooms?.map { r -> if (r.name == name) r.copy(on = current.on) else r }
            }
        }
    }

    fun scene(label: String) {
        val scene = sceneMap[label] ?: return
        scope.launch { runCatching { hue.recall(scene) } }
    }
}
