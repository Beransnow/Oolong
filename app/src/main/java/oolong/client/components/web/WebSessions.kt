package oolong.client.components.web

import android.content.Context
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebView
import androidx.core.content.edit
import androidx.webkit.ProfileStore
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import oolong.client.App
import oolong.client.BuildConfig
import oolong.client.models.database.Account
import java.io.File
import java.util.UUID

/**
 * Which account's web login a web view carries: its cookies, kept apart from other accounts', so a
 * page opened as one account is never logged in as another, and the login page never finds the
 * current account already logged in.
 *
 * All of it runs on the main thread, as web views do.
 */
interface WebSessions {
    /** Before the web view first loads: it gets [account]'s login, or none for null. */
    fun attach(webView: WebView, account: Account?)

    /** Before the login page loads: a session with no login in it. */
    fun attachForLogin(webView: WebView)

    /** The cookies the web view sends to [url]. */
    fun cookies(webView: WebView, url: String): String?

    /** After a login succeeds in [webView], which [attachForLogin] set up: its session becomes [account]'s. */
    fun adoptLogin(webView: WebView, account: Account)

    /** [account] is gone: so is its session. */
    fun remove(account: Account)

    companion object {
        /**
         * The app's sessions: an own profile for each account where the device's web view has
         * profiles, or else one cookie store whose cookies are swapped as accounts take turns.
         */
        val instance: WebSessions by lazy {
            val context = App.INSTANCE
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val profiles = WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE) && !forcedSwap(context)
            Log.i(TAG, if (profiles) "Keeping web logins in a profile for each account" else "Keeping web logins by swapping cookies")
            if (profiles) ProfileSessions(prefs, fallback = CookieSwapSessions(prefs)) else CookieSwapSessions(prefs)
        }

        /** In debug builds, a file named so in the app's files makes it swap cookies, to try that on any device. */
        private fun forcedSwap(context: Context): Boolean =
            BuildConfig.DEBUG && File(context.filesDir, "force_cookie_swap").exists()

        private const val PREFS = "web_sessions"
        private const val TAG = "WebSessions"
    }
}

/** Who a session is for, as kept: an account's uid, or one of these. */
private const val GUEST = "guest"
private const val LOGIN = "login"

/**
 * One cookie store for all: an account's cookies are put in when its page opens, after those of
 * whoever had it before are kept for them and cleared out.
 */
private class CookieSwapSessions(private val prefs: android.content.SharedPreferences) : WebSessions {
    private val manager get() = CookieManager.getInstance()

    /** Whose cookies are in the store now; null when not known, as before the first swap. */
    private var owner: String?
        get() = prefs.getString(KEY_OWNER, null)
        set(value) = prefs.edit(commit = true) { putString(KEY_OWNER, value) }

    override fun attach(webView: WebView, account: Account?) {
        val key = account?.uid ?: GUEST
        if (owner == key) return
        swapTo(key) { account?.let { BaiduCookies.seed(manager, it) } }
    }

    override fun attachForLogin(webView: WebView) = swapTo(LOGIN) {}

    override fun cookies(webView: WebView, url: String): String? = manager.getCookie(url)

    override fun adoptLogin(webView: WebView, account: Account) {
        owner = account.uid
        keep(account.uid)
    }

    override fun remove(account: Account) {
        prefs.edit(commit = true) { remove(KEY_KEPT + account.uid) }
        if (owner == account.uid) {
            manager.removeAllCookies(null)
            owner = null
        }
    }

    /** Keeps the store's cookies for their owner, empties it and puts in [key]'s, or [seed]s it. */
    private fun swapTo(key: String, seed: () -> Unit) {
        owner?.takeIf { it != LOGIN }?.let { keep(it) }
        manager.removeAllCookies(null)
        val kept = prefs.getString(KEY_KEPT + key, null)
        if (kept != null) BaiduCookies.write(manager, kept) else seed()
        owner = key
    }

    private fun keep(key: String) {
        val cookies = BaiduCookies.read(manager)
        prefs.edit(commit = true) { if (cookies != null) putString(KEY_KEPT + key, cookies) else remove(KEY_KEPT + key) }
    }

    private companion object {
        const val KEY_OWNER = "swap_owner"
        const val KEY_KEPT = "swap_kept_"
    }
}

/**
 * A profile of the web view for each account, each with its own cookies and storage. The login
 * page gets a new one, which becomes the account's once it has logged in. Should profiles fail on a
 * device, it swaps cookies instead from then on.
 */
private class ProfileSessions(
    private val prefs: android.content.SharedPreferences,
    private val fallback: CookieSwapSessions,
) : WebSessions {
    private var failed = false
    private val store get() = ProfileStore.getInstance()

    init {
        // Profiles left behind: a login given up, or an account's removed while a page still used it
        runCatching {
            val used = prefs.all.filterKeys { it.startsWith(KEY_PROFILE) }.values.toSet()
            store.allProfileNames
                .filter { (it.startsWith(LOGIN_PREFIX) || it.startsWith(ACCOUNT_PREFIX)) && it !in used }
                .forEach { name -> runCatching { store.deleteProfile(name) } }
        }
    }

    override fun attach(webView: WebView, account: Account?) = orFallback({ fallback.attach(webView, account) }) {
        val name = account?.let { profileOf(it) } ?: GUEST_PROFILE
        // Not by getProfile: for a profile not yet made, some web view versions fail inside it
        val isNew = name !in store.allProfileNames
        val profile = store.getOrCreateProfile(name)
        // An account from before profiles, or one whose profile is gone: its saved login goes in
        if (isNew && account != null) BaiduCookies.seed(profile.cookieManager, account)
        WebViewCompat.setProfile(webView, name)
    }

    override fun attachForLogin(webView: WebView) = orFallback({ fallback.attachForLogin(webView) }) {
        val name = LOGIN_PREFIX + UUID.randomUUID()
        store.getOrCreateProfile(name)
        WebViewCompat.setProfile(webView, name)
    }

    override fun cookies(webView: WebView, url: String): String? =
        if (failed) fallback.cookies(webView, url) else WebViewCompat.getProfile(webView).cookieManager.getCookie(url)

    override fun adoptLogin(webView: WebView, account: Account) = orFallback({ fallback.adoptLogin(webView, account) }) {
        val profile = WebViewCompat.getProfile(webView)
        profile.cookieManager.flush()
        val before = prefs.getString(KEY_PROFILE + account.uid, null)
        prefs.edit(commit = true) { putString(KEY_PROFILE + account.uid, profile.name) }
        // Logged in again: the old profile goes, now or, if a page still uses it, at the next start
        before?.takeIf { it != profile.name }?.let { runCatching { store.deleteProfile(it) } }
    }

    override fun remove(account: Account) = orFallback({ fallback.remove(account) }) {
        val name = prefs.getString(KEY_PROFILE + account.uid, null) ?: return@orFallback
        prefs.edit(commit = true) { remove(KEY_PROFILE + account.uid) }
        runCatching { store.deleteProfile(name) }
    }

    /** The profile [account]'s pages use, named for it the first time. */
    private fun profileOf(account: Account): String =
        prefs.getString(KEY_PROFILE + account.uid, null) ?: (ACCOUNT_PREFIX + account.uid).also { name ->
            prefs.edit(commit = true) { putString(KEY_PROFILE + account.uid, name) }
        }

    /** Runs [block], or [fallback] once profiles have failed, which they then stay. */
    private inline fun orFallback(fallback: () -> Unit, block: () -> Unit) {
        if (!failed) {
            try {
                return block()
            } catch (e: RuntimeException) {
                Log.w(TAG, "Web view profiles failed; swapping cookies from now on", e)
                failed = true
            }
        }
        fallback()
    }

    private companion object {
        const val KEY_PROFILE = "profile_"
        const val ACCOUNT_PREFIX = "account_"
        const val LOGIN_PREFIX = "login_"
        const val GUEST_PROFILE = "guest"
        const val TAG = "WebSessions"
    }
}
