package com.jsalinas.miboxos.screensaver

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jsalinas.miboxos.Prefs
import com.jsalinas.miboxos.ui.Inter
import com.jsalinas.miboxos.wallpaper.Wallpaper
import com.jsalinas.miboxos.wallpaper.Wallpapers
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Imágenes del modo reposo según la preferencia: tus fotos (si hay) o todas, sin "Minimal". */
fun screensaverImages(wallpapers: Wallpapers, prefs: Prefs): List<Wallpaper> {
    val photos = wallpapers.all().filter { it.source != null }
    val mine = photos.filter { it.source is Wallpaper.UserFile }
    return (if (prefs.screensaverOnlyMine && mine.isNotEmpty()) mine else photos).shuffled()
}

/**
 * Modo reposo como los Aerials de Apple TV: cada imagen se acerca y se desplaza despacio
 * (efecto Ken Burns), se funde con la siguiente, y abajo a la izquierda aparece su nombre unos segundos.
 */
@Composable
fun Screensaver(images: List<Wallpaper>, load: suspend (Wallpaper) -> ImageBitmap?, secondsPerImage: Int) {
    var index by remember { mutableIntStateOf(0) }
    val current = images.getOrNull(index % images.size.coerceAtLeast(1))
    // La imagen anterior queda en pantalla hasta que la nueva terminó de cargar (sin cuadros negros)
    var shown by remember { mutableStateOf<Pair<ImageBitmap, Int>?>(null) }
    LaunchedEffect(index, current?.id) {
        current?.let { wp -> load(wp)?.let { shown = it to index } }
    }
    var showCaption by remember { mutableStateOf(true) }

    LaunchedEffect(images, secondsPerImage) {
        if (images.size < 2) return@LaunchedEffect
        while (true) {
            delay(secondsPerImage * 1_000L)
            index++
        }
    }
    LaunchedEffect(index) {
        showCaption = true
        delay(5_000)
        showCaption = false
    }

    Box(Modifier.fillMaxSize().background(Color.Black).testTag("screensaver")) {
        Crossfade(shown, animationSpec = tween(1_500), label = "saver") { frame ->
            if (frame != null) KenBurns(frame.first, frame.second, secondsPerImage)
        }
        // Sombra suave arriba y abajo para que se lean la hora y el nombre
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
            0f to Color.Black.copy(alpha = 0.35f), 0.2f to Color.Transparent, 0.8f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.45f),
        )))
        Clock(Modifier.align(Alignment.TopEnd).padding(top = 36.dp, end = 56.dp))
        val captionAlpha by animateFloatAsState(if (showCaption) 1f else 0f, tween(800), label = "caption")
        BasicText(
            current?.name.orEmpty(),
            style = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 20.sp, color = Color.White),
            modifier = Modifier.align(Alignment.BottomStart).padding(start = 56.dp, bottom = 44.dp).alpha(captionAlpha),
        )
    }
}

@Composable
private fun KenBurns(image: ImageBitmap, index: Int, secondsPerImage: Int) {
    val progress = remember(index) { Animatable(0f) }
    LaunchedEffect(index) {
        // Dura más que el cambio de imagen para que siga moviéndose durante el fundido
        progress.animateTo(1f, tween((secondsPerImage + 2) * 1_000, easing = LinearEasing))
    }
    // Alterna dirección y punto de partida para que no se repita el mismo movimiento
    val direction = if (index % 2 == 0) 1f else -1f
    val zoomIn = index % 3 != 2
    Image(
        image,
        contentDescription = null,
        modifier = Modifier.fillMaxSize().graphicsLayer {
            val p = progress.value
            val scale = if (zoomIn) 1.05f + 0.10f * p else 1.15f - 0.10f * p
            scaleX = scale
            scaleY = scale
            translationX = direction * (p - 0.5f) * size.width * 0.05f
            translationY = (p - 0.5f) * size.height * 0.02f
        },
        contentScale = ContentScale.Crop,
        filterQuality = FilterQuality.High,
    )
}

@Composable
private fun Clock(modifier: Modifier) {
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = Date()
            delay(60_000 - System.currentTimeMillis() % 60_000)
        }
    }
    BasicText(
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(now),
        style = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 28.sp, color = Color.White),
        modifier = modifier,
    )
}
