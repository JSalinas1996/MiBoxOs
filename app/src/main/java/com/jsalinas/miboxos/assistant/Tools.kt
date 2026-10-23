package com.jsalinas.miboxos.assistant

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.os.SystemClock
import android.provider.Settings
import android.view.KeyEvent
import com.jsalinas.miboxos.apps.AppRepository
import org.json.JSONArray
import org.json.JSONObject

/** Declaración (para Gemini) y ejecución (en la Mi Box) de las acciones del asistente. */
object Tools {
    const val SHOW_REPLY = "mostrar_respuesta"
    private const val YOUTUBE_TV = "com.google.android.youtube.tv"

    fun declarations(): JSONArray = JSONArray()
        .put(fn(SHOW_REPLY, "Muestra en pantalla lo que dijo el usuario y la respuesta.",
            "transcripcion" to str("Lo que dijo el usuario, literal."),
            "respuesta" to str("Respuesta breve para mostrar.")))
        .put(fn("abrir_app", "Abre una app instalada.",
            "paquete" to str("Nombre de paquete exacto, de la lista de apps instaladas.")))
        .put(fn("buscar_en_youtube", "Abre YouTube con una búsqueda.",
            "consulta" to str("Texto a buscar.")))
        .put(fn("control_multimedia", "Controla lo que se está reproduciendo.",
            "accion" to enumStr("play_pausa", "play", "pausa", "siguiente", "anterior", "adelantar", "retroceder", "detener")))
        .put(fn("volumen", "Cambia el volumen.",
            "accion" to enumStr("subir", "bajar", "silenciar", "activar_sonido"),
            "pasos" to JSONObject().put("type", "INTEGER").put("description", "Cuántos pasos subir o bajar (1-10).")))
        .put(fn("abrir_ajustes", "Abre los ajustes del sistema."))

    /** Ejecuta las llamadas; devuelve los errores para mostrarlos. */
    fun execute(context: Context, repo: AppRepository, calls: List<ToolCall>): List<String> {
        val errors = mutableListOf<String>()
        for (call in calls) {
            runCatching {
                when (call.name) {
                    "abrir_app" -> {
                        val pkg = call.args.optString("paquete")
                        if (!repo.launch(pkg)) errors += "No encontré la app $pkg"
                    }
                    "buscar_en_youtube" -> searchYouTube(context, call.args.optString("consulta"))
                    "control_multimedia" -> media(context, call.args.optString("accion"))
                    "volumen" -> volume(context, call.args.optString("accion"), call.args.optInt("pasos", 2))
                    "abrir_ajustes" -> context.startActivity(
                        Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    else -> errors += "Acción desconocida: ${call.name}"
                }
            }.onFailure { errors += "Falló ${call.name}: ${it.message}" }
        }
        return errors
    }

    private fun searchYouTube(context: Context, query: String) {
        val search = Intent(Intent.ACTION_SEARCH)
            .setPackage(YOUTUBE_TV)
            .putExtra(SearchManager.QUERY, query)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (search.resolveActivity(context.packageManager) != null) {
            context.startActivity(search)
        } else {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=" + Uri.encode(query)))
                    .setPackage(YOUTUBE_TV)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    private fun media(context: Context, action: String) {
        val key = when (action) {
            "play" -> KeyEvent.KEYCODE_MEDIA_PLAY
            "pausa" -> KeyEvent.KEYCODE_MEDIA_PAUSE
            "siguiente" -> KeyEvent.KEYCODE_MEDIA_NEXT
            "anterior" -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
            "adelantar" -> KeyEvent.KEYCODE_MEDIA_FAST_FORWARD
            "retroceder" -> KeyEvent.KEYCODE_MEDIA_REWIND
            "detener" -> KeyEvent.KEYCODE_MEDIA_STOP
            else -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
        }
        val audio = context.getSystemService(AudioManager::class.java)
        val now = SystemClock.uptimeMillis()
        audio.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, key, 0))
        audio.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, key, 0))
    }

    private fun volume(context: Context, action: String, steps: Int) {
        val audio = context.getSystemService(AudioManager::class.java)
        val stream = AudioManager.STREAM_MUSIC
        when (action) {
            "silenciar" -> audio.adjustStreamVolume(stream, AudioManager.ADJUST_MUTE, AudioManager.FLAG_SHOW_UI)
            "activar_sonido" -> audio.adjustStreamVolume(stream, AudioManager.ADJUST_UNMUTE, AudioManager.FLAG_SHOW_UI)
            else -> {
                val dir = if (action == "bajar") AudioManager.ADJUST_LOWER else AudioManager.ADJUST_RAISE
                repeat(steps.coerceIn(1, 10)) { audio.adjustStreamVolume(stream, dir, AudioManager.FLAG_SHOW_UI) }
            }
        }
    }

    private fun str(description: String) = JSONObject().put("type", "STRING").put("description", description)

    private fun enumStr(vararg values: String) =
        JSONObject().put("type", "STRING").put("enum", JSONArray(values.toList()))

    private fun fn(name: String, description: String, vararg params: Pair<String, JSONObject>): JSONObject {
        val decl = JSONObject().put("name", name).put("description", description)
        if (params.isNotEmpty()) {
            val props = JSONObject()
            params.forEach { (k, v) -> props.put(k, v) }
            decl.put("parameters", JSONObject()
                .put("type", "OBJECT")
                .put("properties", props)
                .put("required", JSONArray(params.map { it.first }.filter { it != "pasos" })))
        }
        return decl
    }
}
