package com.audioassistant.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.audioassistant.ui.theme.AppSpacing

enum class FeatureTone { PURPLE, BLUE, NEUTRAL }

@Composable
fun IconBadge(icon: AppIcon, tone: FeatureTone = FeatureTone.PURPLE, size: Dp = AppSpacing.featureIcon) {
    val colors = MaterialTheme.colorScheme
    val background = when (tone) {
        FeatureTone.PURPLE -> colors.primaryContainer
        FeatureTone.BLUE -> colors.secondaryContainer
        FeatureTone.NEUTRAL -> colors.surfaceContainerHigh
    }
    val foreground = when (tone) {
        FeatureTone.PURPLE -> colors.primary
        FeatureTone.BLUE -> colors.secondary
        FeatureTone.NEUTRAL -> colors.onSurfaceVariant
    }
    Surface(shape = MaterialTheme.shapes.medium, color = background, contentColor = foreground) {
        Box(Modifier.size(size), contentAlignment = Alignment.Center) {
            AppGlyph(icon, modifier = Modifier.size(if (size >= 52.dp) 28.dp else 22.dp))
        }
    }
}

@Composable
fun FeatureCard(title: String, description: String, icon: AppIcon, tone: FeatureTone, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))) {
        Row(Modifier.padding(AppSpacing.large), horizontalArrangement = Arrangement.spacedBy(AppSpacing.large), verticalAlignment = Alignment.CenterVertically) {
            IconBadge(icon, tone)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AppSpacing.xSmall)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            AppGlyph(AppIcon.RIGHT, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun SectionHeader(title: String, supporting: String? = null, icon: AppIcon? = null) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AppSpacing.small), verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) AppGlyph(icon, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        if (supporting != null) Text(supporting, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun EmptyState(
    title: String, description: String, modifier: Modifier = Modifier, icon: AppIcon = AppIcon.AUDIO,
    tone: FeatureTone = FeatureTone.PURPLE, actions: (@Composable ColumnScope.() -> Unit)? = null,
) {
    Column(modifier.fillMaxWidth().padding(vertical = AppSpacing.section),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(AppSpacing.medium)) {
        IconBadge(icon, tone, 64.dp)
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Text(description, modifier = Modifier.widthIn(max = 420.dp), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        if (actions != null) Column(Modifier.widthIn(max = 360.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.medium), content = actions)
    }
}

@Composable
fun PrimaryActionButton(text: String, icon: AppIcon, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(onClick, modifier.heightIn(min = AppSpacing.touch), enabled = enabled, shape = MaterialTheme.shapes.medium,
        contentPadding = PaddingValues(horizontal = AppSpacing.large, vertical = AppSpacing.medium),
        colors = ButtonDefaults.buttonColors(
            disabledContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        )) {
        AppGlyph(icon, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(AppSpacing.small))
        Text(text, textAlign = TextAlign.Center)
    }
}

@Composable
fun SecondaryActionButton(text: String, icon: AppIcon, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    OutlinedButton(onClick, modifier.heightIn(min = AppSpacing.touch), enabled = enabled,
        shape = MaterialTheme.shapes.medium, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        contentPadding = PaddingValues(horizontal = AppSpacing.medium, vertical = AppSpacing.medium),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)) {
        AppGlyph(icon, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(AppSpacing.small))
        Text(text, textAlign = TextAlign.Center)
    }
}

@Composable
fun TextAction(text: String, icon: AppIcon, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    TextButton(onClick, modifier.heightIn(min = AppSpacing.touch), enabled = enabled) {
        AppGlyph(icon, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
    }
}

@Composable
fun ActionDock(content: @Composable ColumnScope.() -> Unit) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Column {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Column(Modifier.fillMaxWidth().padding(horizontal = AppSpacing.page, vertical = AppSpacing.medium),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.small), content = content)
        }
    }
}

@Composable
fun NoteRow(text: String, icon: AppIcon = AppIcon.INFO) {
    val color = if (icon == AppIcon.ERROR) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
    Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.small), verticalAlignment = Alignment.CenterVertically) {
        AppGlyph(icon, modifier = Modifier.size(16.dp), tint = color)
        Text(text, style = MaterialTheme.typography.bodySmall, color = color)
    }
}

@Composable
fun SheetHeader(title: String, icon: AppIcon, onClose: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = AppSpacing.page, vertical = AppSpacing.small),
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.medium), verticalAlignment = Alignment.CenterVertically) {
        IconBadge(icon, size = 40.dp)
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        IconButton(onClick = onClose) { AppGlyph(AppIcon.CLOSE, "关闭面板") }
    }
}

/** 窄屏/大字时纵排，保持文字完整和触摸目标；只用于并列操作，不撑满剩余高度。 */
@Composable
fun ActionPair(
    first: @Composable (Modifier) -> Unit, second: @Composable (Modifier) -> Unit,
    stackAt: Dp = 320.dp, stackForLargeText: Boolean = true,
) {
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth < stackAt || (stackForLargeText && fontScale > 1.3f)) {
            Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
                first(Modifier.fillMaxWidth())
                second(Modifier.fillMaxWidth())
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
                first(Modifier.weight(1f))
                second(Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun HomeAppBar(onSettings: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = AppSpacing.page, vertical = AppSpacing.small),
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.medium), verticalAlignment = Alignment.CenterVertically) {
            IconBadge(AppIcon.WAVE, size = 44.dp)
            Column(Modifier.weight(1f)) {
                Text("音频助手", style = MaterialTheme.typography.headlineSmall)
                Text("让音频处理更简单", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onSettings) { AppGlyph(AppIcon.TUNE, "设置（暂未开放）") }
        }
    }
}
