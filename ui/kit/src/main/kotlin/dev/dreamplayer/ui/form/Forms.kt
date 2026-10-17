package dev.dreamplayer.ui.form

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import dev.dreamplayer.ui.theme.Dream

/** a section title inside a form / the settings sheet. */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        modifier = modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 6.dp),
        style = MaterialTheme.typography.labelMedium,
        color = Dream.colors.playing,
    )
}

/** a labelled text field ("label — value" of the TUI forms). */
@Composable
fun FormTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    supporting: String? = null,
    singleLine: Boolean = true,
    enabled: Boolean = true,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it) } },
        supportingText = supporting?.let { { Text(it) } },
        singleLine = singleLine,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
    )
}

/** an on / off option. */
@Composable
fun FormSwitch(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    enabled: Boolean = true,
) {
    Row(
        modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge, color = if (enabled) Color.Unspecified else Dream.colors.muted)
            if (description != null) {
                Text(description, style = MaterialTheme.typography.bodySmall, color = Dream.colors.muted)
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

/** one of several options (a TUI `choice` field). */
@Composable
fun <T> FormChoice(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    SingleChoiceSegmentedButtonRow(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        options.forEachIndexed { i, o ->
            SegmentedButton(
                selected = o == selected,
                onClick = { onSelect(o) },
                shape = SegmentedButtonDefaults.itemShape(i, options.size),
                label = { Text(label(o), maxLines = 1) },
            )
        }
    }
}

/** a read-only "label — value" line. */
@Composable
fun InfoLine(label: String, value: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = Dream.colors.muted, modifier = Modifier.weight(0.38f))
        Text(value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(0.62f))
    }
}

/** the search line above a track list (`/` of the TUI). */
@Composable
fun SearchBox(query: String, onQueryChange: (String) -> Unit, modifier: Modifier = Modifier, placeholder: String = "Поиск") {
    Column(modifier) {
        TextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = { Text(placeholder) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) { Icon(Icons.Filled.Close, contentDescription = "Очистить") }
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        HorizontalDivider()
    }
}
