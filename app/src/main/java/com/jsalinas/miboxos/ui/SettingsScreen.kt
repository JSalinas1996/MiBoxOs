@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.jsalinas.miboxos.ui

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Glow
import androidx.tv.material3.Surface
import com.jsalinas.miboxos.apps.SystemTiles
import com.jsalinas.miboxos.hue.HueStatus
import com.jsalinas.miboxos.update.Release
import com.jsalinas.miboxos.update.UpdateStatus
import com.jsalinas.miboxos.wallpaper.Wallpaper

enum class SettingsPage(val title: String) {
    ROOT("Ajustes"),
    APPEARANCE("Apariencia"),
    WALLPAPER("Fondo"),
    SCREENSAVER("Modo reposo"),
    APPS("Apps"),
    HUE("Philips Hue"),
    ASSISTANT("Asistente"),
    ABOUT("Acerca de"),
}

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
    val version: String,
    val screensaverSeconds: Int,
    val screensaverOnlyMine: Boolean,
    val hasOwnPhotos: Boolean,
    val globalAssistantOn: Boolean = false,
    val assistantKeyLabel: String = "Botón de voz",
    val watchNextAllowed: Boolean = false,
    /** Apps ocultas (paquete, nombre). */
    val hiddenApps: List<Pair<String, String>> = emptyList(),
    val customOrder: Boolean = false,
    val voiceReplies: Boolean = true,
    val nowPlayingAccess: Boolean = false,
    val update: UpdateStatus = UpdateStatus.Idle,
)

class SettingsActions(
    val onNavigate: (SettingsPage) -> Unit,
    val onDarkMode: (Boolean) -> Unit,
    val onWallpaper: (String) -> Unit,
    val onHuePair: () -> Unit,
    val onSystemSettings: () -> Unit,
    val onScreensaverSeconds: (Int) -> Unit = {},
    val onScreensaverOnlyMine: (Boolean) -> Unit = {},
    val onScreensaverPreview: () -> Unit = {},
    val onScreensaverSystem: () -> Unit = {},
    val onGlobalAssistant: () -> Unit = {},
    val onLearnKey: () -> Unit = {},
    val onWatchNextPermission: () -> Unit = {},
    val onUnhide: (String) -> Unit = {},
    val onResetOrder: () -> Unit = {},
    val onVoiceReplies: (Boolean) -> Unit = {},
    val onNowPlayingAccess: () -> Unit = {},
    val onCheckUpdate: () -> Unit = {},
    val onInstallUpdate: (Release) -> Unit = {},
)

/**
 * Ajustes a pantalla completa como la app Ajustes de tvOS: título centrado arriba, ilustración grande
 * a la izquierda (cambia con la fila enfocada) y lista de filas a la derecha. "Atrás" sube un nivel
 * (lo maneja MainActivity).
 */
@Composable
fun SettingsScreen(visible: Boolean, page: SettingsPage, state: SettingsState, actions: SettingsActions) {
    AnimatedVisibility(visible = visible, enter = fadeIn(tween(220)), exit = fadeOut(tween(180))) {
        val colors = LocalColors.current
        var illustration by remember { mutableStateOf("root") }
        val firstRow = remember(page) { FocusRequester() }
        // Al volver a la raíz, el foco vuelve a la fila de la subpágina de la que se salió
        val rootRows = remember { SettingsPage.entries.associateWith { FocusRequester() } }
        var lastPage by remember { mutableStateOf(SettingsPage.APPEARANCE) }
        var listHasFocus by remember { mutableStateOf(false) }

        Box(Modifier.fillMaxSize().background(colors.settingsBackground)) {
            Crossfade(page.title, animationSpec = tween(200), label = "title") {
                BasicText(it, style = Type.largeTitle.copy(textAlign = TextAlign.Center), modifier = Modifier.fillMaxWidth().padding(top = 40.dp))
            }
            Row(Modifier.fillMaxSize().padding(top = 100.dp, bottom = 24.dp)) {
                Box(Modifier.weight(0.46f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                    Illustration(illustration, state)
                }
                Column(
                    Modifier.weight(0.54f).fillMaxHeight().padding(end = 72.dp)
                        .onFocusChanged { listHasFocus = it.hasFocus }
                        .verticalScroll(rememberScrollState())
                        // Margen para que la fila enfocada crezca y proyecte sombra sin recortarse
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    when (page) {
                        SettingsPage.ROOT -> RootPage(state, actions, rootRows) { illustration = it }
                        SettingsPage.APPEARANCE -> AppearancePage(state, actions, firstRow) { illustration = it }
                        SettingsPage.WALLPAPER -> WallpaperPage(state, actions, firstRow) { illustration = it }
                        SettingsPage.SCREENSAVER -> ScreensaverPage(state, actions, firstRow) { illustration = it }
                        SettingsPage.APPS -> AppsPage(state, actions, firstRow) { illustration = it }
                        SettingsPage.HUE -> HuePage(state, actions, firstRow) { illustration = it }
                        SettingsPage.ASSISTANT -> AssistantPage(state, actions, firstRow) { illustration = it }
                        SettingsPage.ABOUT -> AboutPage(state, actions, firstRow) { illustration = it }
                    }
                }
            }
        }

        // Foco al cambiar de página: primera fila, o al volver a la raíz, la fila de la que se salió.
        // Se reintenta cuadro a cuadro hasta que la lista tenga el foco (la página nueva tarda un cuadro).
        LaunchedEffect(page) {
            if (page != SettingsPage.ROOT) lastPage = page
            val target = if (page == SettingsPage.ROOT) rootRows.getValue(lastPage) else firstRow
            withFrameNanos { }
            repeat(20) {
                runCatching { target.requestFocus() }
                withFrameNanos { }
                if (listHasFocus) return@LaunchedEffect
            }
        }
    }
}

@Composable
private fun RootPage(state: SettingsState, actions: SettingsActions, rows: Map<SettingsPage, FocusRequester>, onIllustration: (String) -> Unit) {
    val wallpaperName = state.wallpapers.firstOrNull { it.id == state.selectedWallpaper }?.name ?: "Minimal"
    val hueValue = when (state.hue) {
        is HueStatus.Paired -> "Vinculado"
        HueStatus.NotPaired -> "Sin vincular"
        is HueStatus.Failed -> "Sin conexión"
        else -> "Vinculando…"
    }
    (state.update as? UpdateStatus.Available)?.let { available ->
        SettingsRow("Actualizar a ${available.release.version}", "Nueva versión", chevron = true,
            onFocus = { onIllustration("about") }) { actions.onInstallUpdate(available.release) }
    }
    SettingsRow("Apariencia", if (state.darkMode) "Oscuro" else "Claro", chevron = true, focus = rows[SettingsPage.APPEARANCE],
        onFocus = { onIllustration("appearance") }) { actions.onNavigate(SettingsPage.APPEARANCE) }
    SettingsRow("Fondo", wallpaperName, chevron = true, focus = rows[SettingsPage.WALLPAPER],
        onFocus = { onIllustration("wallpaper:${state.selectedWallpaper}") }) { actions.onNavigate(SettingsPage.WALLPAPER) }
    SettingsRow("Modo reposo", "Cada ${secondsLabel(state.screensaverSeconds)}", chevron = true, focus = rows[SettingsPage.SCREENSAVER],
        onFocus = { onIllustration("screensaver") }) { actions.onNavigate(SettingsPage.SCREENSAVER) }
    SettingsRow("Philips Hue", hueValue, chevron = true, focus = rows[SettingsPage.HUE],
        onFocus = { onIllustration("hue") }) { actions.onNavigate(SettingsPage.HUE) }
    SettingsRow("Apps", if (state.hiddenApps.isEmpty()) (if (state.customOrder) "Orden propio" else "Orden por uso") else "${state.hiddenApps.size} ocultas",
        chevron = true, focus = rows[SettingsPage.APPS], onFocus = { onIllustration("root") }) { actions.onNavigate(SettingsPage.APPS) }
    SettingsRow("Ahora suena", if (state.nowPlayingAccess) "Activado" else "Dar acceso", chevron = !state.nowPlayingAccess,
        onFocus = { onIllustration("watchnext") }) { if (!state.nowPlayingAccess) actions.onNowPlayingAccess() }
    SettingsRow("Seguir viendo", if (state.watchNextAllowed) "Activado" else "Dar permiso", chevron = !state.watchNextAllowed,
        onFocus = { onIllustration("watchnext") }) { if (!state.watchNextAllowed) actions.onWatchNextPermission() }
    SettingsRow("Asistente", if (state.assistantReady) "Listo" else "Falta API key", chevron = true, focus = rows[SettingsPage.ASSISTANT],
        onFocus = { onIllustration("assistant") }) { actions.onNavigate(SettingsPage.ASSISTANT) }
    SectionGap()
    SettingsRow("Ajustes de Android TV", null, chevron = true, onFocus = { onIllustration("root") }, onClick = actions.onSystemSettings)
    SettingsRow("Acerca de", null, chevron = true, focus = rows[SettingsPage.ABOUT],
        onFocus = { onIllustration("about") }) { actions.onNavigate(SettingsPage.ABOUT) }
}

@Composable
private fun AppearancePage(state: SettingsState, actions: SettingsActions, first: FocusRequester, onIllustration: (String) -> Unit) {
    LaunchedEffect(Unit) { onIllustration("appearance") }
    SettingsRow("Oscuro", null, checked = state.darkMode, focus = first) { actions.onDarkMode(true) }
    SettingsRow("Claro", null, checked = !state.darkMode) { actions.onDarkMode(false) }
    Footer("Cambia el aspecto del inicio, Ajustes y el asistente.")
}

@Composable
private fun WallpaperPage(state: SettingsState, actions: SettingsActions, first: FocusRequester, onIllustration: (String) -> Unit) {
    state.wallpapers.chunked(3).forEachIndexed { r, rowItems ->
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp), modifier = Modifier.padding(vertical = 6.dp)) {
            rowItems.forEachIndexed { c, wp ->
                WallpaperOption(
                    wallpaper = wp,
                    thumb = state.thumbs[wp.id],
                    selected = wp.id == state.selectedWallpaper,
                    focus = if (r == 0 && c == 0) first else null,
                    onFocus = { onIllustration("wallpaper:${wp.id}") },
                ) { actions.onWallpaper(wp.id) }
            }
        }
    }
    Footer("Para usar tus fotos, copialas a ${state.userFolder}.")
}

@Composable
private fun ScreensaverPage(state: SettingsState, actions: SettingsActions, first: FocusRequester, onIllustration: (String) -> Unit) {
    LaunchedEffect(Unit) { onIllustration("screensaver") }
    SettingsRow("Probar ahora", null, focus = first, onClick = actions.onScreensaverPreview)
    SectionTitle("IMÁGENES")
    SettingsRow("Todas", null, checked = !state.screensaverOnlyMine || !state.hasOwnPhotos) { actions.onScreensaverOnlyMine(false) }
    SettingsRow("Solo mis fotos", if (state.hasOwnPhotos) null else "No hay fotos propias", checked = state.screensaverOnlyMine && state.hasOwnPhotos) {
        actions.onScreensaverOnlyMine(true)
    }
    SectionTitle("CAMBIAR CADA")
    listOf(15, 30, 60).forEach { seconds ->
        SettingsRow(secondsLabel(seconds), null, checked = state.screensaverSeconds == seconds) { actions.onScreensaverSeconds(seconds) }
    }
    SectionGap()
    SettingsRow("Activar en Android TV", null, chevron = true, onClick = actions.onScreensaverSystem)
    Footer("Android TV lo arranca solo después del tiempo de inactividad, también dentro de otras apps. En Protector de pantalla elegí \"MiBoxOs\". Cualquier botón del control lo cierra.")
}

private fun secondsLabel(seconds: Int) = if (seconds >= 60) "${seconds / 60} min" else "$seconds s"

@Composable
private fun SectionTitle(text: String) {
    BasicText(text, style = Type.section, modifier = Modifier.padding(start = 24.dp, top = 14.dp, bottom = 2.dp))
}

@Composable
private fun HuePage(state: SettingsState, actions: SettingsActions, first: FocusRequester, onIllustration: (String) -> Unit) {
    LaunchedEffect(Unit) { onIllustration("hue") }
    val (status, button) = when (val h = state.hue) {
        HueStatus.NotPaired -> "Sin vincular" to "Vincular"
        HueStatus.Searching -> "Buscando el bridge…" to null
        is HueStatus.PressButton -> "Apretá el botón redondo del bridge (${h.secondsLeft} s)" to null
        is HueStatus.Paired -> h.summary to "Volver a vincular"
        is HueStatus.Failed -> h.message to "Reintentar"
    }
    SettingsRow("Estado", status, focus = if (button == null) first else null) {}
    button?.let { SettingsRow(it, null, focus = first, onClick = actions.onHuePair) }
    Footer("Se conecta directo al Hue Bridge de tu casa. Después podés pedirle al asistente cosas como \"apagá el living\".")
}

@Composable
private fun AssistantPage(state: SettingsState, actions: SettingsActions, first: FocusRequester, onIllustration: (String) -> Unit) {
    LaunchedEffect(Unit) { onIllustration("assistant") }
    SettingsRow("En cualquier app", if (state.globalAssistantOn) "Activado" else "Desactivado", chevron = true, focus = first,
        onClick = actions.onGlobalAssistant)
    SettingsRow("Botón del asistente", state.assistantKeyLabel, onClick = actions.onLearnKey)
    SettingsRow("Responder en voz", if (state.voiceReplies) "Sí" else "No") { actions.onVoiceReplies(!state.voiceReplies) }
    SettingsRow("API key de Gemini", if (state.assistantReady) "Cargada" else "Falta") {}
    SettingsRow("Modelo", state.model) {}
    Footer(
        "Activado, el botón de voz del control abre el asistente encima de cualquier app; mantenido apretado abre el " +
            "Centro de control. Para activarlo: En cualquier app → Accesibilidad → MiBoxOs. Si el botón no responde, " +
            "elegí \"Botón del asistente\" y apretá el botón de voz para que lo aprenda."
    )
}

@Composable
private fun AboutPage(state: SettingsState, actions: SettingsActions, first: FocusRequester, onIllustration: (String) -> Unit) {
    LaunchedEffect(Unit) { onIllustration("about") }
    SettingsRow("Versión", state.version, focus = first) {}
    when (val u = state.update) {
        is UpdateStatus.Available -> SettingsRow("Instalar ${u.release.version}", "Nueva versión", chevron = true) { actions.onInstallUpdate(u.release) }
        else -> SettingsRow("Buscar actualización", when (u) {
            UpdateStatus.Checking -> "Buscando…"
            UpdateStatus.UpToDate -> "Estás al día"
            UpdateStatus.Downloading -> "Descargando…"
            UpdateStatus.WaitingConfirmation -> "Confirmá en pantalla"
            is UpdateStatus.Failed -> u.message
            else -> ""
        }, onClick = actions.onCheckUpdate)
    }
    SettingsRow("Fondos", "NASA · dominio público") {}
    SettingsRow("Tipografía", "Inter · SIL OFL") {}
}

@Composable
private fun AppsPage(state: SettingsState, actions: SettingsActions, first: FocusRequester, onIllustration: (String) -> Unit) {
    LaunchedEffect(Unit) { onIllustration("root") }
    SettingsRow("Orden automático (por uso)", null, checked = !state.customOrder, focus = first, onClick = actions.onResetOrder)
    if (state.hiddenApps.isNotEmpty()) {
        SectionTitle("OCULTAS")
        state.hiddenApps.forEach { (pkg, label) -> SettingsRow(label, "Mostrar") { actions.onUnhide(pkg) } }
    }
    Footer("Para mover u ocultar una app, mantené apretado OK sobre su ícono en el inicio.")
}

/** Fila de tvOS: translúcida en reposo; con foco se vuelve blanca, crece apenas y proyecta sombra. */
@Composable
private fun SettingsRow(
    title: String,
    value: String?,
    chevron: Boolean = false,
    checked: Boolean = false,
    focus: FocusRequester? = null,
    onFocus: () -> Unit = {},
    onClick: () -> Unit,
) {
    val colors = LocalColors.current
    var hasFocus by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .then(if (focus != null) Modifier.focusRequester(focus) else Modifier)
            .onFocusChanged {
                hasFocus = it.isFocused
                if (it.isFocused) onFocus()
            },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
        colors = ClickableSurfaceDefaults.colors(containerColor = colors.control, focusedContainerColor = colors.controlFocused),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.035f),
        glow = ClickableSurfaceDefaults.glow(focusedGlow = Glow(Color.Black.copy(alpha = 0.35f), 14.dp)),
    ) {
        val main = if (hasFocus) colors.onControlFocused else colors.text
        val secondary = if (hasFocus) colors.onControlFocused.copy(alpha = 0.6f) else colors.secondary
        Row(Modifier.fillMaxSize().padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
            BasicText(title, style = Type.row.copy(color = main), maxLines = 1)
            // El valor ocupa todo el ancho libre y se alinea a la derecha
            BasicText(value.orEmpty(), style = Type.row.copy(color = secondary, textAlign = TextAlign.End), maxLines = 1,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(start = 20.dp))
            if (checked) BasicText("✓", style = Type.row.copy(color = if (hasFocus) main else colors.accent), modifier = Modifier.padding(start = 12.dp))
            if (chevron) BasicText("›", style = Type.row.copy(color = secondary, fontSize = Type.row.fontSize * 1.4f), modifier = Modifier.padding(start = 12.dp))
        }
    }
}

@Composable
private fun WallpaperOption(
    wallpaper: Wallpaper,
    thumb: ImageBitmap?,
    selected: Boolean,
    focus: FocusRequester?,
    onFocus: () -> Unit,
    onClick: () -> Unit,
) {
    val colors = LocalColors.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            onClick = onClick,
            modifier = Modifier
                .size(width = 150.dp, height = 84.dp)
                .then(if (focus != null) Modifier.focusRequester(focus) else Modifier)
                .onFocusChanged { if (it.isFocused) onFocus() },
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
            colors = ClickableSurfaceDefaults.colors(containerColor = colors.tile, focusedContainerColor = colors.tile),
            border = ClickableSurfaceDefaults.border(
                border = if (selected) Border(BorderStroke(3.dp, colors.accent)) else Border.None,
                focusedBorder = if (selected) Border(BorderStroke(3.dp, colors.accent)) else Border.None,
            ),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
            glow = ClickableSurfaceDefaults.glow(focusedGlow = Glow(Color.Black.copy(alpha = 0.5f), 16.dp)),
        ) {
            if (thumb != null) {
                Image(thumb, wallpaper.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, filterQuality = FilterQuality.High)
            } else if (wallpaper.source == null) {
                MinimalSwatch(Modifier.fillMaxSize())
            }
        }
        BasicText(
            (if (selected) "✓ " else "") + wallpaper.name,
            style = Type.caption.copy(textAlign = TextAlign.Center),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(150.dp).padding(top = 10.dp),
        )
    }
}

@Composable
private fun Footer(text: String) {
    BasicText(text, style = Type.caption.copy(fontSize = Type.caption.fontSize * 1.1f), modifier = Modifier.padding(start = 24.dp, top = 10.dp, end = 24.dp))
}

@Composable
private fun SectionGap() = Spacer(Modifier.height(14.dp))

/** Ilustración grande de la izquierda, como las de Ajustes en tvOS. */
@Composable
private fun Illustration(id: String, state: SettingsState) {
    Crossfade(id, animationSpec = tween(250), label = "illustration") { current ->
        Box(Modifier.size(width = 340.dp, height = 300.dp), contentAlignment = Alignment.Center) {
            when {
                current.startsWith("wallpaper:") -> {
                    val wpId = current.removePrefix("wallpaper:")
                    val thumb = state.thumbs[wpId]
                    val mod = Modifier.width(330.dp).aspectRatio(16f / 9f).shadow(24.dp, RoundedCornerShape(16.dp)).clip(RoundedCornerShape(16.dp))
                    if (thumb != null) Image(thumb, null, mod, contentScale = ContentScale.Crop, filterQuality = FilterQuality.High)
                    else MinimalSwatch(mod)
                }
                current == "screensaver" -> ScreensaverArt(state)
                current == "appearance" -> AppearanceArt()
                current == "hue" -> BulbArt(on = state.hue is HueStatus.Paired)
                current == "assistant" -> Orb(active = true, level = 0.25f, size = 210.dp)
                current == "watchnext" -> WatchNextArt()
                current == "about" -> AboutArt(state.version)
                else -> GearArt()
            }
        }
    }
}

/** Tres fondos apilados, como la ilustración de Protector de pantalla en tvOS. */
@Composable
private fun ScreensaverArt(state: SettingsState) {
    val images = listOf("aurora", "nebulosa_carina", "tierra_desde_la_luna").mapNotNull { state.thumbs[it] }
    Box(contentAlignment = Alignment.Center) {
        images.forEachIndexed { i, img ->
            val offset = (i - 1) * 26
            Image(
                img, null,
                Modifier.padding(start = (40 + offset).coerceAtLeast(0).dp, top = (40 + offset).coerceAtLeast(0).dp)
                    .width(260.dp).aspectRatio(16f / 9f)
                    .shadow(20.dp, RoundedCornerShape(14.dp)).clip(RoundedCornerShape(14.dp)),
                contentScale = ContentScale.Crop, filterQuality = FilterQuality.High,
            )
        }
        if (images.isEmpty()) GearArt()
    }
}

/** Tarjeta con barra de progreso, como las de "Seguir viendo". */
@Composable
private fun WatchNextArt() {
    Box(
        Modifier.width(300.dp).aspectRatio(16f / 9f).shadow(24.dp, RoundedCornerShape(16.dp)).clip(RoundedCornerShape(16.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF3A3A3C), Color(0xFF111113)))),
        contentAlignment = Alignment.BottomStart,
    ) {
        Canvas(Modifier.size(64.dp).align(Alignment.Center)) {
            val path = androidx.compose.ui.graphics.Path().apply {
                moveTo(size.width * 0.3f, size.height * 0.2f); lineTo(size.width * 0.8f, size.height * 0.5f); lineTo(size.width * 0.3f, size.height * 0.8f); close()
            }
            drawPath(path, Color.White)
        }
        Box(Modifier.padding(16.dp).fillMaxWidth().height(5.dp).background(Color.White.copy(alpha = 0.3f), RoundedCornerShape(3.dp)))
        Box(Modifier.padding(16.dp).fillMaxWidth(0.6f).height(5.dp).background(Color.White, RoundedCornerShape(3.dp)))
    }
}

@Composable
private fun GearArt() {
    val gear = remember {
        val bmp = Bitmap.createBitmap(480, 480, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bmp)
        SystemTiles.drawGear(canvas, 240f, 240f, 220f)
        bmp.asImageBitmap()
    }
    Image(gear, null, Modifier.size(240.dp), filterQuality = FilterQuality.High)
}

/** Círculo mitad oscuro, mitad claro: el ícono de Apariencia. */
@Composable
private fun AppearanceArt() {
    Canvas(Modifier.size(220.dp).shadow(24.dp, RoundedCornerShape(110.dp))) {
        val r = size.minDimension / 2
        drawArc(Color(0xFF1C1C1E), 90f, 180f, useCenter = true)
        drawArc(Color(0xFFF2F2F7), -90f, 180f, useCenter = true)
        drawCircle(Color(0xFF8E8E93), radius = r - 2f, style = Stroke(width = 4f))
    }
}

/** Lamparita: encendida si Hue está vinculado. */
@Composable
private fun BulbArt(on: Boolean) {
    val glass = if (on) Color(0xFFFFD27A) else Color(0xFF8E8E93)
    Canvas(Modifier.size(width = 200.dp, height = 260.dp)) {
        val cx = size.width / 2
        val r = size.width * 0.38f
        val cy = r + size.height * 0.08f
        if (on) drawCircle(Brush.radialGradient(listOf(Color(0x66FFC857), Color.Transparent), Offset(cx, cy), r * 2.2f), r * 2.2f, Offset(cx, cy))
        drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.9f), glass), Offset(cx - r * 0.3f, cy - r * 0.35f), r * 1.4f), r, Offset(cx, cy))
        val neckTop = cy + r * 0.82f
        drawRect(glass, Offset(cx - r * 0.42f, neckTop - r * 0.15f), Size(r * 0.84f, r * 0.3f))
        repeat(3) { i ->
            drawRoundRect(Color(0xFF8E8E93), Offset(cx - r * 0.45f, neckTop + r * 0.12f + i * r * 0.2f), Size(r * 0.9f, r * 0.15f),
                androidx.compose.ui.geometry.CornerRadius(r * 0.07f))
        }
        drawCircle(Color(0xFF636366), r * 0.18f, Offset(cx, neckTop + r * 0.82f))
    }
}

@Composable
private fun AboutArt(version: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(width = 260.dp, height = 146.dp).shadow(24.dp, RoundedCornerShape(18.dp)).clip(RoundedCornerShape(18.dp))
                .background(Brush.linearGradient(listOf(Color(0xFF2C2C2E), Color(0xFF0B0B0D)))),
            contentAlignment = Alignment.Center,
        ) {
            BasicText("MiBoxOs", style = Type.largeTitle.copy(color = Color.White))
        }
        BasicText("Versión $version", style = Type.caption, modifier = Modifier.padding(top = 16.dp))
    }
}

@Composable
private fun MinimalSwatch(modifier: Modifier) {
    Box(modifier.background(Brush.horizontalGradient(0.5f to Color(0xFF1C1C1E), 0.5f to Color(0xFFE5E5EA))))
}
