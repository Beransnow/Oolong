package oolong.client.ui.page.webview

import android.annotation.SuppressLint
import android.view.ContextThemeWrapper
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.ScaffoldState
import androidx.compose.material.Text
import androidx.compose.material.rememberScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import oolong.client.R
import oolong.client.ui.common.theme.compose.ExtendedTheme
import oolong.client.ui.common.theme.compose.Motion
import oolong.client.ui.common.theme.compose.SmoothCornerShape
import oolong.client.ui.common.theme.compose.Symbols
import oolong.client.ui.common.theme.compose.TextStyles
import oolong.client.ui.widgets.compose.AccompanistWebChromeClient
import oolong.client.ui.widgets.compose.AccompanistWebViewClient
import oolong.client.ui.widgets.compose.ContextMenuItem
import oolong.client.ui.widgets.compose.GlassScaffold
import oolong.client.ui.widgets.compose.GlassTopBar
import oolong.client.ui.widgets.compose.GlassTopBarItemHeight
import oolong.client.ui.widgets.compose.IconButton
import oolong.client.ui.widgets.compose.LazyLoad
import oolong.client.ui.widgets.compose.LoadingState
import oolong.client.ui.widgets.compose.LocalContextMenus
import oolong.client.ui.widgets.compose.MenuIconButton
import oolong.client.ui.widgets.compose.WebView
import oolong.client.ui.widgets.compose.WebViewNavigator
import oolong.client.ui.widgets.compose.WebViewState
import oolong.client.ui.widgets.compose.floatingGlass

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

    // Before the first page starts, the one it is going to
    val currentUrl by remember { derivedStateOf { state.lastLoadedUrl ?: initialUrl } }
    val fill = rememberLoadProgress(state)
    val menus = LocalContextMenus.current

    GlassScaffold(
        scaffoldState = scaffoldState,
        sampleContent = false,
        topBar = {
            GlassTopBar(
                title = {
                    var bounds by remember { mutableStateOf(Rect.Zero) }
                    AddressCapsule(
                        title = title ?: pageTitle,
                        url = currentUrl,
                        fill = fill,
                        modifier = Modifier.onGloballyPositioned { bounds = it.boundsInWindow() },
                        onClick = {
                            val items = menuItems()
                            if (menus != null && items.isNotEmpty()) menus.show(items, anchor = bounds)
                        },
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(
                            imageVector = Symbols.Close,
                            contentDescription = stringResource(id = R.string.btn_close),
                            tint = ExtendedTheme.colors.topBarContent,
                        )
                    }
                },
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
        val colors = ExtendedTheme.colors
        // Until the page first draws, the web view shows the page's background, not white
        val background = colors.background.toArgb()
        LaunchedEffect(state.webView, background) { state.webView?.setBackgroundColor(background) }
        val night = colors.isNightMode
        WebView(
            state = state,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            navigator = navigator,
            onCreated = {
                it.setBackgroundColor(background)
                it.settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    setSupportZoom(true)
                    builtInZoomControls = true
                    displayZoomControls = false
                }
                // Pages without a dark look of their own are darkened in the dark appearance
                if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
                    WebSettingsCompat.setAlgorithmicDarkeningAllowed(it.settings, true)
                }
            },
            client = client,
            chromeClient = chromeClient,
            // The web view takes its light or dark look from its context's theme, which follows the
            // app's appearance rather than the system's
            factory = { context ->
                android.webkit.WebView(
                    ContextThemeWrapper(context, if (night) R.style.ThemeOverlay_WebView_Dark else R.style.ThemeOverlay_WebView_Light),
                )
            },
        )
    }
}

/** How far the page has loaded, and how much the fill showing it shows. */
@Stable
private class LoadProgress {
    val value = Animatable(0f)
    val shown = Animatable(0f)
}

/**
 * Follows [state]'s loading: the fill shows only for loads that take longer than [ProgressDelay],
 * never goes back within a load, and on finishing runs to the end before it fades.
 */
@Composable
private fun rememberLoadProgress(state: WebViewState): LoadProgress {
    val progress = remember { LoadProgress() }
    LaunchedEffect(state) {
        snapshotFlow { state.loadingState is LoadingState.Loading }
            .distinctUntilChanged()
            .collectLatest { loading ->
                if (loading) {
                    progress.value.snapTo(0f)
                    progress.shown.snapTo(0f)
                    coroutineScope {
                        launch {
                            delay(ProgressDelay)
                            progress.shown.animateTo(1f, Motion.fadeIn())
                        }
                        snapshotFlow { (state.loadingState as? LoadingState.Loading)?.progress ?: 0f }
                            .collect { reported ->
                                if (reported > progress.value.targetValue) {
                                    launch { progress.value.animateTo(reported, Motion.settle(Motion.ProgressThreshold)) }
                                }
                            }
                    }
                } else if (progress.shown.value > 0f) {
                    progress.value.animateTo(1f, Motion.settle(Motion.ProgressThreshold))
                    progress.shown.animateTo(0f, Motion.fadeOut())
                }
            }
    }
    return progress
}

/** Loads quicker than this show no progress, so a fast page doesn't flash it. */
private const val ProgressDelay = 200L

/**
 * The page's title and, under it, the site it is on, in a capsule that fills from its start as the
 * page loads. The domain loses its start rather than its end when it is too long, as its end names
 * the site; a page over plain HTTP is marked as not secure. Until the page gives a title, the
 * domain stands alone in its place. Tapping it opens the page's menu.
 */
@Composable
private fun AddressCapsule(
    title: String,
    url: String?,
    fill: LoadProgress,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ExtendedTheme.colors
    val uri = remember(url) { url?.toUri() }
    val host = uri?.host.orEmpty().lowercase().removePrefix("www.")
    val insecure = host.isNotEmpty() && uri?.scheme.equals("http", ignoreCase = true)
    val fillColor = colors.accent.copy(alpha = if (colors.isNightMode) 0.20f else 0.16f)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(GlassTopBarItemHeight)
            .floatingGlass(CapsuleShape)
            .clip(CapsuleShape)
            // Always in the chain, drawing nothing while idle: the glass under it must not see its
            // modifiers change while it draws
            .drawBehind {
                val shown = fill.shown.value
                if (shown > 0f) {
                    drawRect(
                        color = fillColor.copy(alpha = fillColor.alpha * shown),
                        size = Size(size.width * fill.value.value, size.height),
                    )
                }
            }
            // The glass answers the press
            .clickable(interactionSource = null, indication = null, onClick = onClick)
            .padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        if (title.isNotEmpty() || host.isEmpty()) {
            Text(
                text = title,
                style = TextStyles.label,
                color = colors.topBarContent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (host.isNotEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (insecure) {
                    val warning = MaterialTheme.colors.error
                    Icon(
                        imageVector = Symbols.Warning,
                        contentDescription = null,
                        tint = warning,
                        modifier = Modifier.size(12.dp),
                    )
                    Text(
                        text = stringResource(id = R.string.web_not_secure),
                        style = TextStyles.footnote,
                        fontWeight = FontWeight.Medium,
                        color = warning,
                        modifier = Modifier.padding(start = 2.dp, end = 4.dp),
                        maxLines = 1,
                    )
                }
                val alone = title.isEmpty()
                Text(
                    text = host,
                    style = if (alone) TextStyles.label else TextStyles.footnote,
                    color = if (alone) colors.topBarContent else colors.topBarContentSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.StartEllipsis,
                )
            }
        }
    }
}

private val CapsuleShape = SmoothCornerShape.Capsule
