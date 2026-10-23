@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.jsalinas.miboxos.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import com.jsalinas.miboxos.hue.HueStatus
import com.jsalinas.miboxos.wallpaper.Wallpaper

class SettingsState(
    val darkMode: Boolean,
    val wallpapers: List<Wallpaper>,
    /** Miniaturas ya cargadas (id -> imagen); las que faltan se muestran vacías. */
    val thumbs: Map<String, ImageBitmap>,
    val selectedWallpaper: String,
    val userFolder: String,
    val hue: HueStatus,
    val assistantReady: Boolean,
    val model: String,
)

class SettingsActions(
    val onDarkMode: (Boolean) -> Unit,
    val onWallpaper: (String) -> Unit,
    val onHuePair: () -> Unit,
    val onSystemSettings: () -> Unit,
)

/** Panel lateral derecho, como los menús de tvOS. "Atrás" lo cierra (lo maneja MainActivity). */
@Composable
fun SettingsPanel(visible: Boolean, state: SettingsState, actions: SettingsActions) {
    val firstFocus = remember { FocusRequester() }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterEnd) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(200)) + slideInHorizontally(tween(300)) { it / 3 },
            exit = fadeOut(tween(200)) + slideOutHorizontally(tween(300)) { it / 3 },
        ) {
            Column(
                Modifier
                    .fillMaxHeight()
                    .width(460.dp)
                    .padding(16.dp)
                    .background(LocalColors.current.panel, RoundedCornerShape(28.dp))
                    .verticalScroll(rememberScrollState())
                    .padding(28.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                BasicText("Ajustes", style = Type.title.copy(fontSize = Type.hero.fontSize * 0.7f))

                Section("APARIENCIA")
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Chip("Oscuro", selected = state.darkMode, modifier = Modifier.focusRequester(firstFocus)) { actions.onDarkMode(true) }
                    Chip("Claro", selected = !state.darkMode) { actions.onDarkMode(false) }
                }

                Section("FONDO")
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = PaddingValues(vertical = 8.dp, horizontal = 4.dp),
                ) {
                    items(state.wallpapers, key = { it.id }) { wp ->
                        WallpaperThumb(wp, state.thumbs[wp.id], selected = wp.id == state.selectedWallpaper) {
                            actions.onWallpaper(wp.id)
                        }
                    }
                }
                BasicText("Tus fotos: copialas a ${state.userFolder}", style = Type.caption)

                Section("PHILIPS HUE")
                val (hueText, hueButton) = when (val h = state.hue) {
                    HueStatus.NotPaired -> "Sin vincular" to "Vincular"
                    HueStatus.Searching -> "Buscando el bridge…" to null
                    is HueStatus.PressButton -> "Apretá el botón redondo del bridge (${h.secondsLeft} s)" to null
                    is HueStatus.Paired -> h.summary to "Volver a vincular"
                    is HueStatus.Failed -> h.message to "Reintentar"
                }
                BasicText(hueText, style = Type.body)
                hueButton?.let { Chip(it, selected = false, onClick = actions.onHuePair) }

                Section("ASISTENTE")
                BasicText(
                    if (state.assistantReady) "API key de Gemini cargada" else "Falta la API key de Gemini (ver README)",
                    style = Type.body,
                )
                BasicText("Modelo: ${state.model}", style = Type.caption)

                Section("SISTEMA")
                Chip("Ajustes de Android TV", selected = false, onClick = actions.onSystemSettings)
            }
        }
    }
    LaunchedEffect(visible) {
        if (visible) runCatching { firstFocus.requestFocus() }
    }
}

@Composable
private fun Section(title: String) {
    BasicText(title, style = Type.section, modifier = Modifier.padding(top = 14.dp))
}

@Composable
private fun Chip(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val colors = LocalColors.current
    var hasFocus by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        modifier = modifier.onFocusChanged { hasFocus = it.isFocused },
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) colors.accent else colors.control,
            focusedContainerColor = colors.controlFocused,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
        glow = ClickableSurfaceDefaults.glow(),
    ) {
        val textColor = when {
            hasFocus -> colors.onControlFocused
            selected -> Color.White
            else -> colors.text
        }
        BasicText(label, style = Type.body.copy(color = textColor), modifier = Modifier.padding(horizontal = 18.dp, vertical = 9.dp))
    }
}

@Composable
private fun WallpaperThumb(wallpaper: Wallpaper, thumb: ImageBitmap?, selected: Boolean, onClick: () -> Unit) {
    val colors = LocalColors.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            onClick = onClick,
            modifier = Modifier.size(width = 168.dp, height = 94.dp),
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
            colors = ClickableSurfaceDefaults.colors(containerColor = colors.tile, focusedContainerColor = colors.tile),
            border = ClickableSurfaceDefaults.border(
                border = if (selected) Border(BorderStroke(3.dp, colors.accent)) else Border.None,
                focusedBorder = Border(BorderStroke(3.dp, if (selected) colors.accent else colors.text)),
            ),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.08f),
        ) {
            if (thumb != null) {
                Image(thumb, wallpaper.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, filterQuality = FilterQuality.High)
            } else if (wallpaper.source == null) {
                // Muestra del fondo minimalista en ambos modos, mitad y mitad
                Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0.5f to Color(0xFF1C1C1E), 0.5f to Color(0xFFE5E5EA))))
            }
        }
        BasicText(
            wallpaper.name,
            style = Type.caption,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp).width(168.dp),
        )
    }
}
