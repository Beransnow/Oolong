package oolong.client.ui.page.settings.about

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import oolong.client.BuildConfig
import oolong.client.R
import oolong.client.ui.common.theme.compose.Corners
import oolong.client.ui.common.theme.compose.ExtendedTheme
import oolong.client.ui.navigation.Navigator
import oolong.client.ui.page.LocalNavigator
import oolong.client.ui.widgets.compose.BackNavigationIcon
import oolong.client.ui.widgets.compose.GlassScaffold
import oolong.client.ui.widgets.compose.GlassTopBar
import oolong.client.ui.widgets.compose.GlassTopBarTitle
import oolong.client.ui.widgets.compose.SecondaryButton
import oolong.client.utils.launchUrl

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
                    SecondaryButton(
                        shape = Corners.full,
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
