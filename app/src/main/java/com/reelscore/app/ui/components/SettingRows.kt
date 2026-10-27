package com.reelscore.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.reelscore.app.ui.theme.groupShape

/** Rows for settings-style lists: grouped surfaces, 64 dp minimum height, whole-row touch targets. */

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        modifier = modifier.padding(top = 32.dp, bottom = 12.dp, start = 4.dp),
    )
}

@Composable
fun RowGap() = Spacer(Modifier.height(2.dp))

@Composable
private fun RowBase(index: Int, count: Int, interaction: Modifier, content: @Composable RowScope.() -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clip(groupShape(index, count))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .then(interaction)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) { content() }
}

@Composable
private fun RowLabel(title: String, subtitle: String?, color: Color, modifier: Modifier) {
    Column(modifier) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = color)
        if (subtitle != null) {
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun ToggleRow(
    icon: ImageVector?,
    title: String,
    checked: Boolean,
    index: Int,
    count: Int,
    onChange: (Boolean) -> Unit,
) {
    RowBase(index, count, Modifier.toggleable(value = checked, role = Role.Switch, onValueChange = onChange)) {
        if (icon != null) Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        RowLabel(title, null, MaterialTheme.colorScheme.onSurface, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = null)
    }
}

/** A row that opens something. Shows [value] on the right if given, otherwise a chevron. */
@Composable
fun NavRow(
    icon: ImageVector?,
    title: String,
    index: Int,
    count: Int,
    subtitle: String? = null,
    value: String? = null,
    destructive: Boolean = false,
    onClick: () -> Unit,
) {
    val color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    RowBase(index, count, Modifier.clickable(role = Role.Button, onClick = onClick)) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = if (destructive) color else MaterialTheme.colorScheme.onSurfaceVariant)
        }
        RowLabel(title, subtitle, color, Modifier.weight(1f))
        if (value != null) {
            Text(value, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        } else {
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
