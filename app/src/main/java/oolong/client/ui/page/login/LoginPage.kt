package oolong.client.ui.page.login

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebView
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Icon
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.SnackbarDuration
import androidx.compose.material.SnackbarHostState
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.cancellable
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import oolong.client.R
import oolong.client.api.retrofit.exception.getErrorMessage
import oolong.client.repository.AccountActions
import oolong.client.ui.common.LocalAccountActions
import oolong.client.ui.common.theme.compose.ExtendedTheme
import oolong.client.ui.common.theme.compose.Motion
import oolong.client.ui.common.theme.compose.Symbols
import oolong.client.ui.navigation.Navigator
import oolong.client.ui.page.LocalNavigator
import oolong.client.ui.page.webview.MyWebChromeClient
import oolong.client.ui.page.webview.MyWebViewClient
import oolong.client.ui.widgets.compose.BackNavigationIcon
import oolong.client.ui.widgets.compose.ContextMenuItem
import oolong.client.ui.widgets.compose.GlassScaffold
import oolong.client.ui.widgets.compose.GlassTopBar
import oolong.client.ui.widgets.compose.LazyLoad
import oolong.client.ui.widgets.compose.LoadingState
import oolong.client.ui.widgets.compose.LocalSnackbarHostState
import oolong.client.ui.widgets.compose.MenuIconButton
import oolong.client.ui.widgets.compose.WebView
import oolong.client.ui.widgets.compose.rememberSaveableWebViewState
import oolong.client.ui.widgets.compose.rememberWebViewNavigator
import oolong.client.utils.ClientUtils
import oolong.client.utils.isInternalHost
import oolong.client.utils.parseCookie

const val LOGIN_URL =
    "https://wappass.baidu.com/passport?login&u=https%3A%2F%2Ftieba.baidu.com%2Findex%2Ftbwise%2Fmine"

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LoginPage(
    navigator: Navigator = LocalNavigator.current,
) {
    val context = LocalContext.current
    val accounts = LocalAccountActions.current
    val coroutineScope = rememberCoroutineScope()
    val webViewState = rememberSaveableWebViewState()
    val webViewNavigator = rememberWebViewNavigator()
    var loaded by rememberSaveable {
        mutableStateOf(false)
    }
    var pageTitle by rememberSaveable {
        mutableStateOf("")
    }
    val displayPageTitle by remember {
        derivedStateOf {
            pageTitle.ifEmpty {
                context.getString(R.string.title_default)
            }
        }
    }
    val currentHost by remember {
        derivedStateOf {
            webViewState.lastLoadedUrl?.toUri()?.host.orEmpty().lowercase()
        }
    }
    val isExternalHost by remember {
        derivedStateOf {
            currentHost.isNotEmpty() && !isInternalHost(currentHost)
        }
    }

    DisposableEffect(Unit) {
        val job = coroutineScope.launch {
            snapshotFlow { webViewState.pageTitle }
                .filterNotNull()
                .filter { it.isNotEmpty() }
                .cancellable()
                .collect {
                    pageTitle = it
                }
        }
        onDispose {
            job.cancel()
        }
    }

    LazyLoad(loaded = loaded) {
        webViewNavigator.loadUrl(LOGIN_URL)
        loaded = true
    }

    val isLoading by remember {
        derivedStateOf {
            webViewState.loadingState is LoadingState.Loading
        }
    }

    val progress by remember {
        derivedStateOf {
            webViewState.loadingState.let {
                if (it is LoadingState.Loading) {
                    it.progress
                } else {
                    0f
                }
            }
        }
    }

    val animatedProgress by animateFloatAsState(targetValue = progress, animationSpec = Motion.settle(), label = "progress")

    GlassScaffold(
        sampleContent = false,
        topBar = {
            GlassTopBar(
                title = {
                    Column {
                        Text(
                            text = displayPageTitle,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (isExternalHost) {
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
                navigationIcon = { BackNavigationIcon(onBackPressed = { navigator.navigateUp() }) },
                actions = {
                    val refresh = stringResource(id = R.string.title_refresh)
                    MenuIconButton(
                        icon = Symbols.MoreVert,
                        contentDescription = stringResource(id = R.string.btn_more),
                        items = { listOf(ContextMenuItem(Symbols.Refresh, refresh) { webViewNavigator.reload() }) },
                    )
                },
            )
        }
    ) { paddingValues ->
        Box {
            val snackbarHostState = LocalSnackbarHostState.current
            WebView(
                state = webViewState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                navigator = webViewNavigator,
                onCreated = {
                    it.settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        setSupportZoom(true)
                        builtInZoomControls = true
                        displayZoomControls = false
                    }
                },
                client = remember(navigator) {
                    LoginWebViewClient(
                        navigator,
                        accounts,
                        coroutineScope,
                        snackbarHostState
                    )
                },
                chromeClient = remember { MyWebChromeClient(context, coroutineScope) }
            )

            if (isLoading) {
                LinearProgressIndicator(
                    progress = animatedProgress,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

class LoginWebViewClient(
    nativeNavigator: Navigator?,
    private val accounts: AccountActions,
    val coroutineScope: CoroutineScope,
    val snackbarHostState: SnackbarHostState,
) : MyWebViewClient(nativeNavigator) {
    private var isLoadingAccount = false

    override fun injectCookies(url: String) {}

    override fun onPageFinished(view: WebView, url: String?) {
        super.onPageFinished(view, url)
        if (url == null) {
            return
        }
        if (isLoadingAccount) {
            return
        }
        val cookieStr = CookieManager.getInstance().getCookie(url) ?: return
        val cookies = parseCookie(cookieStr).mapKeys { it.key.uppercase() }
        val bduss = cookies["BDUSS"]
        val sToken = cookies["STOKEN"]
        val baiduId = cookies["BAIDUID"]
        if (url.startsWith("https://tieba.baidu.com/index/tbwise/") || url.startsWith("https://tiebac.baidu.com/index/tbwise/")) {
            if (bduss == null || sToken == null) {
                return
            }
            if (!baiduId.isNullOrEmpty() && ClientUtils.baiduId.isNullOrEmpty()) {
                coroutineScope.launch {
                    ClientUtils.saveBaiduId(context, baiduId)
                }
            }
            coroutineScope.launch {
                snackbarHostState.showSnackbar(
                    context.getString(R.string.text_please_wait),
                    duration = SnackbarDuration.Indefinite
                )
            }
            isLoadingAccount = true
            coroutineScope.launch {
                runCatching { accounts.logIn(bduss, sToken, cookieStr) }
                    .onSuccess { account ->
                        isLoadingAccount = false
                        accounts.switchTo(account.id)
                        snackbarHostState.currentSnackbarData?.dismiss()
                        launch {
                            snackbarHostState.showSnackbar(
                                context.getString(R.string.text_login_success),
                                duration = SnackbarDuration.Short
                            )
                        }
                        delay(1500L)
                        nativeNavigator?.navigateUp()
                    }
                    .onFailure {
                        isLoadingAccount = false
                        navigator.loadUrl(LOGIN_URL)
                        snackbarHostState.currentSnackbarData?.dismiss()
                        snackbarHostState.showSnackbar(
                            context.getString(R.string.text_login_failed, it.getErrorMessage()),
                            duration = SnackbarDuration.Short
                        )
                    }
            }
        }
    }
}
