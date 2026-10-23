package com.jsalinas.miboxos.assistant

import android.util.Base64
import com.jsalinas.miboxos.Endpoints
import com.jsalinas.miboxos.apps.TvApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class ToolCall(val name: String, val args: JSONObject)

class AssistantReply(val transcript: String, val reply: String, val calls: List<ToolCall>)

/** Manda el audio a Gemini (generateContent) con las acciones disponibles como funciones. */
class GeminiClient(private val apiKey: String, private val model: String) {

    suspend fun interpret(
        wav: ByteArray,
        apps: List<TvApp>,
        hueContext: String?,
        withHue: Boolean = hueContext != null,
        withNavigation: Boolean = false,
    ): AssistantReply = withContext(Dispatchers.IO) {
        val appList = apps.joinToString("\n") { "- ${it.label} (${it.packageName})" }
        val body = JSONObject()
            .put("systemInstruction", JSONObject().put("parts", JSONArray().put(text(SYSTEM_PROMPT))))
            .put("contents", JSONArray().put(
                JSONObject().put("role", "user").put("parts", JSONArray()
                    .put(text("Apps instaladas:\n$appList" + hueContext?.let { "\n\n$it" }.orEmpty()))
                    .put(JSONObject().put("inlineData", JSONObject()
                        .put("mimeType", "audio/wav")
                        .put("data", Base64.encodeToString(wav, Base64.NO_WRAP))))
                )
            ))
            .put("tools", JSONArray().put(JSONObject().put("functionDeclarations", functions(withHue, withNavigation))))
            .put("toolConfig", JSONObject().put("functionCallingConfig", JSONObject().put("mode", "ANY")))

        // Si el modelo está saturado (503/429/500) reintenta una vez y después prueba los modelos de respaldo
        val models = (listOf(model) + FALLBACK_MODELS).distinct()
        var last: Exception? = null
        for ((i, m) in models.withIndex()) {
            repeat(if (i == 0) 2 else 1) { attempt ->
                try {
                    return@withContext parse(post(m, body))
                } catch (e: Overloaded) {
                    last = e
                    if (attempt == 0 && i == 0) delay(1_000)
                }
            }
        }
        throw IllegalStateException("Gemini está saturado en este momento; probá de nuevo en un rato", last)
    }

    private class Overloaded(message: String) : Exception(message)

    private fun post(model: String, body: JSONObject): JSONObject {
        val conn = URL("${Endpoints.gemini}/v1beta/models/$model:generateContent")
            .openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.connectTimeout = 10_000
        conn.readTimeout = 30_000
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("x-goog-api-key", apiKey)
        try {
            conn.outputStream.use { it.write(body.toString().toByteArray()) }
            val code = conn.responseCode
            val response = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                val msg = runCatching { JSONObject(response).getJSONObject("error").getString("message") }
                    .getOrDefault(response.take(200))
                if (code in RETRYABLE) throw Overloaded("Gemini respondió $code: $msg")
                error("Gemini respondió $code: $msg")
            }
            return JSONObject(response)
        } finally {
            conn.disconnect()
        }
    }

    private fun functions(withHue: Boolean, withNavigation: Boolean): JSONArray {
        val all = Tools.declarations()
        if (withHue) Tools.hueDeclarations().let { hue -> for (i in 0 until hue.length()) all.put(hue.get(i)) }
        if (withNavigation) Tools.navigationDeclarations().let { nav -> for (i in 0 until nav.length()) all.put(nav.get(i)) }
        return all
    }

    private fun parse(json: JSONObject): AssistantReply {
        val parts = json.optJSONArray("candidates")?.optJSONObject(0)
            ?.optJSONObject("content")?.optJSONArray("parts") ?: JSONArray()
        var transcript = ""
        var reply = ""
        val calls = mutableListOf<ToolCall>()
        for (i in 0 until parts.length()) {
            val part = parts.getJSONObject(i)
            val call = part.optJSONObject("functionCall")
            if (call != null) {
                val args = call.optJSONObject("args") ?: JSONObject()
                if (call.optString("name") == Tools.SHOW_REPLY) {
                    transcript = args.optString("transcripcion")
                    reply = args.optString("respuesta")
                } else {
                    calls += ToolCall(call.optString("name"), args)
                }
            } else if (!part.optBoolean("thought") && reply.isEmpty()) {
                reply = part.optString("text")
            }
        }
        return AssistantReply(transcript, reply, calls)
    }

    private fun text(value: String) = JSONObject().put("text", value)

    private companion object {
        /** Modelos estables de respaldo si el elegido está saturado. */
        val FALLBACK_MODELS = listOf("gemini-3.6-flash", "gemini-3.5-flash-lite")
        val RETRYABLE = setOf(429, 500, 503)

        val SYSTEM_PROMPT = """
Sos el asistente de voz de un televisor (Xiaomi Mi Box S con Android TV) en Argentina.
Escuchás el audio del usuario y controlás la TV llamando funciones.
Reglas:
- Siempre llamá a ${Tools.SHOW_REPLY} con la transcripción literal y una respuesta muy breve en español rioplatense.
- Para abrir una app usá el nombre de paquete exacto de la lista de apps instaladas. Si no está instalada, decilo en la respuesta.
- Para luces usá los nombres exactos de la lista de Hue. "Prendé las luces" sin aclarar = grupo "Todas".
- Si hay una app en pantalla, "pausá", "seguí", "adelantá" se refieren a lo que se está reproduciendo ahí.
- Si piden buscar algo en YouTube, usá buscar_en_youtube en vez de solo abrir la app.
- Si el pedido no se puede hacer con las funciones disponibles, explicalo en una frase sin inventar acciones.
""".trimIndent()
    }
}
