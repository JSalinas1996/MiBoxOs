package com.jsalinas.miboxos.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jsalinas.miboxos.assistant.AssistantState
import kotlin.math.cos
import kotlin.math.sin

/** Asistente como Siri en tvOS: franja oscura abajo a todo lo ancho, orbe grande y texto grande. */
@Composable
fun AssistantOverlay(state: AssistantState, level: Float, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = state != AssistantState.Idle,
        modifier = modifier.fillMaxSize(),
        enter = fadeIn(tween(250)),
        exit = fadeOut(tween(250)),
    ) {
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.35f), 0.5f to Color.Black.copy(alpha = 0.55f), 0.8f to Color.Black.copy(alpha = 0.88f), 1f to Color.Black.copy(alpha = 0.95f))
            ),
            contentAlignment = Alignment.BottomStart,
        ) {
            Row(
                Modifier.padding(start = 72.dp, end = 120.dp, bottom = 56.dp).animateEnterExit(
                    enter = slideInVertically(tween(350)) { it / 3 },
                    exit = slideOutVertically(tween(250)) { it / 3 },
                ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(28.dp),
            ) {
                Orb(active = state == AssistantState.Listening || state == AssistantState.Thinking, level = level, size = 84.dp)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    when (state) {
                        AssistantState.Listening -> BasicText("Te escucho…", style = Type.siri)
                        AssistantState.Thinking -> BasicText("Pensando…", style = Type.siri)
                        is AssistantState.Done -> {
                            if (state.transcript.isNotBlank()) BasicText("“${state.transcript}”", style = Type.siri)
                            BasicText(state.reply, style = Type.siriReply)
                        }
                        is AssistantState.Failed -> BasicText(state.message, style = Type.siriReply.copy(color = Color.White))
                        AssistantState.Idle -> Unit
                    }
                }
            }
        }
    }
}

/** Orbe de colores que gira y crece con el volumen de la voz. */
@Composable
fun Orb(active: Boolean, level: Float, size: Dp, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "orb")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(if (active) 2_400 else 9_000, easing = LinearEasing)),
        label = "angle",
    )
    val pulse by animateFloatAsState(if (active) 0.75f + level * 0.5f else 0.7f, tween(120), label = "pulse")

    Canvas(modifier.size(size)) {
        val r = this.size.minDimension / 2
        OrbColors.forEachIndexed { i, color ->
            val a = angle * (if (i % 2 == 0) 1f else -1.3f) + i * 1.57f
            val c = center + Offset(cos(a), sin(a)) * r * 0.28f * pulse
            drawCircle(
                brush = Brush.radialGradient(listOf(color, color.copy(alpha = 0f)), center = c, radius = r * pulse),
                radius = r * pulse,
                center = c,
                blendMode = BlendMode.Screen,
            )
        }
    }
}
