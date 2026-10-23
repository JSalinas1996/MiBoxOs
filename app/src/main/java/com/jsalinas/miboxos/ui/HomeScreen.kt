@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.jsalinas.miboxos.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Glow
import androidx.tv.material3.Surface
import com.jsalinas.miboxos.apps.TvApp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(
    apps: List<TvApp>,
    wallpaper: ImageBitmap?,
    interactive: Boolean,
    onLaunch: (TvApp) -> Unit,
    onAssistant: () -> Unit,
    onSettings: () -> Unit,
    firstTileFocus: FocusRequester,
) {
    var focused by remember { mutableStateOf<TvApp?>(null) }

    Box(Modifier.fillMaxSize()) {
        Background(wallpaper)

        Column(Modifier.fillMaxSize()) {
            TopBar(interactive, onAssistant = onAssistant, onSettings = onSettings)

            Box(Modifier.fillMaxWidth().weight(1f).padding(horizontal = 56.dp), contentAlignment = Alignment.BottomStart) {
                Crossfade((focused ?: apps.firstOrNull())?.label.orEmpty(), animationSpec = tween(250), label = "hero") { label ->
                    BasicText(label, style = Type.hero, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(5),
                modifier = Modifier.fillMaxWidth().height(300.dp),
                contentPadding = PaddingValues(start = 56.dp, end = 56.dp, top = 24.dp, bottom = 48.dp),
                horizontalArrangement = Arrangement.spacedBy(28.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                itemsIndexed(apps, key = { _, app -> app.packageName }) { index, app ->
                    AppTile(
                        app = app,
                        isFocused = focused?.packageName == app.packageName,
                        onFocus = { focused = app },
                        onClick = { onLaunch(app) },
                        modifier = Modifier
                            .focusProperties { canFocus = interactive }
                            .then(if (index == 0) Modifier.focusRequester(firstTileFocus) else Modifier),
                    )
                }
            }
        }
    }

    LaunchedEffect(apps.isNotEmpty()) {
        if (apps.isNotEmpty()) runCatching { firstTileFocus.requestFocus() }
    }
}

/** Foto elegida o, en "Minimal", un degradé sobrio con un halo de luz arriba a la izquierda. */
@Composable
private fun Background(wallpaper: ImageBitmap?) {
    val colors = LocalColors.current
    Crossfade(wallpaper, animationSpec = tween(500), label = "wallpaper") { image ->
        if (image != null) {
            Box(Modifier.fillMaxSize()) {
                Image(
                    bitmap = image,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    filterQuality = FilterQuality.High,
                )
                // Oscurece solo la franja de las apps para que los nombres se lean sobre cualquier foto
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(0.45f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.6f))
                    )
                )
            }
        } else {
            val (glow, base) = if (colors.dark) Color(0xFF2C2C30) to Color(0xFF0A0A0B) else Color(0xFFFFFFFF) to Color(0xFFE3E3E8)
            Box(
                Modifier.fillMaxSize().background(
                    Brush.radialGradient(listOf(glow, base), center = Offset(300f, 0f), radius = 2400f)
                )
            )
        }
    }
}

@Composable
private fun TopBar(interactive: Boolean, onAssistant: () -> Unit, onSettings: () -> Unit) {
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = Date()
            delay(60_000 - System.currentTimeMillis() % 60_000)
        }
    }
    Row(
        Modifier.fillMaxWidth().padding(start = 56.dp, end = 56.dp, top = 32.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PillButton(interactive, onClick = onAssistant) { color ->
            Orb(active = false, level = 0f, size = 22.dp)
            Spacer(Modifier.size(10.dp))
            BasicText("Asistente", style = Type.body.copy(color = color))
        }
        Spacer(Modifier.weight(1f))
        BasicText(SimpleDateFormat("HH:mm", Locale.getDefault()).format(now), style = Type.clock)
        Spacer(Modifier.size(20.dp))
        PillButton(interactive, onClick = onSettings) { color ->
            BasicText("Ajustes", style = Type.body.copy(color = color))
        }
    }
}

/** Botón translúcido sobre el fondo; al enfocarse se vuelve sólido como en tvOS. */
@Composable
private fun PillButton(interactive: Boolean, onClick: () -> Unit, content: @Composable (Color) -> Unit) {
    val colors = LocalColors.current
    var hasFocus by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        modifier = Modifier
            .focusProperties { canFocus = interactive }
            .onFocusChanged { hasFocus = it.isFocused },
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = colors.onWallpaper.copy(alpha = 0.14f),
            focusedContainerColor = colors.controlFocused,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.08f),
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            content(if (hasFocus) colors.onControlFocused else colors.onWallpaper)
        }
    }
}

@Composable
private fun AppTile(
    app: TvApp,
    isFocused: Boolean,
    onFocus: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val labelAlpha by animateFloatAsState(if (isFocused) 1f else 0f, tween(200), label = "label")
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            onClick = onClick,
            modifier = modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .onFocusChanged { if (it.isFocused) onFocus() },
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(14.dp)),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = LocalColors.current.tile,
                focusedContainerColor = LocalColors.current.tile,
            ),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.12f),
            // Borde finito: sin él, un banner negro desaparece sobre un fondo espacial negro
            border = ClickableSurfaceDefaults.border(
                border = Border(BorderStroke(1.dp, LocalColors.current.onWallpaper.copy(alpha = 0.14f))),
            ),
            glow = ClickableSurfaceDefaults.glow(
                focusedGlow = Glow(elevationColor = Color.Black.copy(alpha = 0.55f), elevation = 14.dp),
            ),
        ) {
            Image(
                bitmap = app.tile,
                contentDescription = app.label,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                filterQuality = FilterQuality.High,
            )
            // Reflejo diagonal sutil sobre la tarjeta enfocada, como en tvOS
            if (isFocused) {
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.linearGradient(listOf(Color.White.copy(alpha = 0.16f), Color.Transparent))
                    )
                )
            }
        }
        BasicText(
            app.label,
            style = Type.tileLabel.copy(textAlign = TextAlign.Center),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 12.dp).alpha(labelAlpha),
        )
    }
}
