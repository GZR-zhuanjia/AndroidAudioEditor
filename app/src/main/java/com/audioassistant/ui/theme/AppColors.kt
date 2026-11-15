package com.audioassistant.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

object AppColors {
    val Dark = darkColorScheme(
        primary = Color(0xFFB3CCF5), onPrimary = Color(0xFF142E50),
        primaryContainer = Color(0xFF293A51), onPrimaryContainer = Color(0xFFD8E6FF),
        secondary = Color(0xFFC0C7D2), onSecondary = Color(0xFF28313C),
        secondaryContainer = Color(0xFF303943), onSecondaryContainer = Color(0xFFE0E6EE),
        tertiary = Color(0xFFC8BCDE), onTertiary = Color(0xFF332B44),
        tertiaryContainer = Color(0xFF342F40), onTertiaryContainer = Color(0xFFEBE0FA),
        background = Color.Black, onBackground = Color(0xFFF3F3F3),
        surface = Color(0xFF111214), onSurface = Color(0xFFF3F3F3),
        surfaceVariant = Color(0xFF303236), onSurfaceVariant = Color(0xFFB7B9BE),
        surfaceContainerLowest = Color.Black, surfaceContainerLow = Color(0xFF191A1C),
        surfaceContainer = Color(0xFF202124), surfaceContainerHigh = Color(0xFF292B2E),
        surfaceContainerHighest = Color(0xFF34363A),
        outline = Color(0xFF85888E), outlineVariant = Color(0xFF393B3F),
        error = Color(0xFFFFB4AB), errorContainer = Color(0xFF51302F), onErrorContainer = Color(0xFFFFDAD6),
    )
    val Light = lightColorScheme(
        primary = Color(0xFF365E93), onPrimary = Color.White,
        primaryContainer = Color(0xFFDCE8FA), onPrimaryContainer = Color(0xFF17395F),
        secondary = Color(0xFF536171), onSecondary = Color.White,
        secondaryContainer = Color(0xFFE0E6ED), onSecondaryContainer = Color(0xFF293542),
        tertiary = Color(0xFF6B5E7C), onTertiary = Color.White,
        tertiaryContainer = Color(0xFFEDE5F5), onTertiaryContainer = Color(0xFF40324F),
        background = Color(0xFFF5F6F7), onBackground = Color(0xFF191B1F),
        surface = Color.White, onSurface = Color(0xFF191B1F),
        surfaceVariant = Color(0xFFE4E6E9), onSurfaceVariant = Color(0xFF5C6067),
        surfaceContainerLowest = Color.White, surfaceContainerLow = Color.White,
        surfaceContainer = Color(0xFFEEEFF1), surfaceContainerHigh = Color(0xFFE7E9EC),
        surfaceContainerHighest = Color(0xFFDEE1E5),
        outline = Color(0xFF777B82), outlineVariant = Color(0xFFD5D8DD),
        error = Color(0xFFAC3434), errorContainer = Color(0xFFFFE6E2), onErrorContainer = Color(0xFF742020),
    )
}
