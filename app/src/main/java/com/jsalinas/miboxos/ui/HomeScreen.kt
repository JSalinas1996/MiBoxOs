@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.jsalinas.miboxos.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Glow
import androidx.tv.material3.Surface
import com.jsalinas.miboxos.apps.TvApp
import com.jsalinas.miboxos.watchnext.WatchNextItem

// Medidas pensadas para 960×540 dp (1080p), con proporciones como el inicio de tvOS
private val TOP_SHELF = 292.dp
private val ICON_W = 150.dp
private val ICON_H = 84.dp
private val COL_GAP = 26.dp
private val ROW_PITCH = 132.dp
private const val COLUMNS = 5

/**
 * Inicio estilo tvOS: arriba el Top Shelf (el fondo elegido, a sangre) y abajo los íconos sobre un
 * material desenfocado. Al bajar de la primera fila el Top Shelf se va hacia arriba, como en Apple TV.
 */
@Composable
fun HomeScreen(
    apps: List<TvApp>,
    wallpaper: ImageBitmap?,
    frosted: ImageBitmap?,
    interactive: Boolean,
    onLaunch: (TvApp) -> Unit,
    firstTileFocus: FocusRequester,
    continueWatching: List<WatchNextItem> = emptyList(),
    onWatchNext: (WatchNextItem) -> Unit = {},
) {
    var focusedIndex by remember { mutableIntStateOf(0) }
    var gridHasFocus by remember { mutableStateOf(false) }
    var shelfHasFocus by remember { mutableStateOf(false) }
    val row = focusedIndex / COLUMNS
    val scrolled = row > 0 && !shelfHasFocus
    // Como en tvOS, el Top Shelf prioriza lo de la app enfocada; después, lo del resto de las apps
    val focusedPackage = apps.getOrNull(focusedIndex)?.packageName
    val shelfItems = continueWatching.sortedByDescending { it.packageName == focusedPackage }

    val offset by animateDpAsState(
        if (!scrolled) 0.dp else -(TOP_SHELF - 64.dp) - ROW_PITCH * (row - 1),
        tween(450, easing = FastOutSlowInEasing), label = "scroll",
    )
    val reveal by animateFloatAsState(if (scrolled) 1f else 0f, tween(450), label = "material")

    Box(Modifier.fillMaxSize()) {
        Wallpaper(wallpaper)
        Material(frosted, reveal)

        Column(
            Modifier
                .fillMaxWidth()
                .wrapContentHeight(Alignment.Top, unbounded = true)
                .offset(y = offset)
                .onFocusChanged { gridHasFocus = it.hasFocus },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.fillMaxWidth().height(TOP_SHELF), contentAlignment = Alignment.BottomStart) {
                if (shelfItems.isNotEmpty()) {
                    ContinueWatching(
                        items = shelfItems,
                        photoBackground = wallpaper != null,
                        interactive = interactive,
                        onOpen = onWatchNext,
                        modifier = Modifier.onFocusChanged { shelfHasFocus = it.hasFocus },
                    )
                }
            }
            apps.chunked(COLUMNS).forEachIndexed { r, rowApps ->
                Row(Modifier.height(ROW_PITCH).width(ICON_W * COLUMNS + COL_GAP * (COLUMNS - 1)), horizontalArrangement = Arrangement.spacedBy(COL_GAP)) {
                    rowApps.forEachIndexed { c, app ->
                        val index = r * COLUMNS + c
                        AppIcon(
                            app = app,
                            isFocused = gridHasFocus && !shelfHasFocus && focusedIndex == index,
                            onFocus = { focusedIndex = index },
                            onClick = { onLaunch(app) },
                            modifier = Modifier
                                .focusProperties { canFocus = interactive }
                                .then(if (index == 0) Modifier.focusRequester(firstTileFocus) else Modifier),
                        )
                    }
                }
            }
        }
    }

    // Al arrancar y al volver de Ajustes el foco va a la primera app; se reintenta cuadro a cuadro
    // porque en el primer cuadro el ícono puede no estar listo todavía
    LaunchedEffect(apps.isNotEmpty(), interactive) {
        if (apps.isEmpty() || !interactive) return@LaunchedEffect
        repeat(30) {
            if (gridHasFocus) return@LaunchedEffect
            runCatching { firstTileFocus.requestFocus() }
            withFrameNanos { }
        }
    }
}

/** "Seguir viendo" en el Top Shelf: lo que dejaste a medias, con su progreso; OK lo abre ahí mismo. */
@Composable
private fun ContinueWatching(
    items: List<WatchNextItem>,
    photoBackground: Boolean,
    interactive: Boolean,
    onOpen: (WatchNextItem) -> Unit,
    modifier: Modifier,
) {
    val labelColor = if (photoBackground) Color.White else LocalColors.current.onWallpaper
    Column(modifier.padding(bottom = 14.dp)) {
        BasicText(
            "Seguir viendo",
            style = Type.section.copy(color = labelColor.copy(alpha = 0.85f)),
            modifier = Modifier.padding(start = 53.dp, bottom = 10.dp),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 53.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            items(items, key = { it.id }) { item ->
                WatchNextCard(item, labelColor, Modifier.focusProperties { canFocus = interactive }) { onOpen(item) }
            }
        }
    }
}

@Composable
private fun WatchNextCard(item: WatchNextItem, labelColor: Color, modifier: Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val labelAlpha by animateFloatAsState(if (focused) 1f else 0f, tween(180), label = "wnLabel")
    Column(Modifier.width(196.dp)) {
        Surface(
            onClick = onClick,
            modifier = modifier.size(196.dp, 110.dp).onFocusChanged { focused = it.isFocused },
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
            colors = ClickableSurfaceDefaults.colors(containerColor = Color(0xFF1C1C1E), focusedContainerColor = Color(0xFF1C1C1E)),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
            glow = ClickableSurfaceDefaults.glow(focusedGlow = Glow(Color.Black.copy(alpha = 0.6f), 18.dp)),
        ) {
            if (item.image != null) {
                Image(item.image, item.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, filterQuality = FilterQuality.High)
            } else {
                Box(Modifier.fillMaxSize().padding(12.dp), contentAlignment = Alignment.BottomStart) {
                    BasicText(item.title, style = Type.body.copy(color = Color.White), maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            item.progress?.let { p ->
                Box(Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 8.dp), contentAlignment = Alignment.BottomStart) {
                    Box(Modifier.fillMaxWidth().height(4.dp).background(Color.White.copy(alpha = 0.3f), RoundedCornerShape(2.dp)))
                    Box(Modifier.fillMaxWidth(p).height(4.dp).background(Color.White, RoundedCornerShape(2.dp)))
                }
            }
        }
        Column(Modifier.padding(top = 12.dp).alpha(labelAlpha)) {
            BasicText(item.title, style = Type.tileLabel.copy(color = labelColor), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (item.subtitle.isNotBlank()) {
                BasicText(item.subtitle, style = Type.caption.copy(color = labelColor.copy(alpha = 0.7f)), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun Wallpaper(image: ImageBitmap?) {
    val colors = LocalColors.current
    Crossfade(image, animationSpec = tween(600), label = "wallpaper") { img ->
        if (img != null) {
            Image(img, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, filterQuality = FilterQuality.High)
        } else {
            // "Minimal": degradé sobrio con un halo arriba, del gris de tvOS
            val (glow, base) = if (colors.dark) Color(0xFF3A3A3F) to Color(0xFF0C0C0E) else Color.White to Color(0xFFD8D8DE)
            Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(glow, base), center = Offset(960f, -200f), radius = 2200f)))
        }
    }
}

/**
 * Material desenfocado: en reposo cubre solo la franja de los íconos (el Top Shelf queda nítido);
 * al bajar de fila sube hasta cubrir toda la pantalla.
 */
@Composable
private fun Material(frosted: ImageBitmap?, reveal: Float) {
    val colors = LocalColors.current
    val start = lerp(0.46f, -0.3f, reveal)
    val end = lerp(0.60f, 0f, reveal)
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
            .drawWithContent {
                drawContent()
                drawRect(
                    Brush.verticalGradient(start to Color.Transparent, end to Color.Black),
                    blendMode = BlendMode.DstIn,
                )
            },
    ) {
        if (frosted != null) {
            Image(frosted, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, filterQuality = FilterQuality.High)
        }
        Box(Modifier.fillMaxSize().background(if (frosted != null) colors.materialTint else colors.materialTint.copy(alpha = 0.25f)))
    }
}

@Composable
private fun AppIcon(
    app: TvApp,
    isFocused: Boolean,
    onFocus: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val labelAlpha by animateFloatAsState(if (isFocused) 1f else 0f, tween(180), label = "label")
    // Brillo especular que cruza el ícono al recibir el foco
    val shine = remember { Animatable(-0.6f) }
    LaunchedEffect(isFocused) {
        if (isFocused) {
            shine.snapTo(-0.6f)
            shine.animateTo(1.6f, tween(900, easing = LinearEasing))
        }
    }
    Column(Modifier.width(ICON_W), horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            onClick = onClick,
            modifier = modifier
                .size(ICON_W, ICON_H)
                .onFocusChanged { if (it.isFocused) onFocus() },
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = LocalColors.current.tile,
                focusedContainerColor = LocalColors.current.tile,
            ),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.17f),
            glow = ClickableSurfaceDefaults.glow(
                focusedGlow = Glow(elevationColor = Color.Black.copy(alpha = 0.6f), elevation = 22.dp),
            ),
        ) {
            Image(app.tile, app.label, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, filterQuality = FilterQuality.High)
            if (isFocused) {
                val p = shine.value
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.linearGradient(
                            (p - 0.25f).coerceIn(0f, 1f) to Color.Transparent,
                            p.coerceIn(0f, 1f) to Color.White.copy(alpha = 0.22f),
                            (p + 0.25f).coerceIn(0f, 1f) to Color.Transparent,
                            start = Offset.Zero,
                            end = Offset(600f, 340f),
                        )
                    )
                )
                // Luz cenital fija, como el reflejo de los íconos de tvOS
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.12f), Color.Transparent))))
            }
        }
        BasicText(
            app.label,
            style = Type.tileLabel.copy(textAlign = TextAlign.Center),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            // El nombre puede ser más ancho que el ícono sin empujar a los vecinos
            modifier = Modifier.requiredWidth(ICON_W + 60.dp).padding(top = 18.dp).alpha(labelAlpha),
        )
    }
}
