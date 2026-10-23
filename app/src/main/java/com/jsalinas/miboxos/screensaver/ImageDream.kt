package com.jsalinas.miboxos.screensaver

import android.service.dreams.DreamService
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.jsalinas.miboxos.Prefs
import com.jsalinas.miboxos.wallpaper.Wallpapers

/**
 * Protector de pantalla del sistema: Android TV lo arranca solo tras el tiempo de inactividad,
 * también dentro de otras apps. Se elige en Ajustes de Android TV → Protector de pantalla.
 * Cualquier botón del control lo cierra.
 */
class ImageDream : DreamService(), LifecycleOwner, SavedStateRegistryOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedState = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedState.savedStateRegistry

    override fun onCreate() {
        super.onCreate()
        savedState.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = false
        isFullscreen = true
        val prefs = Prefs(this)
        val wallpapers = Wallpapers(this)
        val images = screensaverImages(wallpapers, prefs)
        val view = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@ImageDream)
            setViewTreeSavedStateRegistryOwner(this@ImageDream)
            setContent { Screensaver(images, wallpapers::loadFull, prefs.screensaverSeconds) }
        }
        setContentView(view)
    }

    override fun onDreamingStarted() {
        super.onDreamingStarted()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    override fun onDreamingStopped() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        super.onDreamingStopped()
    }

    override fun onDestroy() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        super.onDestroy()
    }
}
