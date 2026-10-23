@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.jsalinas.miboxos.ui

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Glow
import androidx.tv.material3.Surface
import com.jsalinas.miboxos.apps.SystemTiles
import com.jsalinas.miboxos.nowplaying.NowPlaying
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class RoomState(val name: String, val on: Boolean)

class ControlCenterState(
    val darkMode: Boolean,
    val huePaired: Boolean,
    /** null mientras carga. */
    val rooms: List<RoomState>?,
    val scenes: List<String>,
    val nowPlaying: NowPlaying? = null,
    /** Minutos del temporizador de apagado, o null. */
    val sleepTimer: Int? = null,
)

class ControlCenterActions(
    val onToggleRoom: (String) -> Unit = {},
    val onScene: (String) -> Unit = {},
    val onAssistant: () -> Unit = {},
    val onScreensaver: () -> Unit = {},
    val onDarkMode: (Boolean) -> Unit = {},
    val onSettings: () -> Unit = {},
    val onPlayPause: () -> Unit = {},
    val onNext: () -> Unit = {},
    val onPrevious: () -> Unit = {},
    val onSleepNow: () -> Unit = {},
    val onCycleTimer: () -> Unit = {},
)

/**
 * Centro de control como en tvOS: panel a la derecha con la hora, accesos rápidos y las luces de casa.
 * Se abre manteniendo el botón de voz (desde cualquier app) o con la tecla Menú; Atrás lo cierra.
 */
@Composable
fun ControlCenter(visible: Boolean, state: ControlCenterState, actions: ControlCenterActions) {
    val colors = LocalColors.current
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterEnd) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(200)) + slideInHorizontally(tween(320)) { it / 4 },
            exit = fadeOut(tween(180)) + slideOutHorizontally(tween(260)) { it / 4 },
        ) {
            val first = remember { FocusRequester() }
            var hasFocus by remember { mutableStateOf(false) }
            Column(
                Modifier
                    .testTag("controlCenter")
                    .fillMaxHeight()
                    .width(400.dp)
                    .padding(20.dp)
                    .background(colors.panel, RoundedCornerShape(36.dp))
                    .onFocusChanged { hasFocus = it.hasFocus }
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 26.dp, vertical = 28.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                ClockHeader()
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
                    QuickTile("Reposo", Modifier.focusRequester(first), onClick = actions.onScreensaver) { ScreensaverIcon(it) }
                    QuickTile("Asistente", onClick = actions.onAssistant) { Orb(active = false, level = 0f, size = 30.dp) }
                    QuickTile(if (state.darkMode) "Oscuro" else "Claro", onClick = { actions.onDarkMode(!state.darkMode) }) {
                        MoonIcon(it, filled = state.darkMode)
                    }
                    QuickTile("Ajustes", onClick = actions.onSettings) { GearIcon() }
                }

                state.nowPlaying?.let { NowPlayingCard(it, actions) }

                BasicText("Apagar", style = Type.title.copy(fontSize = 18.sp), modifier = Modifier.padding(top = 12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SceneChip("Apagar ahora", Modifier.weight(1f).testTag("cc:apagar"), actions.onSleepNow)
                    SceneChip(
                        state.sleepTimer?.let { "En $it min" } ?: "Temporizador",
                        Modifier.weight(1f).testTag("cc:timer"),
                        actions.onCycleTimer,
                    )
                }

                BasicText("Inicio", style = Type.title.copy(fontSize = 18.sp), modifier = Modifier.padding(top = 12.dp))
                when {
                    !state.huePaired -> BasicText("Vinculá Philips Hue en Ajustes para controlar las luces desde acá.", style = Type.caption)
                    state.rooms == null -> BasicText("Cargando luces…", style = Type.caption)
                    state.rooms.isEmpty() -> BasicText("No pude leer las luces del bridge.", style = Type.caption)
                    else -> state.rooms.chunked(2).forEach { pair ->
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            pair.forEach { room -> RoomTile(room, Modifier.weight(1f)) { actions.onToggleRoom(room.name) } }
                            if (pair.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
                if (state.huePaired && state.scenes.isNotEmpty()) {
                    BasicText("Escenas", style = Type.caption, modifier = Modifier.padding(top = 4.dp))
                    state.scenes.chunked(2).forEach { pair ->
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            pair.forEach { scene -> SceneChip(scene, Modifier.weight(1f)) { actions.onScene(scene) } }
                            if (pair.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
            LaunchedEffect(Unit) {
                withFrameNanos { }
                repeat(20) {
                    runCatching { first.requestFocus() }
                    withFrameNanos { }
                    if (hasFocus) return@LaunchedEffect
                }
            }
        }
    }
}

@Composable
private fun ClockHeader() {
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = Date()
            delay(60_000 - System.currentTimeMillis() % 60_000)
        }
    }
    val locale = Locale.forLanguageTag("es-AR")
    Column {
        BasicText(SimpleDateFormat("HH:mm", locale).format(now), style = Type.largeTitle.copy(fontSize = 46.sp))
        BasicText(
            SimpleDateFormat("EEEE d 'de' MMMM", locale).format(now).replaceFirstChar { it.uppercase() },
            style = Type.caption.copy(fontSize = 15.sp),
        )
    }
}

/** Botón redondo de acceso rápido: gris translúcido en reposo, blanco con foco. */
@Composable
private fun QuickTile(label: String, modifier: Modifier = Modifier, onClick: () -> Unit, icon: @Composable (Color) -> Unit) {
    val colors = LocalColors.current
    var focused by remember { mutableStateOf(false) }
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(68.dp)) {
        Surface(
            onClick = onClick,
            modifier = modifier.size(60.dp).onFocusChanged { focused = it.isFocused }
                .semantics { contentDescription = label }.testTag("cc:$label"),
            shape = ClickableSurfaceDefaults.shape(CircleShape),
            colors = ClickableSurfaceDefaults.colors(containerColor = colors.control, focusedContainerColor = colors.controlFocused),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.12f),
            glow = ClickableSurfaceDefaults.glow(focusedGlow = Glow(Color.Black.copy(alpha = 0.4f), 14.dp)),
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                icon(if (focused) colors.onControlFocused else colors.text)
            }
        }
        BasicText(
            label, style = Type.caption.copy(fontSize = 12.sp, textAlign = TextAlign.Center), maxLines = 1,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp),
        )
    }
}

/** Habitación de Hue: blanca si está prendida (como los accesorios de Casa en tvOS), gris si no. */
@Composable
private fun RoomTile(room: RoomState, modifier: Modifier, onClick: () -> Unit) {
    val colors = LocalColors.current
    var focused by remember { mutableStateOf(false) }
    val lit = room.on
    Surface(
        onClick = onClick,
        modifier = modifier.height(86.dp).onFocusChanged { focused = it.isFocused },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(20.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (lit) Color(0xFFF2F2F7) else colors.control,
            focusedContainerColor = if (lit) Color.White else colors.controlFocused,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
        glow = ClickableSurfaceDefaults.glow(focusedGlow = Glow(Color.Black.copy(alpha = 0.4f), 14.dp)),
    ) {
        val dark = lit || focused
        val main = if (dark) Color(0xFF1D1D1F) else colors.text
        Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.SpaceBetween) {
            BulbIcon(lit, 22.dp)
            Column {
                BasicText(room.name, style = Type.body.copy(color = main, fontSize = 15.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                BasicText(if (lit) "Encendida" else "Apagada", style = Type.caption.copy(color = main.copy(alpha = 0.6f), fontSize = 12.sp))
            }
        }
    }
}

/** "Ahora suena": portada, título y controles de lo que se reproduce en cualquier app. */
@Composable
private fun NowPlayingCard(np: NowPlaying, actions: ControlCenterActions) {
    val colors = LocalColors.current
    Column(
        Modifier.testTag("cc:nowPlaying").fillMaxWidth().padding(top = 10.dp).background(colors.control, RoundedCornerShape(20.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(56.dp).background(colors.tile, RoundedCornerShape(10.dp))) {
                np.artwork?.let { Image(it, null, Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp)), contentScale = ContentScale.Crop) }
            }
            Column(Modifier.weight(1f)) {
                BasicText(np.title, style = Type.body.copy(fontSize = 15.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                BasicText(listOf(np.subtitle, np.appLabel).filter { it.isNotBlank() }.joinToString(" · "),
                    style = Type.caption.copy(fontSize = 12.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            MediaButton("Anterior", actions.onPrevious) { TransportIcon(it, TransportKind.PREVIOUS) }
            MediaButton(if (np.playing) "Pausa" else "Reproducir", actions.onPlayPause) {
                TransportIcon(it, if (np.playing) TransportKind.PAUSE else TransportKind.PLAY)
            }
            MediaButton("Siguiente", actions.onNext) { TransportIcon(it, TransportKind.NEXT) }
        }
    }
}

@Composable
private fun MediaButton(label: String, onClick: () -> Unit, icon: @Composable (Color) -> Unit) {
    val colors = LocalColors.current
    var focused by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        modifier = Modifier.size(46.dp).onFocusChanged { focused = it.isFocused }.semantics { contentDescription = label }.testTag("cc:$label"),
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        colors = ClickableSurfaceDefaults.colors(containerColor = Color.Transparent, focusedContainerColor = colors.controlFocused),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.12f),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { icon(if (focused) colors.onControlFocused else colors.text) }
    }
}

private enum class TransportKind { PLAY, PAUSE, NEXT, PREVIOUS }

@Composable
private fun TransportIcon(color: Color, kind: TransportKind) {
    Canvas(Modifier.size(20.dp)) {
        val w = size.width
        val h = size.height
        fun tri(x0: Float, x1: Float) = androidx.compose.ui.graphics.Path().apply { moveTo(x0, 0f); lineTo(x1, h / 2); lineTo(x0, h); close() }
        when (kind) {
            TransportKind.PLAY -> drawPath(tri(w * 0.2f, w * 0.9f), color)
            TransportKind.PAUSE -> {
                drawRect(color, Offset(w * 0.18f, 0f), Size(w * 0.22f, h))
                drawRect(color, Offset(w * 0.6f, 0f), Size(w * 0.22f, h))
            }
            TransportKind.NEXT -> {
                drawPath(tri(0f, w * 0.5f), color); drawPath(tri(w * 0.45f, w * 0.95f), color)
            }
            TransportKind.PREVIOUS -> {
                drawPath(androidx.compose.ui.graphics.Path().apply { moveTo(w * 0.5f, 0f); lineTo(0f, h / 2); lineTo(w * 0.5f, h); close() }, color)
                drawPath(androidx.compose.ui.graphics.Path().apply { moveTo(w * 0.95f, 0f); lineTo(w * 0.45f, h / 2); lineTo(w * 0.95f, h); close() }, color)
            }
        }
    }
}

@Composable
private fun SceneChip(name: String, modifier: Modifier, onClick: () -> Unit) {
    val colors = LocalColors.current
    var focused by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        modifier = modifier.height(44.dp).onFocusChanged { focused = it.isFocused },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(14.dp)),
        colors = ClickableSurfaceDefaults.colors(containerColor = colors.control, focusedContainerColor = colors.controlFocused),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
    ) {
        Box(Modifier.fillMaxSize().padding(horizontal = 14.dp), contentAlignment = Alignment.CenterStart) {
            BasicText(name, style = Type.body.copy(color = if (focused) colors.onControlFocused else colors.text, fontSize = 14.sp),
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun MoonIcon(color: Color, filled: Boolean) {
    Canvas(Modifier.size(26.dp).graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)) {
        val r = size.minDimension / 2
        if (filled) {
            drawCircle(color, r * 0.9f)
            drawCircle(Color.Transparent, r * 0.75f, Offset(center.x + r * 0.45f, center.y - r * 0.35f), blendMode = androidx.compose.ui.graphics.BlendMode.Clear)
        } else {
            drawCircle(color, r * 0.45f)
            repeat(8) { i ->
                val a = Math.toRadians(i * 45.0)
                val start = Offset(center.x + (r * 0.65f * Math.cos(a)).toFloat(), center.y + (r * 0.65f * Math.sin(a)).toFloat())
                val end = Offset(center.x + (r * 0.92f * Math.cos(a)).toFloat(), center.y + (r * 0.92f * Math.sin(a)).toFloat())
                drawLine(color, start, end, strokeWidth = r * 0.14f)
            }
        }
    }
}

@Composable
private fun ScreensaverIcon(color: Color) {
    Canvas(Modifier.size(width = 28.dp, height = 22.dp)) {
        drawRoundRect(color, style = androidx.compose.ui.graphics.drawscope.Stroke(width = size.height * 0.1f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height * 0.15f))
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(size.width * 0.15f, size.height * 0.8f)
            lineTo(size.width * 0.42f, size.height * 0.42f)
            lineTo(size.width * 0.6f, size.height * 0.65f)
            lineTo(size.width * 0.72f, size.height * 0.5f)
            lineTo(size.width * 0.88f, size.height * 0.8f)
            close()
        }
        drawPath(path, color)
        drawCircle(color, size.height * 0.09f, Offset(size.width * 0.72f, size.height * 0.28f))
    }
}

@Composable
private fun GearIcon() {
    val gear = remember {
        val bmp = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
        SystemTiles.drawGear(android.graphics.Canvas(bmp), 48f, 48f, 44f)
        bmp.asImageBitmap()
    }
    Image(gear, null, Modifier.size(30.dp), filterQuality = FilterQuality.High)
}

@Composable
private fun BulbIcon(on: Boolean, size: Dp) {
    val glass = if (on) Color(0xFFFFC94D) else Color(0xFF8E8E93)
    Canvas(Modifier.size(size)) {
        val r = this.size.width * 0.36f
        val cx = this.size.width / 2
        drawCircle(glass, r, Offset(cx, r + this.size.height * 0.05f))
        drawRect(Color(0xFF8E8E93), Offset(cx - r * 0.5f, r * 2f), Size(r, this.size.height - r * 2.1f))
    }
}
