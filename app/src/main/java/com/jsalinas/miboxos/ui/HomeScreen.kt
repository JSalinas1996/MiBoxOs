@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.jsalinas.miboxos.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
    onLaunch: (TvApp) -> Unit,
    onAssistant: () -> Unit,
    onSettings: () -> Unit,
    firstTileFocus: FocusRequester,
) {
    var focused by remember { mutableStateOf<TvApp?>(null) }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Backdrop(focused ?: apps.firstOrNull())

        Column(Modifier.fillMaxSize()) {
            TopBar(onAssistant = onAssistant, onSettings = onSettings)

            // Zona "top shelf": nombre grande de la app con foco
            Box(Modifier.fillMaxWidth().weight(1f).padding(horizontal = 56.dp), contentAlignment = Alignment.BottomStart) {
                Crossfade(focused?.label.orEmpty(), animationSpec = tween(250), label = "hero") { label ->
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
                        modifier = if (index == 0) Modifier.focusRequester(firstTileFocus) else Modifier,
                    )
                }
            }
        }
    }

    LaunchedEffect(apps.isNotEmpty()) {
        if (apps.isNotEmpty()) runCatching { firstTileFocus.requestFocus() }
    }
}

/** Fondo a pantalla completa: la versión diminuta del banner escalada con filtrado = desenfoque barato (sin RenderEffect, que no existe en Android 9). */
@Composable
private fun Backdrop(app: TvApp?) {
    Crossfade(app?.backdrop, animationSpec = tween(450), label = "backdrop") { image ->
        Box(Modifier.fillMaxSize()) {
            if (image != null) {
                Image(
                    bitmap = image,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().alpha(0.85f),
                    contentScale = ContentScale.Crop,
                    filterQuality = FilterQuality.High,
                )
            }
        }
    }
    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(
                0f to Color.Black.copy(alpha = 0.35f),
                0.45f to Color.Black.copy(alpha = 0.15f),
                1f to Color.Black.copy(alpha = 0.85f),
            )
        )
    )
}

@Composable
private fun TopBar(onAssistant: () -> Unit, onSettings: () -> Unit) {
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
        PillButton(onClick = onAssistant) {
            Orb(active = false, level = 0f, size = 22.dp)
            Spacer(Modifier.size(10.dp))
            BasicText("Asistente", style = Type.body)
        }
        Spacer(Modifier.weight(1f))
        BasicText(SimpleDateFormat("HH:mm", Locale.getDefault()).format(now), style = Type.clock)
        Spacer(Modifier.size(20.dp))
        PillButton(onClick = onSettings) {
            BasicText("Ajustes", style = Type.body)
        }
    }
}

@Composable
private fun PillButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.12f),
            contentColor = Palette.text,
            focusedContainerColor = Color.White.copy(alpha = 0.95f),
            focusedContentColor = Color.Black,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.08f),
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            content()
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
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = Palette.tileFallback,
                focusedContainerColor = Palette.tileFallback,
            ),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.12f),
            glow = ClickableSurfaceDefaults.glow(
                focusedGlow = Glow(elevationColor = Color.Black.copy(alpha = 0.6f), elevation = 12.dp),
            ),
        ) {
            when {
                app.banner != null -> Image(
                    bitmap = app.banner,
                    contentDescription = app.label,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    filterQuality = FilterQuality.High,
                )
                app.icon != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Image(bitmap = app.icon, contentDescription = app.label, modifier = Modifier.size(48.dp))
                }
                else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    BasicText(app.label, style = Type.body)
                }
            }
            // Brillo diagonal sutil, como el reflejo de los íconos de tvOS
            if (isFocused) {
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.linearGradient(listOf(Color.White.copy(alpha = 0.18f), Color.Transparent))
                    )
                )
            }
        }
        BasicText(
            app.label,
            style = Type.caption.copy(color = Palette.text, textAlign = TextAlign.Center),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 12.dp).alpha(labelAlpha),
        )
    }
}
