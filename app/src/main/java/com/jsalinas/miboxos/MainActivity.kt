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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.lifecycle.lifecycleScope
import com.jsalinas.miboxos.apps.AppRepository
import com.jsalinas.miboxos.apps.TvApp
import com.jsalinas.miboxos.assistant.AssistantController
import com.jsalinas.miboxos.assistant.AssistantState
import com.jsalinas.miboxos.ui.AssistantOverlay
import com.jsalinas.miboxos.ui.HomeScreen
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var prefs: Prefs
    private lateinit var repo: AppRepository
    private lateinit var assistant: AssistantController
    private var apps by mutableStateOf<List<TvApp>>(emptyList())
    private val firstTileFocus = FocusRequester()

    private val micPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) assistant.toggle()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        repo = AppRepository(this, prefs)
        assistant = AssistantController(this, prefs, repo, lifecycleScope) { apps }
        applyConfigExtras(intent)

        // Es el launcher: "Atrás" nunca sale, solo cierra el asistente
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (assistant.state != AssistantState.Idle) assistant.dismiss()
            }
        })

        setContent {
            Box(Modifier.fillMaxSize()) {
                HomeScreen(
                    apps = apps,
                    onLaunch = { repo.launch(it.packageName) },
                    onAssistant = ::onAssistantKey,
                    onSettings = { startActivity(Intent(Settings.ACTION_SETTINGS)) },
                    firstTileFocus = firstTileFocus,
                )
                AssistantOverlay(assistant.state, assistant.level)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Recarga en cada vuelta al inicio: refleja apps instaladas/desinstaladas y el orden por uso
        lifecycleScope.launch { apps = repo.loadApps() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        applyConfigExtras(intent)
        // Botón Home estando ya en el inicio: volver a la primera app
        if (intent.hasCategory(Intent.CATEGORY_HOME)) {
            assistant.dismiss()
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
