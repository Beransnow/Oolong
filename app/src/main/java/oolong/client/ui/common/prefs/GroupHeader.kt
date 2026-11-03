package oolong.client.ui.common.prefs

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import oolong.client.ui.common.theme.compose.ExtendedTheme

/** A group's title: small grey text over its settings, lined up with their text. */
@Composable
fun GroupHeader(
    title: String,
    // Grey, like the app's other labels: the accent is kept for states such as switches
    color: Color = ExtendedTheme.colors.textSecondary
) {
    Text(
        title,
        color = color,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 32.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
    )
}

/** A note under a group of settings, lined up with their text. */
@Composable
fun PrefsFooter(text: String) {
    Text(
        text,
        color = ExtendedTheme.colors.textSecondary,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 8.dp),
    )
}
