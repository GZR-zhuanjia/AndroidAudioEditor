package com.audioassistant.ui.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.audioassistant.ui.components.AppGlyph
import com.audioassistant.ui.theme.AppSpacing

@Composable
fun AppBottomBar(current: Destination, enabled: Boolean, onSelect: (Destination) -> Unit) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Column {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Row(Modifier.fillMaxWidth().windowInsetsPadding(NavigationBarDefaults.windowInsets)) {
                Destination.entries.forEach { destination ->
                    val selected = current == destination
                    val indicator by animateColorAsState(
                        if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                        tween(AppMotion.IndicatorMs), label = "底栏指示背景")
                    val foreground by animateColorAsState(
                        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        tween(AppMotion.IndicatorMs), label = "底栏文字颜色")
                    Column(Modifier.weight(1f).selectable(selected, enabled = enabled, role = Role.Tab,
                        onClick = { onSelect(destination) }).heightIn(min = 64.dp).padding(vertical = AppSpacing.small),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(AppSpacing.xSmall)) {
                        // 只绘背景颜色，图标尺寸和基线完全固定；不套Material项的另一组位移动画。
                        Box(Modifier.size(width = 60.dp, height = 30.dp).drawBehind {
                            drawRoundRect(indicator, cornerRadius = CornerRadius(15.dp.toPx()))
                        }, contentAlignment = Alignment.Center) {
                            AppGlyph(destination.icon, modifier = Modifier.size(AppSpacing.icon), tint = foreground)
                        }
                        Text(destination.navLabel, style = MaterialTheme.typography.labelMedium, color = foreground)
                    }
                }
            }
        }
    }
}
