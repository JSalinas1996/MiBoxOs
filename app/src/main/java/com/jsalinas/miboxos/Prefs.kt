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

    var darkMode: Boolean
        get() = config.getBoolean(KEY_DARK, true)
        set(value) = config.edit().putBoolean(KEY_DARK, value).apply()

    /** Id de fondo: "minimal", un asset ("tierra") o "file:<ruta>" para imágenes propias. */
    var wallpaper: String
        get() = config.getString(KEY_WALLPAPER, "minimal").orEmpty()
        set(value) = config.edit().putString(KEY_WALLPAPER, value).apply()

    var hueBridgeIp: String
        get() = config.getString(KEY_HUE_IP, "").orEmpty()
        set(value) = config.edit().putString(KEY_HUE_IP, value).apply()

    var hueBridgeId: String
        get() = config.getString(KEY_HUE_ID, "").orEmpty()
        set(value) = config.edit().putString(KEY_HUE_ID, value).apply()

    var hueAppKey: String
        get() = config.getString(KEY_HUE_KEY, "").orEmpty()
        set(value) = config.edit().putString(KEY_HUE_KEY, value).apply()

    fun launchCount(packageName: String): Int = launches.getInt(packageName, 0)

    fun recordLaunch(packageName: String) {
        launches.edit().putInt(packageName, launchCount(packageName) + 1).apply()
    }

    companion object {
        const val KEY_API = "gemini_api_key"
        const val KEY_MODEL = "gemini_model"
        private const val KEY_DARK = "dark_mode"
        private const val KEY_WALLPAPER = "wallpaper"
        private const val KEY_HUE_IP = "hue_bridge_ip"
        private const val KEY_HUE_ID = "hue_bridge_id"
        private const val KEY_HUE_KEY = "hue_app_key"
        const val DEFAULT_MODEL = "gemini-3.8-flash"
    }
}
