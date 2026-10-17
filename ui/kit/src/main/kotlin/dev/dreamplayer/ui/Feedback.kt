package dev.dreamplayer.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.dreamplayer.model.StatusKind
import dev.dreamplayer.model.StatusMessage
import dev.dreamplayer.ui.theme.Dream
import kotlinx.coroutines.delay
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.round
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sign

/**
 * the status banner (the TUI status line): shows the latest message, colored
 * by severity; errors stay longer. Tap to hide.
 */
@Composable
fun StatusBanner(message: StatusMessage?, modifier: Modifier = Modifier) {
    var shown by remember { mutableStateOf<StatusMessage?>(null) }
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(message) {
        if (message == null) return@LaunchedEffect
        shown = message
        visible = true
        delay(if (message.kind == StatusKind.Error) 6000 else 3000)
        visible = false
    }
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut(),
        modifier = modifier,
    ) {
        val m = shown ?: return@AnimatedVisibility
        val color = Dream.colors.of(m.kind)
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .border(1.dp, color.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                .clickable { visible = false },
        ) {
            Text(m.text, color = color, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(12.dp))
        }
    }
}

/**
 * the top banner for warnings and errors. Unlike [StatusBanner] it doesn't
 * time out: it stays until swiped away (sideways or up). Shows the newest of
 * [alerts]; "ещё N" — older ones, revealed as the newer are dismissed. Tap —
 * [onOpen] (the log with the details).
 */
@Composable
fun AlertBanner(
    alerts: List<StatusMessage>,
    onDismiss: (StatusMessage) -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // the last one stays drawn while the banner slides away.
    var shown by remember { mutableStateOf<StatusMessage?>(null) }
    alerts.lastOrNull()?.let { shown = it }
    AnimatedVisibility(
        visible = alerts.isNotEmpty(),
        enter = slideInVertically { -it } + fadeIn(),
        exit = fadeOut(),
        modifier = modifier,
    ) {
        val m = shown ?: return@AnimatedVisibility
        key(m.stamp) { SwipeableAlert(m, more = alerts.size - 1, onDismiss = { onDismiss(m) }, onOpen = onOpen) }
    }
}

@Composable
private fun SwipeableAlert(m: StatusMessage, more: Int, onDismiss: () -> Unit, onOpen: () -> Unit) {
    val color = Dream.colors.of(m.kind)
    val offset = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    val scope = rememberCoroutineScope()
    var size by remember { mutableStateOf(IntSize(1, 1)) }
    // fades as it is dragged towards dismissal.
    val progress = maxOf(abs(offset.value.x) / size.width, -offset.value.y / size.height).coerceIn(0f, 1f)
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shape = RoundedCornerShape(10.dp),
        shadowElevation = 6.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .onSizeChanged { size = it }
            .offset { offset.value.round() }
            .graphicsLayer { alpha = 1f - progress * 0.8f }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragEnd = {
                        scope.launch {
                            val o = offset.value
                            when {
                                abs(o.x) > size.width * SWIPE_FRACTION -> {
                                    offset.animateTo(Offset(sign(o.x) * size.width * 1.2f, o.y))
                                    onDismiss()
                                }
                                -o.y > size.height * SWIPE_FRACTION -> {
                                    offset.animateTo(Offset(o.x, -size.height * 1.5f))
                                    onDismiss()
                                }
                                else -> offset.animateTo(Offset.Zero)
                            }
                        }
                    },
                    onDragCancel = { scope.launch { offset.animateTo(Offset.Zero) } },
                ) { change, drag ->
                    change.consume()
                    // sideways freely, vertically only up.
                    val next = offset.value + drag
                    scope.launch { offset.snapTo(Offset(next.x, next.y.coerceAtMost(0f))) }
                }
            }
            .border(1.dp, color.copy(alpha = 0.7f), RoundedCornerShape(10.dp))
            .clickable(onClick = onOpen),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (m.kind == StatusKind.Error) Icons.Filled.Error else Icons.Filled.Warning,
                contentDescription = null,
                tint = color,
            )
            Text(
                m.text,
                color = color,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
            )
            if (more > 0) {
                Text("ещё $more", color = Dream.colors.muted, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

/** how far (of its size) the alert is dragged to be dismissed. */
private const val SWIPE_FRACTION = 0.3f

/** confirmation of a destructive action (the red y/N prompt of the TUI). */
@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = { onDismiss(); onConfirm() }) { Text(confirmLabel, color = Dream.colors.error) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

/** an operation log (import / export): colored lines, newest at the bottom. */
@Composable
fun MessageLog(messages: List<StatusMessage>, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        for (m in messages) {
            Text(
                "• ${m.text}",
                color = Dream.colors.of(m.kind),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(vertical = 2.dp),
            )
        }
    }
}

/** asks for a 1-based position in a list of [size] items (moving by number in the TUI). */
@Composable
fun PositionDialog(size: Int, onConfirm: (index: Int) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    val target = dev.dreamplayer.common.targetIndex(text, size)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Переместить на позицию") },
        text = {
            androidx.compose.material3.OutlinedTextField(
                value = text,
                onValueChange = { v -> text = v.filter(Char::isDigit).take(6) },
                label = { Text("1 – $size") },
                singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
                ),
            )
        },
        confirmButton = {
            TextButton(enabled = target != null, onClick = { onDismiss(); target?.let(onConfirm) }) { Text("Переместить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}
