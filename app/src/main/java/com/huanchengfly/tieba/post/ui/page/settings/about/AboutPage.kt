package com.huanchengfly.tieba.post.ui.page.settings.about

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.huanchengfly.tieba.post.BuildConfig
import com.huanchengfly.tieba.post.R
import com.huanchengfly.tieba.post.ui.common.theme.compose.ExtendedTheme
import com.huanchengfly.tieba.post.ui.navigation.Navigator
import com.huanchengfly.tieba.post.ui.page.LocalNavigator
import com.huanchengfly.tieba.post.ui.widgets.compose.BackNavigationIcon
import com.huanchengfly.tieba.post.ui.widgets.compose.GlassScaffold
import com.huanchengfly.tieba.post.ui.widgets.compose.GlassTopBar
import com.huanchengfly.tieba.post.ui.widgets.compose.GlassTopBarTitle
import com.huanchengfly.tieba.post.ui.widgets.compose.TextButton
import com.huanchengfly.tieba.post.utils.launchUrl
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

@Composable
fun AboutPage(
    navigator: Navigator = LocalNavigator.current,
) {
    GlassScaffold(
        backgroundColor = Color.Transparent,
        topBar = {
            GlassTopBar(
                title = { GlassTopBarTitle(text = stringResource(id = R.string.title_about)) },
                navigationIcon = {
                    BackNavigationIcon(onBackPressed = { navigator.navigateUp() })
                }
            )
        }
    ) { contentPaddings ->
        // The mark's lens can be lifted off it and looked through at the page
        val lens = remember { MarkLens() }
        val page = rememberHazeState()
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .hazeSource(page)
                    .padding(contentPaddings),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier.weight(1f)
                ) {
                    AboutMark(lens = lens, size = MarkSize, modifier = Modifier.align(Alignment.Center))
                }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth(0.5f)
                ) {
                    val context = LocalContext.current
                    TextButton(
                        shape = RoundedCornerShape(100),
                        onClick = {
                            launchUrl(context, navigator, "https://github.com/HuanCheng65/TiebaLite")
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(text = stringResource(id = R.string.source_code), modifier = Modifier.padding(vertical = 4.dp))
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(id = R.string.tip_about, BuildConfig.VERSION_NAME),
                        style = MaterialTheme.typography.caption,
                        color = ExtendedTheme.colors.textSecondary,
                    )
                }
                Spacer(modifier = Modifier.height(48.dp))
            }
            LensOverlay(lens = lens, page = page, markSize = MarkSize)
        }
    }
}

private val MarkSize = 100.dp
