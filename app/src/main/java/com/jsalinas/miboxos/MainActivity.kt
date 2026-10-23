package com.jsalinas.miboxos

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.lifecycleScope
import com.jsalinas.miboxos.apps.AppRepository
import com.jsalinas.miboxos.apps.TvApp
import com.jsalinas.miboxos.assistant.AssistantController
import com.jsalinas.miboxos.assistant.AssistantState
import com.jsalinas.miboxos.hue.HueController
import com.jsalinas.miboxos.ui.AssistantOverlay
import com.jsalinas.miboxos.ui.HomeScreen
import com.jsalinas.miboxos.ui.LocalColors
import com.jsalinas.miboxos.ui.SettingsActions
import com.jsalinas.miboxos.ui.SettingsPanel
import com.jsalinas.miboxos.ui.SettingsState
import com.jsalinas.miboxos.ui.appColors
import com.jsalinas.miboxos.wallpaper.Wallpaper
import com.jsalinas.miboxos.wallpaper.Wallpapers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var prefs: Prefs
    private lateinit var repo: AppRepository
    private lateinit var assistant: AssistantController
    private lateinit var hue: HueController
    private lateinit var wallpapers: Wallpapers
    private var apps by mutableStateOf<List<TvApp>>(emptyList())
    private var darkMode by mutableStateOf(true)
    private var wallpaper by mutableStateOf(Wallpaper("minimal", "Minimal", null))
    private var wallpaperImage by mutableStateOf<ImageBitmap?>(null)
    private var wallpaperList by mutableStateOf<List<Wallpaper>>(emptyList())
    private var wallpaperThumbs by mutableStateOf<Map<String, ImageBitmap>>(emptyMap())
    private var settingsOpen by mutableStateOf(false)
    private val firstTileFocus = FocusRequester()

    private val micPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            assistant.toggle()
        } else {
            assistant.fail("Sin permiso de micrófono. Habilitalo en Ajustes de Android TV → Apps → MiBoxOs → Permisos.")
        }
    }

    // Para leer fondos propios de Pictures/MiBoxOs (Android 9 pide este permiso)
    private val storagePermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        refreshWallpapers()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        repo = AppRepository(this, prefs)
        hue = HueController(prefs, lifecycleScope)
        assistant = AssistantController(this, prefs, repo, hue, lifecycleScope) { apps }
        wallpapers = Wallpapers(this)
        darkMode = prefs.darkMode
        applyConfigExtras(intent)
        selectWallpaper(prefs.wallpaper)
        if (hue.isPaired) hue.refreshSummary()

        // Es el launcher: "Atrás" nunca sale, solo cierra el asistente
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    assistant.state != AssistantState.Idle -> assistant.dismiss()
                    settingsOpen -> closeSettings()
                }
            }
        })

        setContent {
            CompositionLocalProvider(LocalColors provides appColors(darkMode, photoWallpaper = wallpaper.source != null)) {
                Box(Modifier.fillMaxSize()) {
                    HomeScreen(
                        apps = apps,
                        wallpaper = wallpaperImage,
                        interactive = !settingsOpen,
                        onLaunch = { repo.launch(it.packageName) },
                        onAssistant = ::onAssistantKey,
                        onSettings = ::openSettings,
                        firstTileFocus = firstTileFocus,
                    )
                    SettingsPanel(
                        visible = settingsOpen,
                        state = SettingsState(
                            darkMode = darkMode,
                            wallpapers = wallpaperList,
                            thumbs = wallpaperThumbs,
                            selectedWallpaper = wallpaper.id,
                            userFolder = "Pictures/MiBoxOs",
                            hue = hue.status,
                            assistantReady = prefs.geminiApiKey.isNotBlank(),
                            model = prefs.geminiModel,
                        ),
                        actions = SettingsActions(
                            onDarkMode = { darkMode = it; prefs.darkMode = it },
                            onWallpaper = ::selectWallpaper,
                            onHuePair = hue::pair,
                            onSystemSettings = { startActivity(Intent(Settings.ACTION_SETTINGS)) },
                        ),
                    )
                    AssistantOverlay(assistant.state, assistant.level)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Recarga en cada vuelta al inicio: refleja apps instaladas/desinstaladas y el orden por uso
        lifecycleScope.launch { runCatching { repo.loadApps() }.onSuccess { apps = it } }
    }

    // Público para poder simular el botón Home en tests
    public override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        applyConfigExtras(intent)
        // Botón Home estando ya en el inicio: volver a la primera app
        if (intent.hasCategory(Intent.CATEGORY_HOME)) {
            assistant.dismiss()
            settingsOpen = false
            runCatching { firstTileFocus.requestFocus() }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode in ASSISTANT_KEYS) {
            if (event.repeatCount == 0) onAssistantKey()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun openSettings() {
        refreshWallpapers()
        settingsOpen = true
        // Se pide una sola vez: si lo rechazan, los fondos incluidos siguen andando
        if (!prefs.askedStoragePermission &&
            checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        ) {
            prefs.askedStoragePermission = true
            storagePermission.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    private fun refreshWallpapers() {
        val list = wallpapers.all()
        wallpaperList = list
        lifecycleScope.launch {
            for (wp in list) {
                if (wp.id in wallpaperThumbs) continue
                wallpapers.loadThumb(wp)?.let { wallpaperThumbs = wallpaperThumbs + (wp.id to it) }
            }
        }
    }

    private fun closeSettings() {
        settingsOpen = false
    }

    private fun selectWallpaper(id: String) {
        val selected = wallpapers.byId(id)
        wallpaper = selected
        prefs.wallpaper = selected.id
        lifecycleScope.launch { wallpaperImage = wallpapers.loadFull(selected) }
    }

    private fun onAssistantKey() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            assistant.toggle()
        } else {
            micPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    /** Configuración por ADB: am start -n com.jsalinas.miboxos/.MainActivity --es gemini_api_key ... */
    private fun applyConfigExtras(intent: Intent?) {
        intent?.getStringExtra(Prefs.KEY_API)?.let { prefs.geminiApiKey = it }
        intent?.getStringExtra(Prefs.KEY_MODEL)?.let { prefs.geminiModel = it }
    }

    private companion object {
        // Teclas que pueden llegar a la app según el control; el botón de Google Assistant
        // suele interceptarlo el sistema, por eso también está el botón "Asistente" en pantalla.
        val ASSISTANT_KEYS = setOf(
            KeyEvent.KEYCODE_SEARCH,
            KeyEvent.KEYCODE_VOICE_ASSIST,
            KeyEvent.KEYCODE_ASSIST,
            KeyEvent.KEYCODE_MENU,
        )
    }
}
