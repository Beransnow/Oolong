package oolong.client.ui.page.login

import android.webkit.WebView
import androidx.compose.material.SnackbarDuration
import androidx.compose.material.SnackbarHostState
import androidx.compose.material.rememberScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import oolong.client.R
import oolong.client.api.retrofit.exception.userMessage
import oolong.client.components.web.WebSessions
import oolong.client.repository.AccountActions
import oolong.client.ui.common.LocalAccountActions
import oolong.client.ui.common.theme.compose.Symbols
import oolong.client.ui.navigation.Navigator
import oolong.client.ui.page.LocalNavigator
import oolong.client.ui.page.webview.MyWebChromeClient
import oolong.client.ui.page.webview.MyWebViewClient
import oolong.client.ui.page.webview.WebPageScaffold
import oolong.client.ui.widgets.compose.ContextMenuItem
import oolong.client.ui.widgets.compose.rememberSaveableWebViewState
import oolong.client.ui.widgets.compose.rememberWebViewNavigator
import oolong.client.utils.ClientUtils
import oolong.client.utils.parseCookie

const val LOGIN_URL =
    "https://wappass.baidu.com/passport?login&u=https%3A%2F%2Ftieba.baidu.com%2Findex%2Ftbwise%2Fmine"

@Composable
fun LoginPage(
    navigator: Navigator = LocalNavigator.current,
) {
    val context = LocalContext.current
    val accounts = LocalAccountActions.current
    val coroutineScope = rememberCoroutineScope()
    val webViewState = rememberSaveableWebViewState()
    val webViewNavigator = rememberWebViewNavigator()
    val scaffoldState = rememberScaffoldState()
    val refresh = stringResource(id = R.string.title_refresh)
    WebPageScaffold(
        initialUrl = LOGIN_URL,
        state = webViewState,
        navigator = webViewNavigator,
        client = remember(navigator) {
            LoginWebViewClient(navigator, accounts, coroutineScope, scaffoldState.snackbarHostState)
        },
        chromeClient = remember { MyWebChromeClient(context, coroutineScope) },
        onClose = { navigator.navigateUp() },
        menuItems = { listOf(ContextMenuItem(Symbols.Refresh, refresh) { webViewNavigator.reload() }) },
        // A session with no login in it, so the current account isn't found logged in already
        attachSession = { WebSessions.instance.attachForLogin(it) },
        title = stringResource(id = R.string.title_login_baidu),
        scaffoldState = scaffoldState,
    )
}

class LoginWebViewClient(
    nativeNavigator: Navigator?,
    private val accounts: AccountActions,
    val coroutineScope: CoroutineScope,
    val snackbarHostState: SnackbarHostState,
) : MyWebViewClient(nativeNavigator) {
    private var isLoadingAccount = false

    override fun onPageFinished(view: WebView, url: String?) {
        super.onPageFinished(view, url)
        if (url == null) {
            return
        }
        if (isLoadingAccount) {
            return
        }
        // Read from the page's own session, which the login is made in
        val cookieStr = WebSessions.instance.cookies(view, url) ?: return
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
                        WebSessions.instance.adoptLogin(view, account)
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
                            context.getString(R.string.text_login_failed, it.userMessage(context)),
                            duration = SnackbarDuration.Short
                        )
                    }
            }
        }
    }
}
