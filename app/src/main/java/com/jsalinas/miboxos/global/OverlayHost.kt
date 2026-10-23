package com.jsalinas.miboxos.global

import android.content.Context
import android.graphics.PixelFormat
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

/**
 * Capa de pantalla completa por encima de cualquier app (ventana de accesibilidad: no necesita el
 * permiso "mostrar sobre otras apps" y no pausa la app de abajo). Toma el foco solo cuando hace falta
 * navegar con el control (Centro de control, modo reposo); con el asistente sigue pasando todo de largo.
 */
class OverlayHost(private val context: Context, content: @Composable () -> Unit) : LifecycleOwner, SavedStateRegistryOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedState = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedState.savedStateRegistry

    private val windowManager = context.getSystemService(WindowManager::class.java)
    private val params = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        PASSIVE_FLAGS,
        PixelFormat.TRANSLUCENT,
    )
    private val view: ComposeView
    private var focusable = false

    init {
        savedState.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        view = ComposeView(context).apply {
            setViewTreeLifecycleOwner(this@OverlayHost)
            setViewTreeSavedStateRegistryOwner(this@OverlayHost)
            setContent(content)
        }
        windowManager.addView(view, params)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    /** Con foco, las flechas y OK van a la capa; sin foco, la capa es solo visual. */
    fun setFocusable(value: Boolean) {
        if (value == focusable) return
        focusable = value
        params.flags = if (value) 0 else PASSIVE_FLAGS
        runCatching { windowManager.updateViewLayout(view, params) }
        if (value) view.requestFocus()
    }

    fun destroy() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        runCatching { windowManager.removeView(view) }
    }

    private companion object {
        const val PASSIVE_FLAGS = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
    }
}
