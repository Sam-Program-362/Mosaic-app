package dev.mosaic.app.feature.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.mosaic.app.data.preferences.ThemeMode
import dev.mosaic.app.ui.theme.LocalMosaicColors

@Composable
fun SettingsScreen(
    theme: ThemeMode,
    folderCount: Int,
    communityCount: Int,
    savedCount: Int,
    onThemeSelected: (ThemeMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalMosaicColors.current
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionLabel("Appearance")
            Row(
                Modifier.widthIn(max = 560.dp).fillMaxWidth()
                    .background(colors.surface, RoundedCornerShape(14.dp))
                    .border(1.dp, colors.divider, RoundedCornerShape(14.dp))
                    .padding(5.dp)
                    .selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                ThemeChoice(
                    mode = ThemeMode.Light,
                    selected = theme == ThemeMode.Light,
                    onClick = { onThemeSelected(ThemeMode.Light) },
                    modifier = Modifier.weight(1f),
                )
                ThemeChoice(
                    mode = ThemeMode.Dark,
                    selected = theme == ThemeMode.Dark,
                    onClick = { onThemeSelected(ThemeMode.Dark) },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionLabel("Your library")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LibraryStat("Folders", folderCount, Modifier.weight(1f))
                LibraryStat("Communities", communityCount, Modifier.weight(1f))
                LibraryStat("Saved", savedCount, Modifier.weight(1f))
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionLabel("About Mosaic")
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = colors.surface,
                border = BorderStroke(1.dp, colors.divider),
            ) {
                Column(
                    Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        "Mosaic turns photos, GIFs, and MP4 videos in folders you choose into a private, Reddit-style feed — entirely on this device.",
                        color = colors.textSecondary,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        "No accounts, servers, uploads, or network requests for browsing, saving, or viewing. Only the folders you select are indexed.",
                        color = colors.textSecondary,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        "The arrows and counts on posts are decorative only. They are not votes and never change your files or library.",
                        color = colors.textSecondary,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun ThemeChoice(
    mode: ThemeMode,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalMosaicColors.current
    val label = if (mode == ThemeMode.Light) "Light" else "Dark"
    Surface(
        modifier = modifier.selectable(
            selected = selected,
            role = Role.RadioButton,
            onClick = onClick,
        ).semantics(mergeDescendants = true) {},
        shape = RoundedCornerShape(10.dp),
        color = if (selected) colors.accent else colors.surface,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 10.dp, horizontal = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (mode == ThemeMode.Light) Icons.Filled.WbSunny else Icons.Filled.NightsStay,
                contentDescription = null,
                tint = if (selected) Color(0xFF1A1A1B) else colors.textSecondary,
                modifier = Modifier.size(17.dp),
            )
            Text(
                label,
                Modifier.padding(start = 7.dp),
                color = if (selected) Color(0xFF1A1A1B) else colors.textSecondary,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

@Composable
private fun LibraryStat(label: String, value: Int, modifier: Modifier = Modifier) {
    val colors = LocalMosaicColors.current
    Surface(
        modifier = modifier.semantics(mergeDescendants = true) {
            contentDescription = "$label: $value"
        },
        shape = RoundedCornerShape(12.dp),
        color = colors.surface,
        border = BorderStroke(1.dp, colors.divider),
    ) {
        Column(
            Modifier.padding(vertical = 12.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(value.toString(), color = colors.textPrimary, style = MaterialTheme.typography.titleLarge)
            Text(label.uppercase(), color = colors.textSecondary, style = MaterialTheme.typography.labelSmall, maxLines = 1)
        }
    }
}

@Composable
private fun SectionLabel(label: String) {
    val colors = LocalMosaicColors.current
    Text(
        label.uppercase(),
        color = colors.textSecondary,
        style = MaterialTheme.typography.labelMedium,
    )
}
