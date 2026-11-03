package oolong.migration

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import oolong.client.ui.common.theme.compose.Motion
import oolong.client.ui.common.theme.compose.Radius
import oolong.client.ui.common.theme.compose.SmoothCornerShape
import oolong.client.ui.common.theme.compose.TextStyles

/**
 * Asked for by Oolong with startActivityForResult. It checks that the caller really is Oolong
 * (its package and signing certificate), asks the person to confirm what goes over, packs the
 * data and returns a one-time read grant to the file. Anyone else gets nothing and sees nothing.
 */
class ExportActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!packageManager.isOolong(callingActivity?.packageName)) {
            setResult(RESULT_CANCELED)
            finish()
            return
        }
        enableEdgeToEdge(navigationBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT))
        setContent {
            ConsentDialog(
                onResult = { code, data ->
                    setResult(code, data)
                    finish()
                    @Suppress("DEPRECATION")
                    overridePendingTransition(0, 0)
                },
            )
        }
    }
}

@Composable
private fun ConsentDialog(onResult: (Int, Intent?) -> Unit) {
    val colors = helperColors()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val visible = remember { MutableTransitionState(false).apply { targetState = true } }
    var result by remember { mutableStateOf<Pair<Int, Intent?>?>(null) }
    var exporting by remember { mutableStateOf(false) }
    fun close(code: Int, data: Intent?) {
        if (result != null) return
        result = code to data
        visible.targetState = false
    }
    // Leave once the dialog has gone
    LaunchedEffect(visible.currentState, visible.isIdle) {
        if (!visible.targetState && visible.isIdle) result?.let { (code, data) -> onResult(code, data) }
    }
    BackHandler { close(Activity.RESULT_CANCELED, null) }

    // As the app's dialogs: the scrim fades in quicker than it fades out, the panel rises from
    // two fifths of its height on the present spring and leaves on the dismiss one
    val scrim by animateFloatAsState(if (visible.targetState) 1f else 0f, tween(if (visible.targetState) 180 else 120), label = "scrim")
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(colors.scrim.copy(alpha = colors.scrim.alpha * scrim))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { close(Activity.RESULT_CANCELED, null) }
        )
        AnimatedVisibility(
            visibleState = visible,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = fadeIn(Motion.fadeIn()) + slideInVertically(Motion.present()) { it * 2 / 5 },
            exit = fadeOut(Motion.fadeOut()) + slideOutVertically(Motion.dismiss()) { it * 2 / 5 },
        ) {
            Column(
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 16.dp)
                    .widthIn(max = 560.dp)
                    .fillMaxWidth()
                    .dialogSurface(colors)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
            ) {
                Endpoints(Modifier.fillMaxWidth().padding(top = 28.dp, bottom = 18.dp))
                BasicText(
                    text = stringResource(R.string.consent_title),
                    modifier = Modifier.padding(horizontal = 22.dp),
                    style = TextStyle(color = colors.text, fontSize = 17.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold),
                )
                Column(
                    modifier = Modifier.padding(start = 22.dp, end = 22.dp, top = 18.dp, bottom = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Item(R.drawable.ic_account, R.string.consent_accounts, colors)
                    Item(R.drawable.ic_palette, R.string.consent_settings, colors)
                    Item(R.drawable.ic_history, R.string.consent_records, colors)
                }
                // Cancel on the left, confirm on the right, 14dp in: concentric with the corners
                Row(
                    modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 20.dp, bottom = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Capsule(stringResource(R.string.cancel), primary = false, colors = colors, modifier = Modifier.weight(1f), enabled = !exporting) {
                        close(Activity.RESULT_CANCELED, null)
                    }
                    Capsule(stringResource(if (exporting) R.string.exporting else R.string.export), primary = true, colors = colors, modifier = Modifier.weight(1f), enabled = !exporting) {
                        exporting = true
                        scope.launch {
                            val ok = withContext(Dispatchers.IO) { runCatching { Exporter.export(context) }.isSuccess }
                            if (ok) {
                                close(Activity.RESULT_OK, Intent().setData(ExportProvider.URI).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                            } else {
                                Toast.makeText(context, R.string.export_failed, Toast.LENGTH_LONG).show()
                                close(Activity.RESULT_CANCELED, null)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** The app's dialog panel (design-system.md §2.5): opaque, its smooth 36dp corners, its shadow and edge. */
private fun Modifier.dialogSurface(colors: HelperColors): Modifier {
    val shape = SmoothCornerShape(Radius.xl)
    return this
        .shadow(
            elevation = if (colors.night) 32.dp else 24.dp,
            shape = shape,
            ambientColor = Color.Black.copy(alpha = if (colors.night) .16f else .14f),
            spotColor = Color.Black.copy(alpha = if (colors.night) .26f else .22f),
        )
        .clip(shape)
        .background(colors.sheet)
        .border(.5.dp, colors.glassEdge, shape)
}

// This app's icon, a few dots, Oolong's icon: where the data comes from and goes to
@Composable
private fun Endpoints(modifier: Modifier) {
    val context = LocalContext.current
    val px = with(LocalDensity.current) { 52.dp.roundToPx() }
    val icons = remember {
        listOf(context.packageName, OOLONG_PACKAGE).map { pkg -> runCatching { context.packageManager.getApplicationIcon(pkg).toImageBitmap(px) }.getOrNull() }
    }
    Row(modifier, horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        icons[0]?.let { Image(it, null, Modifier.size(52.dp)) }
        BasicText("• • •", Modifier.padding(horizontal = 14.dp), style = TextStyles.caption.copy(color = helperColors().textSecondary.copy(alpha = .5f)))
        icons[1]?.let { Image(it, null, Modifier.size(52.dp)) }
    }
}

@Composable
private fun Item(icon: Int, label: Int, colors: HelperColors) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Image(painterResource(icon), null, Modifier.size(22.dp), colorFilter = ColorFilter.tint(colors.textSecondary))
        BasicText(stringResource(label), Modifier.padding(start = 14.dp), style = TextStyles.body.copy(color = colors.text, lineHeight = 22.sp))
    }
}
