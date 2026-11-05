package oolong.client.ui.page.webview

import android.annotation.SuppressLint
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.ScaffoldState
import androidx.compose.material.Text
import androidx.compose.material.rememberScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.core.net.toUri
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import oolong.client.R
import oolong.client.ui.common.theme.compose.ExtendedTheme
import oolong.client.ui.common.theme.compose.Motion
import oolong.client.ui.common.theme.compose.Symbols
import oolong.client.ui.widgets.compose.AccompanistWebChromeClient
import oolong.client.ui.widgets.compose.AccompanistWebViewClient
import oolong.client.ui.widgets.compose.BackNavigationIcon
import oolong.client.ui.widgets.compose.ContextMenuItem
import oolong.client.ui.widgets.compose.GlassScaffold
import oolong.client.ui.widgets.compose.GlassTopBar
import oolong.client.ui.widgets.compose.LazyLoad
import oolong.client.ui.widgets.compose.LoadingState
import oolong.client.ui.widgets.compose.MenuIconButton
import oolong.client.ui.widgets.compose.WebView
import oolong.client.ui.widgets.compose.WebViewNavigator
import oolong.client.ui.widgets.compose.WebViewState
import oolong.client.utils.isInternalHost

/**
 * A page showing a web page: the bar with its title and address over the web view, which starts
 * below the bar, as the web view can't be drawn into the bar's glass.
 *
 * @param initialUrl loaded once, when the page first shows.
 * @param title shown in place of the page's own title, as on the login page.
 * @param menuItems the bar's menu; none leaves the menu out.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun WebPageScaffold(
    initialUrl: String,
    state: WebViewState,
    navigator: WebViewNavigator,
    client: AccompanistWebViewClient,
    chromeClient: AccompanistWebChromeClient,
    onClose: () -> Unit,
    menuItems: () -> List<ContextMenuItem>,
    title: String? = null,
    scaffoldState: ScaffoldState = rememberScaffoldState(),
) {
    var loaded by rememberSaveable { mutableStateOf(false) }
    // The last title the page gave, kept while the next page loads
    var pageTitle by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(state) {
        snapshotFlow { state.pageTitle }
            .filterNotNull()
            .filter { it.isNotEmpty() }
            .collect { pageTitle = it }
    }
    LazyLoad(loaded = loaded) {
        navigator.loadUrl(initialUrl)
        loaded = true
    }

    val currentHost by remember {
        derivedStateOf { state.lastLoadedUrl?.toUri()?.host.orEmpty().lowercase() }
    }
    val progress by remember {
        derivedStateOf { (state.loadingState as? LoadingState.Loading)?.progress }
    }
    val animatedProgress by animateFloatAsState(targetValue = progress ?: 0f, animationSpec = Motion.settle(), label = "progress")

    GlassScaffold(
        scaffoldState = scaffoldState,
        sampleContent = false,
        topBar = {
            GlassTopBar(
                title = {
                    Column {
                        Text(
                            text = title ?: pageTitle.ifEmpty { stringResource(id = R.string.title_default) },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (currentHost.isNotEmpty() && !isInternalHost(currentHost)) {
                            Text(
                                text = currentHost,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = ExtendedTheme.colors.topBarContentSecondary,
                                style = MaterialTheme.typography.caption
                            )
                        }
                    }
                },
                navigationIcon = { BackNavigationIcon(onBackPressed = onClose) },
                actions = {
                    MenuIconButton(
                        icon = Symbols.MoreVert,
                        contentDescription = stringResource(id = R.string.btn_more),
                        items = menuItems,
                    )
                },
            )
        }
    ) { paddingValues ->
        Box {
            WebView(
                state = state,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                navigator = navigator,
                onCreated = {
                    it.settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        setSupportZoom(true)
                        builtInZoomControls = true
                        displayZoomControls = false
                    }
                },
                client = client,
                chromeClient = chromeClient,
            )

            if (progress != null) {
                LinearProgressIndicator(
                    progress = animatedProgress,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
