package com.reelscore.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.reelscore.app.ui.theme.groupShape

/**
 * One app in a grouped list: real icon, name, a supporting line or share bar, and a count.
 * [share] draws a lime bar (no track) sized to this app's share of the biggest count.
 */
@Composable
fun AppRow(
    pkg: String,
    label: String,
    count: Int,
    index: Int,
    rowCount: Int,
    scopes: SharedScopes,
    onClick: () -> Unit,
    supporting: String? = null,
    countCaption: String? = null,
    share: Float? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .sharedAppContainer(scopes, pkg)
            .clip(groupShape(index, rowCount))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .clickable(onClickLabel = "Open $label", onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        AppIcon(pkg, label, 44.dp, Modifier.sharedAppIcon(scopes, pkg))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(label, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (supporting != null) {
                Text(supporting, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (share != null) {
                Box(
                    Modifier
                        .fillMaxWidth(share.coerceIn(0.02f, 1f))
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                )
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text("$count", style = MaterialTheme.typography.headlineSmall)
            if (countCaption != null) {
                Text(countCaption, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
