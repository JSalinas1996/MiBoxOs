package com.jsalinas.miboxos.hue

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.jsalinas.miboxos.Prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

sealed interface HueStatus {
    data object NotPaired : HueStatus
    data object Searching : HueStatus
    data class PressButton(val secondsLeft: Int) : HueStatus
    data class Paired(val summary: String) : HueStatus
    data class Failed(val message: String) : HueStatus
}

/** Vinculación con el bridge y caché de habitaciones/luces/escenas para el asistente. */
class HueController(private val prefs: Prefs, private val scope: CoroutineScope) {
    var status: HueStatus by mutableStateOf(
        if (prefs.hueAppKey.isNotEmpty()) HueStatus.Paired("Vinculado") else HueStatus.NotPaired
    )
        private set

    private var pairing: Job? = null
    private var cached: HueHome? = null
    private var cachedAt = 0L
    private var failedAt = 0L

    val isPaired get() = prefs.hueAppKey.isNotEmpty()

    fun client(): HueClient? =
        if (isPaired) HueClient(BridgeInfo(prefs.hueBridgeIp, prefs.hueBridgeId, prefs.hueBridgePort), prefs.hueAppKey) else null

    /** Busca el bridge y espera hasta 30 s a que se apriete su botón. */
    fun pair() {
        pairing?.cancel()
        pairing = scope.launch {
            status = HueStatus.Searching
            val bridge = discoverBridge()
            if (bridge == null) {
                status = HueStatus.Failed("No encontré el bridge. ¿Está en el mismo WiFi y con internet?")
                return@launch
            }
            val client = HueClient(bridge)
            for (left in 30 downTo 1 step 2) {
                status = HueStatus.PressButton(left)
                val key = runCatching { client.pair() }.getOrElse {
                    status = HueStatus.Failed(it.message ?: "Error al vincular")
                    return@launch
                }
                if (key != null) {
                    prefs.hueBridgeIp = bridge.ip
                    prefs.hueBridgeId = bridge.id
                    prefs.hueBridgePort = bridge.port
                    prefs.hueAppKey = key
                    refreshSummary()
                    return@launch
                }
                delay(2_000)
            }
            status = HueStatus.Failed("No se apretó el botón a tiempo. Probá de nuevo.")
        }
    }

    fun refreshSummary() {
        scope.launch {
            val home = runCatching { home(force = true) }.getOrElse {
                status = HueStatus.Failed("Vinculado, pero no responde: ${it.message}")
                return@launch
            }
            status = home?.let {
                HueStatus.Paired("${it.lights.size} luces · ${it.groups.size - 1} grupos · ${it.scenes.size} escenas")
            } ?: HueStatus.NotPaired
        }
    }

    /**
     * Habitaciones/luces/escenas (caché de 5 min). Si el bridge no respondió hace menos de un minuto,
     * devuelve null sin reintentar, para no demorar cada pedido al asistente con un timeout.
     */
    suspend fun home(force: Boolean = false): HueHome? {
        val client = client() ?: return null
        val now = System.currentTimeMillis()
        if (!force && now - failedAt < RETRY_MS) return null
        if (force || cached == null || now - cachedAt > CACHE_MS) {
            cached = try {
                client.home()
            } catch (e: Exception) {
                failedAt = now
                throw e
            }
            cachedAt = now
            failedAt = 0
        }
        return cached
    }

    /** Grupos (sin "Todas") con su estado, para el Centro de control. */
    suspend fun rooms(): List<Pair<HueTarget, Boolean>> {
        val client = client() ?: return emptyList()
        val home = home(force = true) ?: return emptyList()
        val states = client.groupStates()
        return home.groups.filter { it.name != "Todas" }.map { it to (states[it.groupedLightId] ?: false) }
    }

    suspend fun setOn(target: HueTarget, on: Boolean) {
        client()?.set(target, on = on, brightness = null, colorHex = null)
    }

    suspend fun recall(scene: HueScene) {
        client()?.recall(scene)
    }

    private companion object {
        const val CACHE_MS = 5 * 60_000L
        const val RETRY_MS = 60_000L
    }
}
