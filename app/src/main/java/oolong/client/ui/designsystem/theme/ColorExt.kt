package oolong.client.ui.designsystem.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

fun Color.toHexString(): String {
    return "#${Integer.toHexString(toArgb()).substring(2)}"
}
