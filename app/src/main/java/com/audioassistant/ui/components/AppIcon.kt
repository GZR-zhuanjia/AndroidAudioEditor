package com.audioassistant.ui.components

import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/** 与原本地XML同一套路径，ImageVector只构建一次，切页不再触发资源XML解析。 */
enum class AppIcon(private val path: String) {
    HOME("M3,10 L12,3 L21,10 V21 H15 V14 H9 V21 H3 Z"),
    VIDEO("M4,6 H14 Q16,6 16,8 V16 Q16,18 14,18 H4 Q2,18 2,16 V8 Q2,6 4,6 Z M16,10 L22,7 V17 L16,14"),
    CUT("M8,8 L20,20 M8,16 L20,4 M11,11 L14,8 M3,6 A3,3 0,1 0,9,6 A3,3 0,1 0,3,6 M3,18 A3,3 0,1 0,9,18 A3,3 0,1 0,3,18"),
    LIBRARY("M3,5 V21 H19 M7,2 H22 V17 H7 Z M13,7 V13 M13,8 L18,7 V12 M10,13 A1.5,1.5 0,1 0,13,13 A1.5,1.5 0,1 0,10,13 M15,12 A1.5,1.5 0,1 0,18,12 A1.5,1.5 0,1 0,15,12"),
    AUDIO("M5,2 H14 L20,8 V22 H5 Z M14,2 V8 H20 M9,14 V18 M12,11 V19 M15,13 V17"),
    ADD("M12,3 A9,9 0,1 0,12,21 A9,9 0,1 0,12,3 M8,12 H16 M12,8 V16"),
    PHOTO("M3,4 H21 V20 H3 Z M3,16 L8,11 L13,16 L16,13 L21,18 M14,8 A1,1 0,1 0,16,8 A1,1 0,1 0,14,8"),
    FOLDER("M2,7 V4 H9 L11,6 H21 V9 M2,9 H22 L19,20 H2 Z"),
    PLAY("M8,4 L20,12 L8,20 Z"),
    PAUSE("M8,5 V19 M16,5 V19"),
    DRAG("M5,9 H19 M5,15 H19"),
    DOWNLOAD("M12,3 V15 M7,10 L12,15 L17,10 M4,16 V21 H20 V16"),
    USB("M12,21 V4 M9,7 L12,3 L15,7 M12,16 L6,12 V9 M12,12 L18,9 V6 M4,7 H8 V10 H4 Z M16,3 H20 V6 H16 Z"),
    SHARE("M7,12 L17,6 M7,12 L17,18 M3,12 A2,2 0,1 0,7,12 A2,2 0,1 0,3,12 M17,5 A2,2 0,1 0,21,5 A2,2 0,1 0,17,5 M17,19 A2,2 0,1 0,21,19 A2,2 0,1 0,17,19"),
    DELETE("M3,6 H21 M9,6 V3 H15 V6 M6,6 L7,21 H17 L18,6 M10,10 V17 M14,10 V17"),
    EDIT("M4,16 L16,4 L20,8 L8,20 L3,21 Z M13,7 L17,11"),
    HISTORY("M3,11 A9,9 0,1 1,5,18 M3,4 V11 H10 M12,7 V12 L16,14"),
    TUNE("M3,6 H7 M11,6 H21 M3,12 H13 M17,12 H21 M3,18 H5 M9,18 H21 M7,3 V9 H11 V3 Z M13,9 V15 H17 V9 Z M5,15 V21 H9 V15 Z"),
    CHECK("M12,3 A9,9 0,1 0,12,21 A9,9 0,1 0,12,3 M7,12 L10,15 L17,8"),
    ERROR("M12,3 A9,9 0,1 0,12,21 A9,9 0,1 0,12,3 M12,7 V12 M12,16 V16.2"),
    INFO("M12,3 A9,9 0,1 0,12,21 A9,9 0,1 0,12,3 M12,11 V17 M12,7 V7.2"),
    WAVE("M3,10 V14 M7,6 V18 M12,3 V21 M17,7 V17 M21,10 V14"),
    RIGHT("M9,5 L16,12 L9,19"),
    BACK("M20,12 H4 M10,5 L3,12 L10,19"),
    CLOSE("M5,5 L19,19 M19,5 L5,19"),
    MORE("M5,11.8 V12.2 M12,11.8 V12.2 M19,11.8 V12.2"),
    UP("M5,15 L12,8 L19,15"),
    DOWN("M5,9 L12,16 L19,9"),
    BACKWARD("M11,5 L3,12 L11,19 Z M21,5 L13,12 L21,19 Z"),
    FORWARD("M3,5 L11,12 L3,19 Z M13,5 L21,12 L13,19 Z"),
    START("M5,21 V3 H18 L14,8 L18,13 H5"),
    END("M19,21 V3 H6 L10,8 L6,13 H19"),
    UNDO("M8,4 L3,9 L8,14 M3,9 H14 A6,6 0,0 1,14,21"),
    REDO("M16,4 L21,9 L16,14 M21,9 H10 A6,6 0,0 0,10,21"),
    SPLIT("M12,3 V7 M12,11 V13 M12,17 V21 M3,12 H8 M16,12 H21"),
    MINUS("M5,12 H19"),
    PLUS("M5,12 H19 M12,5 V19"),

    ;
    val vector: ImageVector by lazy {
        ImageVector.Builder(name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
            .addPath(pathData = PathParser().parsePathString(path).toNodes(), fill = SolidColor(Color.Transparent),
                stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round)
            .build()
    }
}

@Composable
fun AppGlyph(icon: AppIcon, description: String? = null, modifier: Modifier = Modifier, tint: Color = Color.Unspecified) {
    Icon(icon.vector, description, modifier, tint = if (tint == Color.Unspecified) LocalContentColor.current else tint)
}
