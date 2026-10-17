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
