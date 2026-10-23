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
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.lifecycle.lifecycleScope
import com.jsalinas.miboxos.apps.AppOrder
import com.jsalinas.miboxos.apps.SystemTiles
import com.jsalinas.miboxos.apps.TvApp
import com.jsalinas.miboxos.assistant.AssistantState
import com.jsalinas.miboxos.global.AssistantKeys
import com.jsalinas.miboxos.ui.AppOptions
import com.jsalinas.miboxos.ui.GlobalLayers
import com.jsalinas.miboxos.ui.HomeScreen
import com.jsalinas.miboxos.ui.LocalColors
import com.jsalinas.miboxos.ui.SettingsActions
import com.jsalinas.miboxos.ui.SettingsPage
import com.jsalinas.miboxos.ui.SettingsScreen
import com.jsalinas.miboxos.ui.SettingsState
import com.jsalinas.miboxos.ui.appColors
import com.jsalinas.miboxos.wallpaper.Wallpaper
import com.jsalinas.miboxos.watchnext.WatchNextItem
import com.jsalinas.miboxos.watchnext.WatchNextRepository
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val app get() = application as MiBoxApp
    private val prefs get() = app.prefs
    private val assistant get() = app.assistant
    private val controlCenter get() = app.controlCenter
    private val watchNext by lazy { WatchNextRepository(this) }

    private var apps by mutableStateOf<List<TvApp>>(emptyList())
    private var continueWatching by mutableStateOf<List<WatchNextItem>>(emptyList())
    private var watchNextAllowed by mutableStateOf(false)
    private var wallpaper by mutableStateOf(Wallpaper("minimal", "Minimal", null))
    private var wallpaperImage by mutableStateOf<ImageBitmap?>(null)
    private var wallpaperList by mutableStateOf<List<Wallpaper>>(emptyList())
    private var wallpaperThumbs by mutableStateOf<Map<String, ImageBitmap>>(emptyMap())
    private var wallpaperFrosted by mutableStateOf<ImageBitmap?>(null)
    private var settingsOpen by mutableStateOf(false)
    private var settingsPage by mutableStateOf(SettingsPage.ROOT)
    private var screensaverSeconds by mutableStateOf(20)
    private var screensaverOnlyMine by mutableStateOf(false)
    private var accessibilityOn by mutableStateOf(false)
    private var appOrder by mutableStateOf<List<String>>(emptyList())
    private var hiddenApps by mutableStateOf<Set<String>>(emptySet())
    private var voiceReplies by mutableStateOf(true)
    /** App con el menú Mover/Ocultar abierto. */
    private var optionsFor by mutableStateOf<TvApp?>(null)
    /** App que se está moviendo con las flechas. */
    private var editing by mutableStateOf<String?>(null)
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

    private val tvListingsPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        refreshWatchNext()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        screensaverSeconds = prefs.screensaverSeconds
        screensaverOnlyMine = prefs.screensaverOnlyMine
        appOrder = prefs.appOrder
        hiddenApps = prefs.hiddenApps
        voiceReplies = prefs.voiceReplies
        app.updater.check()
        applyConfigExtras(intent)
        selectWallpaper(prefs.wallpaper)
        if (app.hue.isPaired) app.hue.refreshSummary()
        // "Seguir viendo" necesita un permiso: se pide una sola vez al primer arranque
        if (!prefs.askedTvListings && !watchNext.hasPermission()) {
            prefs.askedTvListings = true
            tvListingsPermission.launch(WatchNextRepository.PERMISSION)
        }

        // Es el launcher: "Atrás" nunca sale; cierra lo que esté abierto, de arriba hacia abajo
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    assistant.state != AssistantState.Idle -> assistant.dismiss()
                    optionsFor != null -> optionsFor = null
                    editing != null -> editing = null
                    controlCenter.visible -> controlCenter.close()
                    settingsOpen && settingsPage != SettingsPage.ROOT -> settingsPage = SettingsPage.ROOT
                    settingsOpen -> closeSettings()
                }
            }
        })

        setContent {
            CompositionLocalProvider(LocalColors provides appColors(app.darkMode, photoWallpaper = wallpaper.source != null)) {
                Box(
                    Modifier.fillMaxSize().onPreviewKeyEvent { event ->
                        // Aprender el botón del asistente (si el servicio global no está activo, llega acá)
                        if (AssistantKeys.learn(prefs, event.nativeKeyEvent)) return@onPreviewKeyEvent true
                        if (editing != null && onEditKey(event.nativeKeyEvent)) return@onPreviewKeyEvent true
                        // Modo reposo: cualquier botón lo cierra (y no hace nada más)
                        if (!controlCenter.screensaverVisible) return@onPreviewKeyEvent false
                        if (event.type == KeyEventType.KeyUp) controlCenter.screensaverVisible = false
                        true
                    },
                ) {
                    HomeScreen(
                        apps = homeApps(),
                        wallpaper = wallpaperImage,
                        frosted = wallpaperFrosted,
                        interactive = !settingsOpen && !controlCenter.visible && optionsFor == null,
                        onLaunch = ::launch,
                        firstTileFocus = firstTileFocus,
                        continueWatching = continueWatching,
                        onWatchNext = ::openWatchNext,
                        editing = editing,
                        onLongPress = { if (editing == null) optionsFor = it },
                    )
                    AppOptions(
                        app = optionsFor,
                        onMove = {
                            editing = optionsFor?.packageName
                            optionsFor = null
                        },
                        onHide = {
                            optionsFor?.let { hiddenApps = hiddenApps + it.packageName; prefs.hiddenApps = hiddenApps }
                            optionsFor = null
                        },
                    )
                    SettingsScreen(
                        visible = settingsOpen,
                        page = settingsPage,
                        state = SettingsState(
                            darkMode = app.darkMode,
                            wallpapers = wallpaperList,
                            thumbs = wallpaperThumbs,
                            selectedWallpaper = wallpaper.id,
                            userFolder = "Pictures/MiBoxOs",
                            hue = app.hue.status,
                            assistantReady = prefs.geminiApiKey.isNotBlank(),
                            model = prefs.geminiModel,
                            version = runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull().orEmpty(),
                            screensaverSeconds = screensaverSeconds,
                            screensaverOnlyMine = screensaverOnlyMine,
                            hasOwnPhotos = wallpaperList.any { it.source is Wallpaper.UserFile },
                            globalAssistantOn = GlobalAssistant.connected || accessibilityOn,
                            assistantKeyLabel = if (AssistantKeys.learning) "Presioná el botón…" else AssistantKeys.label(prefs),
                            watchNextAllowed = watchNextAllowed,
                            hiddenApps = (apps + systemTiles.toList()).filter { it.packageName in hiddenApps }.map { it.packageName to it.label },
                            customOrder = appOrder.isNotEmpty(),
                            voiceReplies = voiceReplies,
                            nowPlayingAccess = app.nowPlaying.hasAccess(),
                            update = app.updater.status,
                        ),
                        actions = SettingsActions(
                            onNavigate = { settingsPage = it },
                            onDarkMode = { app.darkMode = it; prefs.darkMode = it },
                            onWallpaper = ::selectWallpaper,
                            onHuePair = app.hue::pair,
                            onSystemSettings = { startActivity(Intent(Settings.ACTION_SETTINGS)) },
                            onScreensaverSeconds = { screensaverSeconds = it; prefs.screensaverSeconds = it },
                            onScreensaverOnlyMine = { screensaverOnlyMine = it; prefs.screensaverOnlyMine = it },
                            onScreensaverPreview = { controlCenter.screensaverVisible = true },
                            onScreensaverSystem = ::openDreamSettings,
                            onGlobalAssistant = { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
                            onLearnKey = { AssistantKeys.learning = true },
                            onWatchNextPermission = { tvListingsPermission.launch(WatchNextRepository.PERMISSION) },
                            onUnhide = { pkg -> hiddenApps = hiddenApps - pkg; prefs.hiddenApps = hiddenApps },
                            onResetOrder = { appOrder = emptyList(); prefs.appOrder = emptyList() },
                            onVoiceReplies = { voiceReplies = it; prefs.voiceReplies = it },
                            onNowPlayingAccess = ::openNotificationAccess,
                            onCheckUpdate = { app.updater.check(force = true) },
                            onInstallUpdate = app.updater::install,
                        ),
                    )
                    // Con el asistente global activo, estas capas las dibuja el servicio (encima de cualquier app)
                    if (!GlobalAssistant.connected) {
                        GlobalLayers(app, onAssistant = ::onAssistantKey, onSettings = ::openSettings)
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        accessibilityOn = isGlobalAssistantEnabled()
        // Recarga en cada vuelta al inicio: refleja apps instaladas/desinstaladas, el orden por uso y "Seguir viendo"
        lifecycleScope.launch {
            runCatching { app.repo.loadApps() }.onSuccess {
                apps = it
                app.apps = it
            }
        }
        refreshWatchNext()
    }

    // Público para poder simular el botón Home en tests
    public override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        applyConfigExtras(intent)
        // Botón Home estando ya en el inicio: cerrar todo y volver a la primera app
        if (intent.hasCategory(Intent.CATEGORY_HOME)) {
            assistant.dismiss()
            controlCenter.screensaverVisible = false
            controlCenter.close()
            closeSettings()
            runCatching { firstTileFocus.requestFocus() }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (AssistantKeys.isAssistantKey(prefs, keyCode)) {
            if (event.repeatCount == 0) onAssistantKey()
            return true
        }
        // Menú abre/cierra el Centro de control (con el servicio global también: manteniendo el botón de voz)
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            if (event.repeatCount == 0) {
                if (controlCenter.visible) controlCenter.close() else controlCenter.open()
            }
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
        val default = apps.take(3) + assistantTile + settingsTile + apps.drop(3)
        return AppOrder.arrange(default, appOrder, hiddenApps)
    }

    /** Modo edición: las flechas mueven el ícono (una fila = 5 lugares); OK o Atrás terminan. */
    private fun onEditKey(event: KeyEvent): Boolean {
        val pkg = editing ?: return false
        val delta = when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> -1
            KeyEvent.KEYCODE_DPAD_RIGHT -> 1
            KeyEvent.KEYCODE_DPAD_UP -> -5
            KeyEvent.KEYCODE_DPAD_DOWN -> 5
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_BACK -> {
                if (event.action == KeyEvent.ACTION_UP) editing = null
                return true
            }
            else -> return true
        }
        if (event.action == KeyEvent.ACTION_DOWN) {
            appOrder = AppOrder.move(homeApps(), pkg, delta)
            prefs.appOrder = appOrder
        }
        return true
    }

    /** Acceso a notificaciones (para "Ahora suena"); si este Android TV no expone la pantalla, Ajustes. */
    private fun openNotificationAccess() {
        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        startActivity(if (intent.resolveActivity(packageManager) != null) intent else Intent(Settings.ACTION_SETTINGS))
    }

    private fun launch(app: TvApp) {
        when (app.packageName) {
            SystemTiles.ASSISTANT -> onAssistantKey()
            SystemTiles.SETTINGS -> openSettings()
            else -> this.app.repo.launch(app.packageName)
        }
    }

    /** Abre el contenido exacto (capítulo, minuto) con el intent que publicó la app; si no, la app. */
    private fun openWatchNext(item: WatchNextItem) {
        val opened = item.intent?.let {
            runCatching { startActivity(Intent(it).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
        } ?: false
        if (!opened) app.repo.launch(item.packageName)
    }

    private fun refreshWatchNext() {
        watchNextAllowed = watchNext.hasPermission()
        lifecycleScope.launch { continueWatching = watchNext.load() }
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
        val list = app.wallpapers.all()
        wallpaperList = list
        lifecycleScope.launch {
            for (wp in list) {
                if (wp.id in wallpaperThumbs) continue
                app.wallpapers.loadThumb(wp)?.let { wallpaperThumbs = wallpaperThumbs + (wp.id to it) }
            }
        }
    }

    private fun closeSettings() {
        settingsOpen = false
        AssistantKeys.learning = false
    }

    /** Pantalla de Protector de pantalla de Android; si este Android TV no la expone, Ajustes generales. */
    private fun openDreamSettings() {
        val dream = Intent(Settings.ACTION_DREAM_SETTINGS)
        startActivity(if (dream.resolveActivity(packageManager) != null) dream else Intent(Settings.ACTION_SETTINGS))
    }

    private fun selectWallpaper(id: String) {
        val selected = app.wallpapers.byId(id)
        wallpaper = selected
        prefs.wallpaper = selected.id
        lifecycleScope.launch {
            wallpaperImage = app.wallpapers.loadFull(selected)
            wallpaperFrosted = app.wallpapers.loadFrosted(selected)
        }
    }

    private fun onAssistantKey() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            assistant.toggle()
        } else {
            micPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun isGlobalAssistantEnabled(): Boolean {
        val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
        return enabled.contains("$packageName/") && enabled.contains("GlobalAssistantService")
    }

    /** Configuración por ADB: am start -n com.jsalinas.miboxos/.MainActivity --es gemini_api_key ... */
    private fun applyConfigExtras(intent: Intent?) {
        intent?.getStringExtra(Prefs.KEY_API)?.let { prefs.geminiApiKey = it }
        intent?.getStringExtra(Prefs.KEY_MODEL)?.let { prefs.geminiModel = it }
        if (intent?.getBooleanExtra(EXTRA_OPEN_SETTINGS, false) == true) {
            intent.removeExtra(EXTRA_OPEN_SETTINGS)
            openSettings()
        }
    }

    companion object {
        /** Lo usa el Centro de control (desde cualquier app) para abrir Ajustes del launcher. */
        const val EXTRA_OPEN_SETTINGS = "open_settings"
    }
}
