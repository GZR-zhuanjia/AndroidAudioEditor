package com.audioassistant.ui.navigation

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut

/** 短、可打断的透明度过渡。图标几何位置不动，不做弹簧/整页缩放。 */
object AppMotion {
    const val EnterMs = 150
    const val ExitMs = 100
    const val IndicatorMs = 130
    fun enter() = fadeIn(tween(EnterMs, easing = FastOutSlowInEasing))
    fun exit() = fadeOut(tween(ExitMs, easing = FastOutSlowInEasing))
}
