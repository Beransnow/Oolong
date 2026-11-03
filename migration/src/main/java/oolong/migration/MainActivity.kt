package oolong.migration

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import oolong.client.ui.common.theme.compose.Spacing
import oolong.client.ui.common.theme.compose.TextStyles

/**
 * What opening the old app's icon now shows: that it is Oolong now, and a way there. The
 * import itself starts in Oolong, which asks this app for the data.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val hasOolong = packageManager.isOolong(OOLONG_PACKAGE)
        setContent {
            val colors = helperColors()
            val context = LocalContext.current
            val px = with(LocalDensity.current) { 72.dp.roundToPx() }
            val icon = remember { context.packageManager.getApplicationIcon(context.packageName).toImageBitmap(px) }
            Box(Modifier.fillMaxSize().background(colors.page)) {
                Column(
                    modifier = Modifier.align(Alignment.Center).padding(horizontal = Spacing.xl),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Image(icon, null, Modifier.size(72.dp))
                    BasicText(
                        stringResource(R.string.moved_title),
                        Modifier.padding(top = 18.dp),
                        style = TextStyles.title.copy(color = colors.text, textAlign = TextAlign.Center),
                    )
                    BasicText(
                        stringResource(R.string.moved_body),
                        Modifier.padding(top = 8.dp),
                        style = TextStyles.bodyCompact.copy(color = colors.textSecondary, textAlign = TextAlign.Center),
                    )
                }
                Capsule(
                    text = stringResource(if (hasOolong) R.string.open_oolong else R.string.get_oolong),
                    primary = true,
                    colors = colors,
                    modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(horizontal = Spacing.xl).padding(bottom = 32.dp).fillMaxWidth(),
                ) {
                    if (hasOolong) {
                        packageManager.getLaunchIntentForPackage(OOLONG_PACKAGE)?.let(::startActivity)
                    } else {
                        runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(GET_OOLONG_URL))) }
                    }
                }
            }
        }
    }

    companion object {
        // Where Oolong is published
        const val GET_OOLONG_URL = "https://t.me/tblite_discuss"
    }
}
