package com.jsalinas.miboxos

import android.content.Context

/** Configuración persistente: API key de Gemini, modelo y uso de apps para ordenar el inicio. */
class Prefs(context: Context) {
    private val config = context.getSharedPreferences("config", Context.MODE_PRIVATE)
    private val launches = context.getSharedPreferences("launches", Context.MODE_PRIVATE)

    var geminiApiKey: String
        get() = config.getString(KEY_API, "").orEmpty()
        set(value) = config.edit().putString(KEY_API, value.trim()).apply()

    var geminiModel: String
        get() = config.getString(KEY_MODEL, DEFAULT_MODEL).orEmpty().ifBlank { DEFAULT_MODEL }
        set(value) = config.edit().putString(KEY_MODEL, value.trim()).apply()

    fun launchCount(packageName: String): Int = launches.getInt(packageName, 0)

    fun recordLaunch(packageName: String) {
        launches.edit().putInt(packageName, launchCount(packageName) + 1).apply()
    }

    companion object {
        const val KEY_API = "gemini_api_key"
        const val KEY_MODEL = "gemini_model"
        const val DEFAULT_MODEL = "gemini-3.8-flash"
    }
}
