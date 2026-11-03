package oolong.client.ui.page.settings.tuning

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.collections.immutable.persistentListOf
import oolong.client.ui.common.theme.compose.ExtendedTheme
import oolong.client.ui.navigation.Navigator
import oolong.client.ui.page.LocalNavigator
import oolong.client.ui.widgets.compose.BackNavigationIcon
import oolong.client.ui.widgets.compose.Button
import oolong.client.ui.widgets.compose.GlassScaffold
import oolong.client.ui.widgets.compose.GlassTopBar
import oolong.client.ui.widgets.compose.GlassTopBarTitle
import oolong.client.ui.widgets.compose.MyLazyColumn
import oolong.client.ui.widgets.compose.SegmentedControl
import oolong.client.ui.widgets.compose.Slider
import oolong.client.ui.widgets.compose.TopBarEdgeTuning
import kotlin.math.roundToInt

/**
 * Temporary: sliders for the top bar's scroll edge, over colourful content to scroll under the
 * bar. The values apply to every top bar at once, so other pages can be checked too. Remove once
 * the values are settled.
 */
@Composable
fun TopBarTuningPage(
    navigator: Navigator = LocalNavigator.current,
) {
    GlassScaffold(
        backgroundColor = Color.Transparent,
        topBar = {
            GlassTopBar(
                title = { GlassTopBarTitle(text = "顶栏边缘") },
                navigationIcon = { BackNavigationIcon(onBackPressed = { navigator.navigateUp() }) },
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ExtendedTheme.colors.background)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                TuningSlider("模糊半径", "${TopBarEdgeTuning.blur.roundToInt()}dp", TopBarEdgeTuning.blur, 0f..60f) { TopBarEdgeTuning.blur = it }
                TuningSlider("底色浓度", "${(TopBarEdgeTuning.tint * 100).roundToInt()}%", TopBarEdgeTuning.tint, 0f..1f) { TopBarEdgeTuning.tint = it }
                TuningSlider("淡出长度", "${TopBarEdgeTuning.fade.roundToInt()}dp", TopBarEdgeTuning.fade, 8f..96f) { TopBarEdgeTuning.fade = it }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "曲线", fontSize = 14.sp, color = ExtendedTheme.colors.text, modifier = Modifier.width(72.dp))
                    // 淡出长度 shapes only the first two; the third eases over the whole bar
                    SegmentedControl(
                        options = CurveNames,
                        selected = TopBarEdgeTuning.curve,
                        onSelect = { TopBarEdgeTuning.curve = it },
                    )
                }
                Button(
                    onClick = {
                        TopBarEdgeTuning.blur = TopBarEdgeTuning.DefaultBlur
                        TopBarEdgeTuning.tint = TopBarEdgeTuning.DefaultTint
                        TopBarEdgeTuning.fade = TopBarEdgeTuning.DefaultFade
                        TopBarEdgeTuning.curve = TopBarEdgeTuning.DefaultCurve
                    },
                    modifier = Modifier.align(Alignment.End),
                ) { Text(text = "恢复默认") }
            }
        },
    ) { padding ->
        MyLazyColumn(contentPadding = padding) {
            items(Samples) { (hue, text) ->
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                    Text(text = text, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = ExtendedTheme.colors.text)
                    Box(
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .fillMaxWidth()
                            .height(180.dp)
                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                            .background(Brush.linearGradient(listOf(Color.hsv(hue, 0.7f, 0.95f), Color.hsv((hue + 60f) % 360f, 0.8f, 0.6f)))),
                    )
                }
            }
        }
    }
}

@Composable
private fun TuningSlider(label: String, value: String, current: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text = label, fontSize = 14.sp, color = ExtendedTheme.colors.text, modifier = Modifier.width(72.dp))
        Slider(value = current, onValueChange = onChange, valueRange = range, modifier = Modifier.weight(1f))
        Text(text = value, fontSize = 13.sp, color = ExtendedTheme.colors.textSecondary, modifier = Modifier.width(52.dp).padding(start = 8.dp))
    }
}

private val CurveNames = persistentListOf("原来", "缓入缓出", "整条渐变")

private val Samples = List(16) { i ->
    (i * 47f) % 360f to listOf("租房无中介费，押金平台先行垫付", "新版本地图太大了，跑图跑到手酸", "二手 4070 现在什么价位合适", "今天在江边拍的晚霞")[i % 4]
}
