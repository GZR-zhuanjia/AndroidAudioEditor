package com.audioassistant.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import com.audioassistant.R

/** Official Material Symbols Rounded, bundled locally; see MATERIAL_SYMBOLS_NOTICE.md. */
enum class AppIcon(@DrawableRes val resourceId: Int) {
    HOME(R.drawable.ic_home),
    VIDEO(R.drawable.ic_video),
    CUT(R.drawable.ic_cut),
    LIBRARY(R.drawable.ic_library),
    AUDIO(R.drawable.ic_audio),
    ADD(R.drawable.ic_add),
    PHOTO(R.drawable.ic_photo),
    FOLDER(R.drawable.ic_folder),
    PLAY(R.drawable.ic_play),
    PAUSE(R.drawable.ic_pause),
    DRAG(R.drawable.ic_drag),
    DOWNLOAD(R.drawable.ic_download),
    USB(R.drawable.ic_usb),
    SHARE(R.drawable.ic_share),
    DELETE(R.drawable.ic_delete),
    EDIT(R.drawable.ic_edit),
    HISTORY(R.drawable.ic_history),
    TUNE(R.drawable.ic_tune),
    CHECK(R.drawable.ic_check),
    ERROR(R.drawable.ic_error),
    INFO(R.drawable.ic_info),
    WAVE(R.drawable.ic_wave),
    RIGHT(R.drawable.ic_right),
    BACK(R.drawable.ic_back),
    CLOSE(R.drawable.ic_close),
    MORE(R.drawable.ic_more),
    UP(R.drawable.ic_up),
    DOWN(R.drawable.ic_down),
    BACKWARD(R.drawable.ic_backward),
    FORWARD(R.drawable.ic_forward),
    START(R.drawable.ic_start),
    END(R.drawable.ic_end),
    UNDO(R.drawable.ic_undo),
    REDO(R.drawable.ic_redo),
    SPLIT(R.drawable.ic_split),
    MINUS(R.drawable.ic_minus),
    PLUS(R.drawable.ic_plus),
}

@Composable
fun AppGlyph(icon: AppIcon, description: String? = null, modifier: Modifier = Modifier, tint: Color = Color.Unspecified) {
    Icon(painter = painterResource(icon.resourceId), contentDescription = description, modifier = modifier,
        tint = if (tint == Color.Unspecified) LocalContentColor.current else tint)
}
