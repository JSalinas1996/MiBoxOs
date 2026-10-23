package com.jsalinas.miboxos.testutil

import com.jsalinas.miboxos.Endpoints
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.CopyOnWriteArrayList

/** API de Gemini falsa: responde las llamadas a funciones que el test configure. */
class FakeGemini {
    val server = MockWebServer()
    val requests: MutableList<RecordedRequest> = CopyOnWriteArrayList()

    @Volatile var response: MockResponse = reply("hola", "Hola.")

    init {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                requests += request
                return response
            }
        }
        server.start()
        Endpoints.gemini = server.url("/").toString().trimEnd('/')
    }

    fun lastBody(): JSONObject = JSONObject(requests.last().body.clone().readUtf8())

    fun shutdown() = server.shutdown()

    companion object {
        /** Respuesta con mostrar_respuesta + las acciones dadas (nombre a args). */
        fun reply(transcript: String, text: String, vararg calls: Pair<String, JSONObject>, thought: Boolean = true): MockResponse {
            val parts = JSONArray()
            if (thought) parts.put(JSONObject().put("text", "razonando…").put("thought", true))
            parts.put(JSONObject().put("functionCall", JSONObject().put("name", "mostrar_respuesta")
                .put("args", JSONObject().put("transcripcion", transcript).put("respuesta", text))))
            calls.forEach { (name, args) -> parts.put(JSONObject().put("functionCall", JSONObject().put("name", name).put("args", args))) }
            val body = JSONObject().put("candidates", JSONArray().put(
                JSONObject().put("content", JSONObject().put("role", "model").put("parts", parts))
            ))
            return MockResponse().setHeader("Content-Type", "application/json").setBody(body.toString())
        }

        fun error(code: Int, message: String): MockResponse = MockResponse().setResponseCode(code)
            .setBody(JSONObject().put("error", JSONObject().put("code", code).put("message", message)).toString())
    }
}
