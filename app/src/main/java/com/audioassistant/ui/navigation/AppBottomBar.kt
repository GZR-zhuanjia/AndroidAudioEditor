package com.audioassistant.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.audioassistant.ui.components.AppGlyph
import com.audioassistant.ui.theme.AppSpacing

@Composable
fun AppBottomBar(current: Destination, enabled: Boolean, onSelect: (Destination) -> Unit) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Box(Modifier.windowInsetsPadding(NavigationBarDefaults.windowInsets)
            .padding(horizontal = AppSpacing.page, vertical = AppSpacing.small)) {
            Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainer) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    tonalElevation = 0.dp,
                    windowInsets = WindowInsets(0, 0, 0, 0),
                ) {
                    Destination.entries.forEach { destination ->
                        NavigationBarItem(
                            selected = current == destination,
                            enabled = enabled,
                            onClick = { onSelect(destination) },
                            icon = { AppGlyph(destination.icon, modifier = Modifier.size(AppSpacing.icon)) },
                            label = { Text(destination.navLabel) },
                            alwaysShowLabel = true,
                        )
                    }
                }
            }
        }
    }
}
