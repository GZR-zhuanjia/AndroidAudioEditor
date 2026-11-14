package com.audioassistant.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

object AppColors {
    val Dark = darkColorScheme(
        primary = Color(0xFFB69AF4), onPrimary = Color(0xFF28174B),
        primaryContainer = Color(0xFF36294D), onPrimaryContainer = Color(0xFFEADFFF),
        secondary = Color(0xFF82AAFA), onSecondary = Color(0xFF122C54),
        secondaryContainer = Color(0xFF25344D), onSecondaryContainer = Color(0xFFD6E3FF),
        background = Color(0xFF121116), onBackground = Color(0xFFF2F0F5),
        surface = Color(0xFF19181E), onSurface = Color(0xFFF2F0F5),
        surfaceVariant = Color(0xFF302E38), onSurfaceVariant = Color(0xFFB7B2C0),
        surfaceContainerLowest = Color(0xFF121116), surfaceContainerLow = Color(0xFF1C1B21),
        surfaceContainer = Color(0xFF242328), surfaceContainerHigh = Color(0xFF2C2A32),
        surfaceContainerHighest = Color(0xFF34313C),
        outline = Color(0xFF797381), outlineVariant = Color(0xFF3B3744),
        error = Color(0xFFFFB4AB), errorContainer = Color(0xFF51302F), onErrorContainer = Color(0xFFFFDAD6),
    )
    val Light = lightColorScheme(
        primary = Color(0xFF6C49A8), onPrimary = Color.White,
        primaryContainer = Color(0xFFECE1FF), onPrimaryContainer = Color(0xFF342052),
        secondary = Color(0xFF3E65AA), onSecondary = Color.White,
        secondaryContainer = Color(0xFFE1EBFF), onSecondaryContainer = Color(0xFF1B365D),
        background = Color(0xFFF8F7FC), onBackground = Color(0xFF24212B),
        surface = Color(0xFFFDFCFF), onSurface = Color(0xFF24212B),
        surfaceVariant = Color(0xFFE9E5EF), onSurfaceVariant = Color(0xFF68616F),
        surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF4F1F8),
        surfaceContainer = Color(0xFFF0EDF5), surfaceContainerHigh = Color(0xFFEAE6F0),
        surfaceContainerHighest = Color(0xFFE3DEEB),
        outline = Color(0xFF827A8B), outlineVariant = Color(0xFFDCD5E4),
        error = Color(0xFFAC3434), errorContainer = Color(0xFFFFE6E2), onErrorContainer = Color(0xFF742020),
    )
}
