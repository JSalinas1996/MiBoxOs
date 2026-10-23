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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jsalinas.miboxos.assistant.AssistantState
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun AssistantOverlay(state: AssistantState, level: Float, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(
            visible = state != AssistantState.Idle,
            enter = fadeIn(tween(200)) + slideInVertically(tween(300)) { it / 2 },
            exit = fadeOut(tween(200)) + slideOutVertically(tween(300)) { it / 2 },
        ) {
            Row(
                modifier = Modifier
                    .padding(bottom = 40.dp)
                    .widthIn(min = 360.dp, max = 720.dp)
                    .background(Palette.glass, RoundedCornerShape(28.dp))
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Orb(active = state == AssistantState.Listening || state == AssistantState.Thinking, level = level, size = 48.dp)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    when (state) {
                        AssistantState.Listening -> BasicText("Te escucho…", style = Type.title)
                        AssistantState.Thinking -> BasicText("Pensando…", style = Type.title)
                        is AssistantState.Done -> {
                            if (state.transcript.isNotBlank()) BasicText("“${state.transcript}”", style = Type.caption)
                            BasicText(state.reply, style = Type.body)
                        }
                        is AssistantState.Failed -> BasicText(state.message, style = Type.body)
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
        Palette.orb.forEachIndexed { i, color ->
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
