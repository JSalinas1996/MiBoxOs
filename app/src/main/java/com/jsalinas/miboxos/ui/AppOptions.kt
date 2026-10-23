@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.jsalinas.miboxos.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import com.jsalinas.miboxos.apps.AppOrder
import com.jsalinas.miboxos.apps.TvApp

/**
 * Menú al mantener OK sobre un ícono, como en tvOS: Mover u Ocultar. "Atrás" lo cierra
 * (lo maneja MainActivity).
 */
@Composable
fun AppOptions(app: TvApp?, onMove: () -> Unit, onHide: () -> Unit) {
    val colors = LocalColors.current
    AnimatedVisibility(visible = app != null, enter = fadeIn(tween(150)) + scaleIn(tween(200), 0.92f), exit = fadeOut(tween(150))) {
        val current = app ?: return@AnimatedVisibility
        val first = remember { FocusRequester() }
        var hasFocus by remember { mutableStateOf(false) }
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)), contentAlignment = Alignment.Center) {
            Column(
                Modifier.testTag("appOptions").width(360.dp).background(colors.panel, RoundedCornerShape(28.dp))
                    .onFocusChanged { hasFocus = it.hasFocus }.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Image(current.tile, null, Modifier.size(150.dp, 84.dp).clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop, filterQuality = FilterQuality.High)
                BasicText(current.label, style = Type.title.copy(textAlign = TextAlign.Center), modifier = Modifier.padding(top = 6.dp, bottom = 6.dp))
                OptionRow("Mover", Modifier.focusRequester(first), onMove)
                if (AppOrder.canHide(current)) OptionRow("Ocultar", Modifier, onHide)
            }
        }
        LaunchedEffect(current.packageName) {
            withFrameNanos { }
            repeat(20) {
                runCatching { first.requestFocus() }
                withFrameNanos { }
                if (hasFocus) return@LaunchedEffect
            }
        }
    }
}

@Composable
private fun OptionRow(label: String, modifier: Modifier, onClick: () -> Unit) {
    val colors = LocalColors.current
    var focused by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(50.dp).onFocusChanged { focused = it.isFocused },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
        colors = ClickableSurfaceDefaults.colors(containerColor = colors.control, focusedContainerColor = colors.controlFocused),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.03f),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            BasicText(label, style = Type.row.copy(color = if (focused) colors.onControlFocused else colors.text))
        }
    }
}
