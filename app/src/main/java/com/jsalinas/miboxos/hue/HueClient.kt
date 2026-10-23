package com.jsalinas.miboxos.hue

import android.annotation.SuppressLint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager
import javax.security.auth.x500.X500Principal
import kotlin.math.pow

data class BridgeInfo(val ip: String, val id: String)

data class HueTarget(val name: String, val groupedLightId: String?, val lightIds: List<String>)

data class HueScene(val id: String, val name: String, val groupName: String)

data class HueHome(
    /** Habitaciones y zonas; "Todas" agrupa la casa entera. */
    val groups: List<HueTarget>,
    val lights: List<HueTarget>,
    val scenes: List<HueScene>,
) {
    fun describe(): String = buildString {
        append("Luces Philips Hue disponibles.\n")
        append("Grupos: ").append(groups.joinToString { it.name }).append('\n')
        append("Luces: ").append(lights.joinToString { it.name }).append('\n')
        if (scenes.isNotEmpty()) append("Escenas: ").append(scenes.joinToString { "${it.name} (${it.groupName})" })
    }
}

/**
 * API v2 local del Hue Bridge (HTTPS en la red de casa, sin nube).
 * El bridge usa un certificado de la CA propia de Signify con CN = id del bridge, que Android no
 * conoce: solo se acepta esa cadena, y solo para la IP del bridge.
 */
class HueClient(private val bridge: BridgeInfo, private val appKey: String = "") {

    /** Pide una app key; devuelve null mientras no se apretó el botón del bridge. */
    suspend fun pair(): String? = withContext(Dispatchers.IO) {
        val body = JSONObject().put("devicetype", "miboxos#mibox").put("generateclientkey", true)
        val result = JSONArray(request("POST", "/api", body)).getJSONObject(0)
        result.optJSONObject("success")?.getString("username")
            ?: result.optJSONObject("error")?.let { err ->
                if (err.optInt("type") == LINK_BUTTON_NOT_PRESSED) null else error(err.optString("description"))
            }
    }

    suspend fun home(): HueHome = withContext(Dispatchers.IO) {
        val devices = get("device")
        // device id -> ids de sus servicios "light"
        val deviceLights = devices.associate { d ->
            d.getString("id") to d.optJSONArray("services").objects()
                .filter { it.optString("rtype") == "light" }.map { it.getString("rid") }
        }
        fun groupedLight(o: JSONObject) = o.optJSONArray("services").objects()
            .firstOrNull { it.optString("rtype") == "grouped_light" }?.getString("rid")

        fun lightsOf(o: JSONObject) = o.optJSONArray("children").objects().flatMap { child ->
            when (child.optString("rtype")) {
                "device" -> deviceLights[child.getString("rid")].orEmpty()
                "light" -> listOf(child.getString("rid"))
                else -> emptyList()
            }
        }

        val rooms = (get("room") + get("zone")).map { HueTarget(it.name(), groupedLight(it), lightsOf(it)) }
        val all = get("bridge_home").firstOrNull()?.let { HueTarget("Todas", groupedLight(it), rooms.flatMap { r -> r.lightIds }.distinct()) }
        val lights = get("light").map { HueTarget(it.name(), null, listOf(it.getString("id"))) }
        val groupNames = (get("room") + get("zone")).associate { it.getString("id") to it.name() }
        val scenes = get("scene").map {
            HueScene(it.getString("id"), it.name(), groupNames[it.optJSONObject("group")?.optString("rid")].orEmpty())
        }
        HueHome(listOfNotNull(all) + rooms, lights, scenes)
    }

    /** Prende/apaga y regula brillo/color de un grupo o una luz. */
    suspend fun set(target: HueTarget, on: Boolean?, brightness: Int?, colorHex: String?) = withContext(Dispatchers.IO) {
        val state = JSONObject()
        on?.let { state.put("on", JSONObject().put("on", it)) }
        brightness?.let {
            state.put("dimming", JSONObject().put("brightness", it.coerceIn(1, 100).toDouble()))
            if (on == null) state.put("on", JSONObject().put("on", true))
        }
        val color = colorHex?.let { hexToXy(it) }
        // El color se manda luz por luz: no todos los firmwares aceptan color en grouped_light
        if (color != null) {
            val lightState = JSONObject(state.toString())
                .put("color", JSONObject().put("xy", JSONObject().put("x", color.first).put("y", color.second)))
            if (!lightState.has("on")) lightState.put("on", JSONObject().put("on", true))
            target.lightIds.forEach { request("PUT", "/clip/v2/resource/light/$it", lightState) }
        } else if (state.length() > 0) {
            if (target.groupedLightId != null) {
                request("PUT", "/clip/v2/resource/grouped_light/${target.groupedLightId}", state)
            } else {
                target.lightIds.forEach { request("PUT", "/clip/v2/resource/light/$it", state) }
            }
        }
    }

    suspend fun recall(scene: HueScene) = withContext(Dispatchers.IO) {
        request("PUT", "/clip/v2/resource/scene/${scene.id}", JSONObject().put("recall", JSONObject().put("action", "active")))
    }

    private fun get(type: String): List<JSONObject> =
        JSONObject(request("GET", "/clip/v2/resource/$type", null)).optJSONArray("data").objects()

    private fun request(method: String, path: String, body: JSONObject?): String {
        val conn = URL("https://${bridge.ip}$path").openConnection() as HttpsURLConnection
        conn.sslSocketFactory = sslContext.socketFactory
        conn.hostnameVerifier = hostnameVerifier
        conn.requestMethod = method
        conn.connectTimeout = 4_000
        conn.readTimeout = 8_000
        if (appKey.isNotEmpty()) conn.setRequestProperty("hue-application-key", appKey)
        try {
            if (body != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                conn.outputStream.use { it.write(body.toString().toByteArray()) }
            }
            val code = conn.responseCode
            val text = ((if (code < 400) conn.inputStream else conn.errorStream)?.bufferedReader()?.use { it.readText() }).orEmpty()
            if (code >= 400) error("Hue respondió $code: ${text.take(200)}")
            return text
        } finally {
            conn.disconnect()
        }
    }

    // El certificado no lleva la IP; la identidad la valida BridgeTrustManager
    private val hostnameVerifier = HostnameVerifier { host, _ -> host == bridge.ip }

    private val sslContext: SSLContext by lazy {
        SSLContext.getInstance("TLS").apply { init(null, arrayOf(BridgeTrustManager(bridge.id)), null) }
    }

    private companion object {
        const val LINK_BUTTON_NOT_PRESSED = 101
    }
}

/** Acepta solo certificados emitidos por la CA de Philips Hue o el autofirmado del propio bridge. */
@SuppressLint("CustomX509TrustManager")
private class BridgeTrustManager(private val bridgeId: String) : X509TrustManager {
    override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
        val leaf = chain.firstOrNull() ?: throw CertificateException("Sin certificado")
        leaf.checkValidity()
        val issuer = leaf.issuerX500Principal.getName(X500Principal.RFC2253)
        val fromHueCa = issuer.contains("O=Philips Hue", ignoreCase = true)
        val selfSignedByBridge = bridgeId.isNotEmpty() && leaf.commonName().equals(bridgeId, ignoreCase = true)
        if (!fromHueCa && !selfSignedByBridge) throw CertificateException("No es un Hue Bridge: $issuer")
    }

    override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) =
        throw CertificateException("No se usa")

    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
}

private fun X509Certificate.commonName(): String =
    subjectX500Principal.getName(X500Principal.RFC2253).split(',')
        .map { it.trim() }.firstOrNull { it.startsWith("CN=", ignoreCase = true) }?.substring(3).orEmpty()

private fun JSONObject.name() = optJSONObject("metadata")?.optString("name").orEmpty()

private fun JSONArray?.objects(): List<JSONObject> =
    if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }

/** Convierte "#RRGGBB" al espacio xy de Hue (sRGB → XYZ, Wide RGB D65). */
internal fun hexToXy(hex: String): Pair<Double, Double>? {
    val clean = hex.removePrefix("#")
    if (clean.length != 6) return null
    val rgb = runCatching { clean.toLong(16) }.getOrNull() ?: return null
    fun lin(c: Long): Double {
        val v = c / 255.0
        return if (v > 0.04045) ((v + 0.055) / 1.055).pow(2.4) else v / 12.92
    }
    val r = lin(rgb shr 16 and 0xFF)
    val g = lin(rgb shr 8 and 0xFF)
    val b = lin(rgb and 0xFF)
    val x = r * 0.4124 + g * 0.3576 + b * 0.1805
    val y = r * 0.2126 + g * 0.7152 + b * 0.0722
    val z = r * 0.0193 + g * 0.1192 + b * 0.9505
    val sum = x + y + z
    if (sum == 0.0) return null
    return x / sum to y / sum
}

/** Busca el bridge con el servicio de descubrimiento de Philips (requiere que el bridge tenga internet). */
suspend fun discoverBridge(): BridgeInfo? = withContext(Dispatchers.IO) {
    runCatching {
        val conn = URL("https://discovery.meethue.com/").openConnection() as HttpURLConnection
        conn.connectTimeout = 5_000
        conn.readTimeout = 5_000
        val text = conn.inputStream.bufferedReader().use { it.readText() }
        conn.disconnect()
        JSONArray(text).optJSONObject(0)?.let { BridgeInfo(it.getString("internalipaddress"), it.optString("id")) }
    }.getOrNull()
}
