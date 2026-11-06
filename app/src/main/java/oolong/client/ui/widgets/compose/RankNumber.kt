package oolong.client.ui.widgets.compose

import android.graphics.Typeface
import androidx.compose.foundation.layout.width
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import oolong.client.ui.common.theme.compose.ExtendedTheme

/** A place on a ranking in the numeral face; the first three stand out by weight of colour alone. */
@Composable
fun RankNumber(rank: Int, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val family = remember { FontFamily(Typeface.createFromAsset(context.assets, "bebas.ttf")) }
    Text(
        text = "$rank",
        fontFamily = family,
        fontSize = 18.sp,
        textAlign = TextAlign.Center,
        color = if (rank <= 3) ExtendedTheme.colors.text else ExtendedTheme.colors.textDisabled,
        modifier = modifier.width(20.dp),
    )
}
