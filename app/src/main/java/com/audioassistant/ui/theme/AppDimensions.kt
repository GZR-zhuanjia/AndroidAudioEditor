package com.audioassistant.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

object AppSpacing {
    val xSmall = 4.dp
    val small = 8.dp
    val medium = 12.dp
    val large = 16.dp
    val page = 16.dp
    val section = 24.dp
    val touch = 48.dp
    val icon = 24.dp
    val featureIcon = 52.dp
}

val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp), small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp), large = RoundedCornerShape(20.dp), extraLarge = RoundedCornerShape(24.dp),
)

val AppSheetShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
