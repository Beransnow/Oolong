package com.huanchengfly.tieba.post.utils

import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.annotation.StyleRes
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import coil3.size.Scale
import com.google.android.material.appbar.AppBarLayout
import com.huanchengfly.tieba.post.App
import com.huanchengfly.tieba.post.App.Companion.INSTANCE
import com.huanchengfly.tieba.post.App.Companion.translucentBackground
import com.huanchengfly.tieba.post.R
import com.huanchengfly.tieba.post.activities.BaseActivity
import com.huanchengfly.tieba.post.components.images.loadDrawable
import com.huanchengfly.tieba.post.dataStore
import com.huanchengfly.tieba.post.getBoolean
import com.huanchengfly.tieba.post.getInt
import com.huanchengfly.tieba.post.getString
import com.huanchengfly.tieba.post.interfaces.BackgroundTintable
import com.huanchengfly.tieba.post.putBoolean
import com.huanchengfly.tieba.post.putString
import com.huanchengfly.tieba.post.ui.common.theme.utils.ThemeUtils
import com.huanchengfly.tieba.post.ui.widgets.theme.TintSwipeRefreshLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.Locale

object ThemeUtil {
    /** The theme choices; change them through [updateSettings]. */
    val settingsState: MutableState<ThemeSettings> by lazy(mode = LazyThreadSafetyMode.SYNCHRONIZED) {
        mutableStateOf(ThemeSettings()).also { if (App.isInitialized) load(it) }
    }

    /** Whether [settingsState] holds the saved settings rather than the defaults. */
    @Volatile
    private var loaded = false

    /**
     * Reads the saved settings into [state]. Asked for before the app has started, the defaults
     * stand in until it has; saving them then would lose the saved ones.
     */
    private fun load(state: MutableState<ThemeSettings>) {
        state.value = runBlocking { ThemeSettings.from(dataStore.data.first()) }
        loaded = true
    }

    private fun ensureLoaded() {
        if (!loaded && App.isInitialized) synchronized(this) { if (!loaded) load(settingsState) }
    }

    /** The name of the theme drawn now, worked out from [settingsState]; see [refresh]. */
    val themeState: MutableState<String> by lazy(mode = LazyThreadSafetyMode.SYNCHRONIZED) {
        mutableStateOf(settingsState.value.themeName(isSystemDark()))
    }

    const val TAG = "ThemeUtil"

    const val KEY_THEME = "theme"
    const val KEY_DARK_THEME = "dark_theme"
    const val KEY_OLD_THEME = "old_theme"
    const val KEY_USE_DYNAMIC_THEME = "useDynamicColorTheme"
    const val KEY_SWITCH_REASON = "switch_reason"
    const val KEY_TRANSLUCENT_PRIMARY_COLOR = "translucent_primary_color"
    const val KEY_TRANSLUCENT_THEME_BACKGROUND_PATH = "translucent_theme_background_path"

    const val THEME_TRANSLUCENT = "translucent"
    const val THEME_TRANSLUCENT_LIGHT = "translucent_light"
    const val THEME_TRANSLUCENT_DARK = "translucent_dark"
    const val THEME_CUSTOM = "custom"
    const val THEME_DEFAULT = "tieba"
    const val THEME_BLACK = "black"
    const val THEME_BLUE = "blue"
    const val THEME_PURPLE = "purple"
    const val THEME_PINK = "pink"
    const val THEME_RED = "red"
    const val THEME_BLUE_DARK = "blue_dark"
    const val THEME_GREY_DARK = "grey_dark"
    const val THEME_AMOLED_DARK = "amoled_dark"

    const val TRANSLUCENT_THEME_LIGHT = 0
    const val TRANSLUCENT_THEME_DARK = 1

    private val context: Context
        get() = INSTANCE

    val dataStore: DataStore<Preferences>
        get() = INSTANCE.dataStore

    fun getTextColor(context: Context?): Int {
        return ThemeUtils.getColorByAttr(context, R.attr.colorText)
    }

    @JvmStatic
    fun getSecondaryTextColor(context: Context?): Int {
        return ThemeUtils.getColorByAttr(context, R.attr.colorTextSecondary)
    }

    private fun isSystemDark(): Boolean = App.isInitialized && App.isSystemNight

    /** Whether the app is dark now, as [settingsState] and the system say. */
    fun isDark(): Boolean = settingsState.value.isDark(isSystemDark())

    /** Works out the theme drawn now again, as after the system turns dark or light. */
    fun refresh() {
        ensureLoaded()
        val name = settingsState.value.themeName(isSystemDark())
        if (themeState.value != name) themeState.value = name
    }

    /** Changes the theme choices and saves them. */
    fun updateSettings(transform: (ThemeSettings) -> ThemeSettings) {
        ensureLoaded()
        val settings = transform(settingsState.value)
        if (settings == settingsState.value) return
        settingsState.value = settings
        refresh()
        MainScope().launch(Dispatchers.IO) {
            // Saves are not done in the order they are started: each saves the settings as they
            // are by then, so that the last done holds the last change
            dataStore.edit { settingsState.value.writeTo(it) }
        }
    }

    /** Uses the colour [name], dark or light. */
    fun selectAccent(name: String) = updateSettings { it.copy(accent = name) }

    fun selectStyle(style: ColorStyle) = updateSettings { it.copy(style = style) }

    fun selectDarkTheme(name: String) = updateSettings { it.copy(darkTheme = name) }

    /** Shows the picked wallpaper behind the pages while light, or stops showing it. */
    fun setWallpaper(on: Boolean) = updateSettings { it.copy(wallpaper = on) }

    fun selectMonetStyle(style: MonetStyle) = updateSettings { it.copy(monetStyle = style) }

    fun setAppearance(appearance: Appearance) = updateSettings { it.copy(appearance = appearance) }

    /** Switches between light and dark, no longer following the system. */
    fun toggleDark() = updateSettings {
        it.copy(appearance = if (it.isDark(isSystemDark())) Appearance.Light else Appearance.Dark)
    }

    @JvmStatic
    fun setChipThemeByLevel(level: String?, parent: View, vararg textViews: TextView) {
        setChipTheme(Util.getIconColorByLevel(level), parent, *textViews)
    }

    fun setChipTheme(@ColorInt color: Int, parent: View, vararg textViews: TextView) {
        parent.backgroundTintList = ColorStateList.valueOf(color)
        for (textView in textViews) {
            textView.setTextColor(ThemeUtils.getColorByAttr(parent.context, R.attr.colorOnAccent))
        }
    }

    @JvmStatic
    fun setThemeForSwipeRefreshLayout(swipeRefreshLayout: SwipeRefreshLayout) {
        if (swipeRefreshLayout is TintSwipeRefreshLayout) {
            swipeRefreshLayout.tint()
            return
        }
        val context = swipeRefreshLayout.context
        val resources = context.resources
        if (resources != null) {
            swipeRefreshLayout.setProgressBackgroundColorSchemeColor(resources.getColor(R.color.color_swipe_refresh_bg))
            swipeRefreshLayout.setColorSchemeColors(
                ThemeUtils.getColorByAttr(
                    context,
                    R.attr.colorAccent
                )
            )
        }
    }

    @JvmStatic
    fun isNightMode(): Boolean {
        return isNightMode(getRawTheme())
    }

    @JvmStatic
    fun isNightMode(theme: String): Boolean {
        return theme.lowercase(Locale.getDefault()).contains("dark") && !theme.contains(
            THEME_TRANSLUCENT,
            ignoreCase = true
        )
    }

    fun isTranslucentTheme(): Boolean {
        return isTranslucentTheme(getRawTheme())
    }

    @JvmStatic
    fun isTranslucentTheme(theme: String): Boolean {
        return theme.equals(
            THEME_TRANSLUCENT,
            ignoreCase = true
        ) || theme.contains(
            THEME_TRANSLUCENT,
            ignoreCase = true
        )
    }

    fun isDynamicTheme(theme: String): Boolean {
        return theme.endsWith("_dynamic")
    }

    fun isStatusBarFontDark(): Boolean {
        val theme = getRawTheme()
        return if (isTranslucentTheme(theme)) {
            // The saved theme is plain "translucent"; whether its text is dark is a separate setting
            getCurrentTheme(theme) == THEME_TRANSLUCENT_DARK
        } else {
            !isNightMode(theme)
        }
    }

    fun isNavigationBarFontDark(): Boolean {
        return !isDark()
    }

    fun setTheme(context: Activity) {
        val nowTheme = getCurrentTheme()
        context.setTheme(getThemeByName(nowTheme))
    }

    @JvmOverloads
    fun getCurrentTheme(
        theme: String = getRawTheme(),
        checkDynamic: Boolean = false,
    ): String {
        var nowTheme = theme
        // The bare name leaves the text to the saved setting; the variants say it themselves
        if (nowTheme.equals(THEME_TRANSLUCENT, ignoreCase = true)) {
            val colorTheme =
                dataStore.getInt("translucent_background_theme", TRANSLUCENT_THEME_LIGHT)
            nowTheme = if (colorTheme == TRANSLUCENT_THEME_DARK) {
                THEME_TRANSLUCENT_DARK
            } else {
                THEME_TRANSLUCENT_LIGHT
            }
        } else if (checkDynamic && settingsState.value.usesSystemPalette && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            nowTheme = "${nowTheme}_dynamic"
        }
        return nowTheme
    }

    @JvmStatic
    fun setTranslucentThemeWebViewBackground(webView: WebView?) {
        if (webView == null) {
            return
        }
        if (!isTranslucentTheme()) {
            return
        }
        webView.setBackgroundColor(Color.WHITE)
    }

    private fun setAppBarFitsSystemWindow(view: View?, appBarFitsSystemWindow: Boolean) {
        if (view == null) return
        if (view is AppBarLayout) {
            view.setFitsSystemWindows(appBarFitsSystemWindow)
            view.clipToPadding = !appBarFitsSystemWindow
            return
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                setAppBarFitsSystemWindow(view.getChildAt(i), appBarFitsSystemWindow)
            }
        }
    }

    fun setTranslucentBackground(view: View?) {
        if (view == null) {
            return
        }
        if (!isTranslucentTheme()) {
            return
        }
        view.backgroundTintList = null
        view.setBackgroundColor(Color.TRANSPARENT)
    }

    @JvmStatic
    fun setTranslucentDialogBackground(view: View?) {
        if (view == null) {
            return
        }
        if (!isTranslucentTheme()) {
            return
        }
        view.backgroundTintList = null
        view.setBackgroundColor(
            ThemeUtils.getColorById(
                view.context,
                R.color.theme_color_card_grey_dark
            )
        )
    }

    fun setTranslucentThemeBackground(
        activity: BaseActivity,
        view: View?,
        setFitsSystemWindow: Boolean = true,
        useCache: Boolean = false,
    ) {
        if (view == null) {
            return
        }
        if (!isTranslucentTheme()) {
            if (setFitsSystemWindow) {
                setAppBarFitsSystemWindow(view, false)
                view.fitsSystemWindows = false
                (view as ViewGroup).clipToPadding = true
            }
            return
        }
        if (setFitsSystemWindow) {
            if (view is CoordinatorLayout) {
                setAppBarFitsSystemWindow(view, true)
                view.setFitsSystemWindows(false)
                (view as ViewGroup).clipToPadding = true
            } else {
                setAppBarFitsSystemWindow(view, false)
                view.fitsSystemWindows = true
                (view as ViewGroup).clipToPadding = false
            }
        }
        view.backgroundTintList = null
        if (view is BackgroundTintable) {
            (view as BackgroundTintable).setBackgroundTintResId(0)
        }
        val backgroundFilePath = dataStore.getString(KEY_TRANSLUCENT_THEME_BACKGROUND_PATH)
        if (backgroundFilePath == null) {
            view.setBackgroundColor(Color.BLACK)
            return
        }
        if (useCache && translucentBackground != null &&
            (translucentBackground !is BitmapDrawable || !(translucentBackground as BitmapDrawable).bitmap.isRecycled)
        ) {
            view.background = translucentBackground
            return
        }
        activity.launch {
            val metrics = activity.resources.displayMetrics
            val drawable = activity.loadDrawable(File(backgroundFilePath)) {
                size(metrics.widthPixels, metrics.heightPixels)
                scale(Scale.FILL)
            }
            if (drawable != null) {
                if (useCache) {
                    translucentBackground = drawable
                }
                view.background = drawable
            } else {
                view.setBackgroundColor(Color.BLACK)
            }
        }
    }

    @StyleRes
    private fun getThemeByName(themeName: String): Int {
        return when (themeName.lowercase(Locale.getDefault())) {
            THEME_TRANSLUCENT, THEME_TRANSLUCENT_LIGHT -> R.style.TiebaLite_Translucent_Light
            THEME_TRANSLUCENT_DARK -> R.style.TiebaLite_Translucent_Dark
            THEME_DEFAULT -> R.style.TiebaLite_Tieba
            THEME_BLACK -> R.style.TiebaLite_Black
            THEME_PURPLE -> R.style.TiebaLite_Purple
            THEME_PINK -> R.style.TiebaLite_Pink
            THEME_RED -> R.style.TiebaLite_Red
            THEME_BLUE_DARK -> R.style.TiebaLite_Dark_Blue
            THEME_GREY_DARK -> R.style.TiebaLite_Dark_Grey
            THEME_AMOLED_DARK -> R.style.TiebaLite_Dark_Amoled
            THEME_CUSTOM -> R.style.TiebaLite_Custom
            else -> R.style.TiebaLite_Tieba
        }
    }

    @JvmStatic
    fun getRawTheme(): String {
        val theme = themeState.value
        return when (theme.lowercase(Locale.getDefault())) {
            THEME_TRANSLUCENT,
            THEME_TRANSLUCENT_LIGHT,
            THEME_TRANSLUCENT_DARK,
            THEME_CUSTOM,
            THEME_DEFAULT,
            THEME_BLUE,
            THEME_BLACK,
            THEME_PURPLE,
            THEME_PINK,
            THEME_RED,
            THEME_BLUE_DARK,
            THEME_GREY_DARK,
            THEME_AMOLED_DARK,
            -> theme.lowercase(Locale.getDefault())

            else -> THEME_DEFAULT
        }
    }
}
