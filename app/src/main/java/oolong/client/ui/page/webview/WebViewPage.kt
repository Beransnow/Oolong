package oolong.client.ui.page.webview

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.location.LocationManager
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.JsResult
import android.webkit.ValueCallback
import android.webkit.WebResourceRequest
import android.webkit.WebView
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
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
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.getSystemService
import androidx.core.location.LocationManagerCompat
import androidx.core.net.toUri
import com.hjq.permissions.Permission
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.cancellable
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import oolong.client.App
import oolong.client.R
import oolong.client.arch.GlobalEvent
import oolong.client.arch.onGlobalEvent
import oolong.client.models.database.Account
import oolong.client.ui.common.LocalAccount
import oolong.client.ui.common.theme.compose.ExtendedTheme
import oolong.client.ui.common.theme.compose.Motion
import oolong.client.ui.common.theme.compose.Symbols
import oolong.client.ui.common.theme.compose.currentColors
import oolong.client.ui.navigation.Navigator
import oolong.client.ui.page.LocalNavigator
import oolong.client.ui.widgets.compose.AccompanistWebChromeClient
import oolong.client.ui.widgets.compose.AccompanistWebViewClient
import oolong.client.ui.widgets.compose.AppDialog
import oolong.client.ui.widgets.compose.AppDialogs
import oolong.client.ui.widgets.compose.BackNavigationIcon
import oolong.client.ui.widgets.compose.ContextMenuItem
import oolong.client.ui.widgets.compose.GlassScaffold
import oolong.client.ui.widgets.compose.GlassTopBar
import oolong.client.ui.widgets.compose.LazyLoad
import oolong.client.ui.widgets.compose.LoadingState
import oolong.client.ui.widgets.compose.MenuIconButton
import oolong.client.ui.widgets.compose.WebView
import oolong.client.ui.widgets.compose.rememberSaveableWebViewState
import oolong.client.ui.widgets.compose.rememberWebViewNavigator
import oolong.client.utils.PermissionUtils
import oolong.client.utils.PermissionUtils.PermissionData
import oolong.client.utils.TiebaUtil
import oolong.client.utils.appPreferences
import oolong.client.utils.bdussCookie
import oolong.client.utils.compose.launchActivityForResult
import oolong.client.utils.isInternalHost
import oolong.client.utils.isTiebaHost
import oolong.client.utils.parseCookie
import oolong.client.utils.toTiebaKey
import java.lang.ref.WeakReference
import java.util.UUID

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebViewPage(
    initialUrl: String,
    navigator: Navigator = LocalNavigator.current,
) {
    val context = LocalContext.current
    val account by rememberUpdatedState(LocalAccount.current)
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
        webViewNavigator.loadUrl(initialUrl)
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
                    val copyLink = stringResource(id = R.string.title_copy_link)
                    val openInBrowser = stringResource(id = R.string.title_open_in_browser)
                    val refresh = stringResource(id = R.string.title_refresh)
                    MenuIconButton(
                        icon = Symbols.MoreVert,
                        contentDescription = stringResource(id = R.string.btn_more),
                        items = {
                            val url = webViewState.webView?.url ?: initialUrl
                            listOf(
                                ContextMenuItem(Symbols.Link, copyLink) { TiebaUtil.copyText(context, url) },
                                ContextMenuItem(Symbols.OpenInBrowser, openInBrowser) {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
                                },
                                ContextMenuItem(Symbols.Refresh, refresh) { webViewNavigator.reload() },
                            )
                        },
                    )
                },
            )
        }
    ) { paddingValues ->
        Box {
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
                client = remember(navigator) { MyWebViewClient(navigator) { account } },
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

open class MyWebViewClient(
    protected val nativeNavigator: Navigator? = null,
    /** The account whose login the pages get. */
    private val account: () -> Account? = { null },
) : AccompanistWebViewClient() {
    val context: Context
        get() = state.webView?.context ?: App.INSTANCE

    private fun interceptWebViewRequest(
        webView: WebView,
        request: WebResourceRequest,
    ): Boolean {
        val newUri = request.url
        val scheme = newUri.scheme?.lowercase() ?: return false
        val host = newUri.host?.lowercase() ?: return false
        val path = newUri.path?.lowercase() ?: return false
        val isHttp = scheme.startsWith("http")
        val isTieba = isTiebaHost(host)
        val isInternal = isInternalHost(host)
        return when {
            isHttp && isTieba -> {
                newUri.toTiebaKey()?.let { direction ->
                    nativeNavigator?.navigate(direction)
                    true
                } ?: false
            }

            isHttp && !isInternal -> {
                if (context.appPreferences.useWebView)
                    false
                else {
                    if (context.appPreferences.useCustomTabs) {
                        runCatching {
                            CustomTabsIntent.Builder()
                                .setShowTitle(true)
                                .setDefaultColorSchemeParams(
                                    CustomTabColorSchemeParams.Builder()
                                        .setToolbarColor(currentColors(context).sheet.toArgb())
                                        .setNavigationBarColor(currentColors(context).sheet.toArgb())
                                        .setNavigationBarDividerColor(
                                            currentColors(context).run { divider.compositeOver(sheet) }.toArgb()
                                        )
                                        .build()
                                )
                                .build()
                                .launchUrl(context, newUri)
                        }.onFailure {
                            context.startActivity(
                                Intent(
                                    Intent.ACTION_VIEW,
                                    newUri
                                ).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                            )
                        }
                    } else {
                        context.startActivity(
                            Intent(
                                Intent.ACTION_VIEW,
                                newUri
                            ).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        )
                    }
                    true
                }
            }

            !isHttp -> {
                val currentUri = webView.url?.toUri()
                val currentHost = currentUri?.host
                if (currentHost != null) {
                    launchThirdPartyApp(newUri, currentHost)
                }
                true
            }

            else -> false
        }
    }

    private fun launchThirdPartyApp(
        intent: Intent,
        host: String,
    ) {
        val resolves = context.packageManager.queryIntentActivities(intent, 0)
        if (resolves.isEmpty()) return
        val appName = if (resolves.size == 1) {
            resolves[0].loadLabel(context.packageManager)
        } else {
            context.getString(R.string.name_multi_app)
        }
        val scheme = intent.scheme ?: ""
        AppDialogs.ask(
            context,
            AppDialog.Permission(
                key = permissionKey("${host}_$scheme", PERMISSION_START_APP),
                title = context.getString(R.string.title_start_app_permission, host, appName),
                icon = R.drawable.ic_round_exit_to_app,
            ) { granted, _ -> if (granted) context.startActivity(intent) }
        )
    }

    private fun launchThirdPartyApp(
        uri: Uri,
        host: String,
    ) {
        if (uri.scheme.equals("intent", ignoreCase = true)) {
            launchThirdPartyApp(
                Intent.parseUri(uri.toString(), Intent.URI_INTENT_SCHEME)
                    .addCategory(Intent.CATEGORY_BROWSABLE), host
            )
        } else {
            launchThirdPartyApp(
                Intent(
                    Intent.ACTION_VIEW,
                    uri
                ).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP), host
            )
        }
    }

    open fun injectCookies(url: String) {
        val cookieStr = CookieManager.getInstance().getCookie(url) ?: ""
        val cookies = parseCookie(cookieStr)
        val BDUSS = cookies["BDUSS"]
        val currentAccountBDUSS = account()?.bduss
        if (currentAccountBDUSS != null && BDUSS != currentAccountBDUSS) {
            CookieManager.getInstance()
                .setCookie(url, bdussCookie(currentAccountBDUSS))
        }
    }

    override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        injectCookies(url ?: "")
    }

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest?): Boolean {
        if (request == null) return false
        return interceptWebViewRequest(view, request)
    }
}

class MyWebChromeClient(
    context: Context,
    coroutineScope: CoroutineScope,
) : AccompanistWebChromeClient() {
    private val contextWeakReference = WeakReference(context)

    val context: Context
        get() = state.webView?.context ?: contextWeakReference.get() ?: App.INSTANCE

    private var uploadMessage: ValueCallback<Array<Uri>>? = null

    val id: String = UUID.randomUUID().toString()

    init {
        coroutineScope.onGlobalEvent<GlobalEvent.ActivityResult>(
            filter = { it.requesterId == id },
        ) {
            uploadMessage?.onReceiveValue(FileChooserParams.parseResult(it.resultCode, it.intent))
            uploadMessage = null
        }
    }

    private fun isEnabledLocationFunction(): Boolean {
        val locationManager = context.getSystemService<LocationManager>()
        return locationManager != null && LocationManagerCompat.isLocationEnabled(locationManager)
    }

    override fun onGeolocationPermissionsShowPrompt(
        origin: String?,
        callback: GeolocationPermissions.Callback?,
    ) {
        if (origin == null || callback == null) return
        AppDialogs.ask(
            context,
            AppDialog.Permission(
                key = permissionKey(origin, PERMISSION_LOCATION),
                title = context.getString(
                    R.string.title_ask_permission,
                    origin,
                    context.getString(R.string.common_permission_location)
                ),
                icon = R.drawable.ic_round_location_on,
            ) { granted, isForever ->
                if (!granted) {
                    callback.invoke(origin, false, false)
                } else PermissionUtils.askPermission(
                    context,
                    PermissionData(
                        listOf(
                            Permission.ACCESS_COARSE_LOCATION,
                            Permission.ACCESS_FINE_LOCATION
                        ),
                        context.getString(R.string.usage_webview_location_permission)
                    ),
                    R.string.tip_no_permission,
                    {
                        if (isEnabledLocationFunction()) {
                            callback.invoke(origin, true, isForever)
                        } else {
                            callback.invoke(origin, false, false)
                        }
                    }
                ) {
                    callback.invoke(origin, false, false)
                }
            }
        )
    }

    override fun onShowFileChooser(
        webView: WebView?,
        filePathCallback: ValueCallback<Array<Uri>>?,
        fileChooserParams: FileChooserParams?,
    ): Boolean {
        if (webView == null || filePathCallback == null || fileChooserParams == null) return false
        uploadMessage?.onReceiveValue(null)
        uploadMessage = filePathCallback
        launchActivityForResult(id, fileChooserParams.createIntent())
        return true
    }

    override fun onJsAlert(
        view: WebView?,
        url: String?,
        message: String?,
        result: JsResult?,
    ): Boolean {
        AppDialogs.show(AppDialog.Message(message = message.orEmpty(), onConfirm = { result?.confirm() }))
        return true
    }

    override fun onJsConfirm(
        view: WebView,
        url: String?,
        message: String?,
        result: JsResult,
    ): Boolean {
        val context = view.context ?: context
        if ("ClipboardGuardCopyRequest".equals(message, ignoreCase = true)) {
            val uri = url?.toUri()
            if (uri != null && uri.host != null) {
                AppDialogs.ask(
                    context,
                    AppDialog.Permission(
                        key = permissionKey(uri.host!!, PERMISSION_CLIPBOARD_COPY),
                        title = context.getString(R.string.title_ask_permission_clipboard_copy, uri.host),
                        icon = R.drawable.ic_round_file_copy,
                    ) { granted, _ -> if (granted) result.confirm() else result.cancel() }
                )
            }
        } else {
            AppDialogs.show(
                AppDialog.Message(
                    message = message.orEmpty(),
                    onConfirm = { result.confirm() },
                    cancel = context.getString(R.string.button_cancel),
                    onCancel = { result.cancel() },
                )
            )
        }
        return true
    }
}

@Composable
fun MyWebView() {
}

/** The key a site's answer is kept under: what it asked about, and for whom. */
private fun permissionKey(data: String, permission: Int) = "${data}_$permission"

private const val PERMISSION_LOCATION = 0
private const val PERMISSION_START_APP = 1
private const val PERMISSION_CLIPBOARD_COPY = 2
