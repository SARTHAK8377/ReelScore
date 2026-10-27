package com.reelscore.app.ui.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.reelscore.app.data.ReelRepository
import com.reelscore.app.data.ReelRepository.Period
import com.reelscore.app.detect.DetectorRules
import com.reelscore.app.ui.components.AppIcon
import com.reelscore.app.ui.components.SharedScopes
import com.reelscore.app.ui.components.sharedAppContainer
import com.reelscore.app.ui.components.sharedAppIcon

@Composable
fun AppDetailScreen(pkg: String, scopes: SharedScopes, onBack: () -> Unit) {
    val context = LocalContext.current
    val repo = remember { ReelRepository.get(context) }
    val label = DetectorRules.labelFor(pkg)
    val today by remember(pkg) { repo.totalForApp(pkg, Period.TODAY) }.collectAsStateWithLifecycle(0)
    val week by remember(pkg) { repo.totalForApp(pkg, Period.WEEK) }.collectAsStateWithLifecycle(0)
    val allTime by remember(pkg) { repo.totalForApp(pkg, Period.ALL_TIME) }.collectAsStateWithLifecycle(0)
    val top by remember(pkg) { repo.topCreators(pkg) }.collectAsStateWithLifecycle(emptyList())

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 8.dp),
    ) {
        // Shifted left so the arrow glyph, not its 48 dp touch target, lines up with the content edge.
        IconButton(onClick = onBack, modifier = Modifier.offset(x = (-12).dp)) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
        }

        // The header the tapped row morphs into.
        Column(
            Modifier
                .padding(top = 8.dp)
                .fillMaxWidth()
                .sharedAppContainer(scopes, pkg)
                .clip(MaterialTheme.shapes.extraLarge)
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .padding(20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                AppIcon(pkg, label, 64.dp, Modifier.sharedAppIcon(scopes, pkg))
                Text(label, style = MaterialTheme.typography.headlineLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Row(Modifier.fillMaxWidth().padding(top = 24.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf("Today" to today, "This week" to week, "All time" to allTime).forEach { (name, value) ->
                    Column(Modifier.weight(1f)) {
                        Text("$value", style = MaterialTheme.typography.displaySmall)
                        Text(name, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        Text(
            "Top channels",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(top = 36.dp, bottom = 8.dp),
        )
        if (top.isEmpty()) {
            Text(
                "No channels yet. They appear here as you watch reels in $label.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        top.forEachIndexed { index, creator ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "${index + 1}",
                    style = MaterialTheme.typography.displayMedium,
                    color = if (index == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(56.dp),
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        creator.creator,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        if (creator.count == 1) "1 reel" else "${creator.count} reels",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Text(
            "This list stays on your phone and is never shared.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 20.dp),
        )
    }
}
