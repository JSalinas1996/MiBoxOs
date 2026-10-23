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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.lifecycleScope
import com.jsalinas.miboxos.apps.AppRepository
import com.jsalinas.miboxos.apps.SystemTiles
import com.jsalinas.miboxos.apps.TvApp
import com.jsalinas.miboxos.assistant.AssistantController
import com.jsalinas.miboxos.assistant.AssistantState
import com.jsalinas.miboxos.hue.HueController
import com.jsalinas.miboxos.screensaver.Screensaver
import com.jsalinas.miboxos.screensaver.screensaverImages
import com.jsalinas.miboxos.ui.AssistantOverlay
import com.jsalinas.miboxos.ui.HomeScreen
import com.jsalinas.miboxos.ui.LocalColors
import com.jsalinas.miboxos.ui.SettingsActions
import com.jsalinas.miboxos.ui.SettingsPage
import com.jsalinas.miboxos.ui.SettingsScreen
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
    private var wallpaperFrosted by mutableStateOf<ImageBitmap?>(null)
    private var settingsOpen by mutableStateOf(false)
    private var settingsPage by mutableStateOf(SettingsPage.ROOT)
    private var screensaverPreview by mutableStateOf(false)
    private var screensaverSeconds by mutableStateOf(20)
    private var screensaverOnlyMine by mutableStateOf(false)
    private val systemTiles by lazy { SystemTiles.assistant() to SystemTiles.settings() }
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
        screensaverSeconds = prefs.screensaverSeconds
        screensaverOnlyMine = prefs.screensaverOnlyMine
        applyConfigExtras(intent)
        selectWallpaper(prefs.wallpaper)
        if (hue.isPaired) hue.refreshSummary()

        // Es el launcher: "Atrás" nunca sale, solo cierra el asistente
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    assistant.state != AssistantState.Idle -> assistant.dismiss()
                    settingsOpen && settingsPage != SettingsPage.ROOT -> settingsPage = SettingsPage.ROOT
                    settingsOpen -> closeSettings()
                }
            }
        })

        setContent {
            CompositionLocalProvider(LocalColors provides appColors(darkMode, photoWallpaper = wallpaper.source != null)) {
                Box(
                    Modifier.fillMaxSize().onPreviewKeyEvent { event ->
                        // Durante la vista previa del modo reposo, cualquier botón lo cierra (y no hace nada más)
                        if (!screensaverPreview) return@onPreviewKeyEvent false
                        if (event.type == KeyEventType.KeyUp) screensaverPreview = false
                        true
                    },
                ) {
                    HomeScreen(
                        apps = homeApps(),
                        wallpaper = wallpaperImage,
                        frosted = wallpaperFrosted,
                        interactive = !settingsOpen,
                        onLaunch = ::launch,
                        firstTileFocus = firstTileFocus,
                    )
                    SettingsScreen(
                        visible = settingsOpen,
                        page = settingsPage,
                        state = SettingsState(
                            darkMode = darkMode,
                            wallpapers = wallpaperList,
                            thumbs = wallpaperThumbs,
                            selectedWallpaper = wallpaper.id,
                            userFolder = "Pictures/MiBoxOs",
                            hue = hue.status,
                            assistantReady = prefs.geminiApiKey.isNotBlank(),
                            model = prefs.geminiModel,
                            version = runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull().orEmpty(),
                            screensaverSeconds = screensaverSeconds,
                            screensaverOnlyMine = screensaverOnlyMine,
                            hasOwnPhotos = wallpaperList.any { it.source is Wallpaper.UserFile },
                        ),
                        actions = SettingsActions(
                            onNavigate = { settingsPage = it },
                            onDarkMode = { darkMode = it; prefs.darkMode = it },
                            onWallpaper = ::selectWallpaper,
                            onHuePair = hue::pair,
                            onSystemSettings = { startActivity(Intent(Settings.ACTION_SETTINGS)) },
                            onScreensaverSeconds = { screensaverSeconds = it; prefs.screensaverSeconds = it },
                            onScreensaverOnlyMine = { screensaverOnlyMine = it; prefs.screensaverOnlyMine = it },
                            onScreensaverPreview = { screensaverPreview = true },
                            onScreensaverSystem = ::openDreamSettings,
                        ),
                    )
                    AssistantOverlay(assistant.state, assistant.level)
                    if (screensaverPreview) {
                        val images = remember { screensaverImages(wallpapers, prefs) }
                        Screensaver(images, wallpapers::loadFull, screensaverSeconds)
                    }
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
            screensaverPreview = false
            closeSettings()
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

    /**
     * Como en tvOS, Asistente y Ajustes son íconos en la primera fila (después de las 3 apps más usadas),
     * así se llega a todo con las flechas y OK, sin depender de teclas especiales del control.
     */
    private fun homeApps(): List<TvApp> {
        val (assistantTile, settingsTile) = systemTiles
        return apps.take(3) + assistantTile + settingsTile + apps.drop(3)
    }

    private fun launch(app: TvApp) {
        when (app.packageName) {
            SystemTiles.ASSISTANT -> onAssistantKey()
            SystemTiles.SETTINGS -> openSettings()
            else -> repo.launch(app.packageName)
        }
    }

    private fun openSettings() {
        refreshWallpapers()
        settingsPage = SettingsPage.ROOT
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

    /** Pantalla de Protector de pantalla de Android; si este Android TV no la expone, Ajustes generales. */
    private fun openDreamSettings() {
        val dream = Intent(Settings.ACTION_DREAM_SETTINGS)
        startActivity(if (dream.resolveActivity(packageManager) != null) dream else Intent(Settings.ACTION_SETTINGS))
    }

    private fun selectWallpaper(id: String) {
        val selected = wallpapers.byId(id)
        wallpaper = selected
        prefs.wallpaper = selected.id
        lifecycleScope.launch {
            wallpaperImage = wallpapers.loadFull(selected)
            wallpaperFrosted = wallpapers.loadFrosted(selected)
        }
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
        // Atajos si el control los manda; el botón de Google Assistant suele interceptarlo el sistema,
        // por eso el asistente también es un ícono en el inicio.
        val ASSISTANT_KEYS = setOf(
            KeyEvent.KEYCODE_SEARCH,
            KeyEvent.KEYCODE_VOICE_ASSIST,
            KeyEvent.KEYCODE_ASSIST,
            KeyEvent.KEYCODE_MENU,
        )
    }
}
