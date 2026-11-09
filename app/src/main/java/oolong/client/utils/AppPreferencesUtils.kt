package oolong.client.utils

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import oolong.client.dataStore
import oolong.client.getBoolean
import oolong.client.getFloat
import oolong.client.getInt
import oolong.client.getLong
import oolong.client.getString
import oolong.client.utils.ThemeUtil.TRANSLUCENT_THEME_LIGHT
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

open class AppPreferencesUtils private constructor(ctx: Context) {
    companion object {
        /** The key of [usageStats], which the settings switch writes too. */
        const val UsageStatsKey = "usage_stats"

        private var instance: AppPreferencesUtils? = null

        fun getInstance(context: Context): AppPreferencesUtils {
            return instance ?: AppPreferencesUtils(context).also {
                instance = it
            }
        }
    }

    /** The application context, which lives as long as the process, so holding it leaks nothing. */
    private val context: Context = ctx.applicationContext

    private val preferencesDataStore: DataStore<Preferences>
        get() = context.dataStore

    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    var userLikeLastRequestUnix by DataStoreDelegates.long(defaultValue = 0L)

    var ignoreBatteryOptimizationsDialog by DataStoreDelegates.boolean(defaultValue = false)

    /** Whether a forum's rules and pinned threads are folded into one row, in every forum. */
    var forumTopsFolded by DataStoreDelegates.boolean(defaultValue = false, key = "forum_tops_folded")

    /** Whether the first-launch landing page has been seen through. */
    var landingShown by DataStoreDelegates.boolean(defaultValue = false, key = "landing_shown")

    /** Whether usage statistics are sent (see UsageStats); asked on first launch, or once after an update. */
    var usageStats by DataStoreDelegates.boolean(defaultValue = false, key = UsageStatsKey)

    var usageStatsAsked by DataStoreDelegates.boolean(defaultValue = false, key = "usage_stats_asked")

    var appIcon by DataStoreDelegates.string(
        defaultValue = LauncherIcons.DEFAULT_ICON,
        key = AppIconUtil.PREF_KEY_APP_ICON
    )

    var useThemedIcon by DataStoreDelegates.boolean(defaultValue = false)

    var autoSign by DataStoreDelegates.boolean(defaultValue = false, key = "auto_sign")

    var autoSignTime by DataStoreDelegates.string(
        defaultValue = "09:00",
        key = "auto_sign_time"
    )

    var blockVideo by DataStoreDelegates.boolean(defaultValue = false)

    /** Whether a Tieba link copied elsewhere is offered when the app comes to the front. */
    var clipboardLinks by DataStoreDelegates.boolean(defaultValue = true, key = "clipboard_links")

    var collectThreadSeeLz by DataStoreDelegates.boolean(
        defaultValue = true,
        key = "collect_thread_see_lz"
    )

    var collectThreadDescSort by DataStoreDelegates.boolean(
        defaultValue = false,
        key = "collect_thread_desc_sort"
    )

    var customPrimaryColor by DataStoreDelegates.string(key = "custom_primary_color")

    var defaultSortType by DataStoreDelegates.string(
        key = "default_sort_type",
        defaultValue = "0"
    )

    var doNotUsePhotoPicker by DataStoreDelegates.boolean(defaultValue = false)

    /** Whether motion is reduced; read once at start, before the setting's flow arrives. */
    var reduceMotion by DataStoreDelegates.boolean(defaultValue = false, key = "reduceMotion")

    var forumFabFunction by DataStoreDelegates.string(defaultValue = "hide")

    /** The name of a [oolong.client.ui.page.thread.SubPostStyle]. */
    var subPostStyle by DataStoreDelegates.string(defaultValue = "Timeline", key = "subPostStyle")

    var subPostsCollapsed by DataStoreDelegates.boolean(defaultValue = false, key = "subPostsCollapsed")

    var hideBlockedContent by DataStoreDelegates.boolean(defaultValue = false)

    var hideExplore by DataStoreDelegates.boolean(defaultValue = false)

    /** Whether the feeds load anew when the app opens, rather than showing what was last loaded. */
    var feedAutoRefresh by DataStoreDelegates.boolean(defaultValue = true)

    var hideMedia by DataStoreDelegates.boolean(defaultValue = false)

    var hideReply by DataStoreDelegates.boolean(defaultValue = false)

    var homePageShowHistoryForum by DataStoreDelegates.boolean(defaultValue = true)

    var imageDarkenWhenNightMode by DataStoreDelegates.boolean(defaultValue = true)

    /** Whether a playing video shrinks into a picture-in-picture window when the app is left. */
    var videoAutoPip by DataStoreDelegates.boolean(defaultValue = true)

    var imageLoadType by DataStoreDelegates.string(
        key = "image_load_type",
        defaultValue = "0"
    )

    var imeHeight by DataStoreDelegates.int(defaultValue = 800)

    var listItemsBackgroundIntermixed by DataStoreDelegates.boolean(defaultValue = true)

    var listSingle by DataStoreDelegates.boolean(defaultValue = false)

    var littleTail by DataStoreDelegates.string(key = "little_tail")

    var oldTheme by DataStoreDelegates.string(key = "old_theme")

    var oksignSlowMode by DataStoreDelegates.boolean(
        defaultValue = true,
        key = "oksign_slow_mode"
    )

    var oksignUseOfficialOksign by DataStoreDelegates.boolean(
        defaultValue = true,
        key = "oksign_use_official_oksign"
    )

    var picWatermarkType by DataStoreDelegates.string(
        defaultValue = "2",
        key = "pic_watermark_type",
    )

    var postOrReplyWarning by DataStoreDelegates.boolean(defaultValue = true)

    var radius by DataStoreDelegates.int(defaultValue = 8)

    var signDay by DataStoreDelegates.int(defaultValue = -1, key = "sign_day")

    var subPostSort by DataStoreDelegates.string(defaultValue = "hot", key = "subPostSort")

    var hideIpLocation by DataStoreDelegates.boolean(defaultValue = false, key = "hideIpLocation")

    var showBothUsernameAndNickname by DataStoreDelegates.boolean(
        defaultValue = false,
        key = "show_both_username_and_nickname"
    )

    var theme by DataStoreDelegates.string(defaultValue = ThemeUtil.THEME_DEFAULT)

    var translucentBackgroundAlpha by DataStoreDelegates.int(
        defaultValue = 255,
        key = "translucent_background_alpha"
    )

    var translucentBackgroundBlur by DataStoreDelegates.int(key = "translucent_background_blur")

    var translucentBackgroundTheme by DataStoreDelegates.int(
        defaultValue = TRANSLUCENT_THEME_LIGHT,
        key = "translucent_background_theme"
    )

    var translucentThemeBackgroundPath by DataStoreDelegates.string(key = "translucent_theme_background_path")

    var translucentPrimaryColor by DataStoreDelegates.string(key = "translucent_primary_color")

    var useCustomTabs by DataStoreDelegates.boolean(
        defaultValue = true,
        key = "use_custom_tabs"
    )

    var useWebView by DataStoreDelegates.boolean(defaultValue = true, key = "use_webview")

    private object DataStoreDelegates {
        fun int(
            defaultValue: Int = 0,
            key: String? = null
        ) = object : ReadWriteProperty<AppPreferencesUtils, Int> {
            private var prefValue = defaultValue
            private var initialized = false

            override fun getValue(thisRef: AppPreferencesUtils, property: KProperty<*>): Int {
                val finalKey = key ?: property.name
                if (!initialized) {
                    initialized = true
                    prefValue = thisRef.preferencesDataStore.getInt(finalKey, defaultValue)
                    thisRef.coroutineScope.launch {
                        thisRef.preferencesDataStore.data
                            .map { it[intPreferencesKey(finalKey)] }
                            .distinctUntilChanged()
                            .collect {
                                prefValue = it ?: defaultValue
                            }
                    }
                }
                return prefValue
            }

            override fun setValue(
                thisRef: AppPreferencesUtils,
                property: KProperty<*>,
                value: Int
            ) {
                prefValue = value
                thisRef.coroutineScope.launch {
                    thisRef.preferencesDataStore.edit {
                        it[intPreferencesKey(key ?: property.name)] = value
                    }
                }
            }
        }

        fun string(
            defaultValue: String? = null,
            key: String? = null
        ) = object : ReadWriteProperty<AppPreferencesUtils, String?> {
            private var prefValue = defaultValue
            private var initialized = false

            override fun getValue(thisRef: AppPreferencesUtils, property: KProperty<*>): String? {
                val finalKey = key ?: property.name
                if (!initialized) {
                    initialized = true
                    prefValue = thisRef.preferencesDataStore.getString(finalKey)
                        ?: defaultValue
                    thisRef.coroutineScope.launch {
                        thisRef.preferencesDataStore.data
                            .map { it[stringPreferencesKey(finalKey)] }
                            .distinctUntilChanged()
                            .collect {
                                prefValue = it ?: defaultValue
                            }
                    }
                }
                return prefValue
            }

            override fun setValue(
                thisRef: AppPreferencesUtils,
                property: KProperty<*>,
                value: String?
            ) {
                prefValue = value
                thisRef.coroutineScope.launch {
                    thisRef.preferencesDataStore.edit {
                        if (value == null) {
                            it.remove(stringPreferencesKey(key ?: property.name))
                        } else {
                            it[stringPreferencesKey(key ?: property.name)] = value
                        }
                    }
                }
            }
        }

        fun float(
            defaultValue: Float = 0F,
            key: String? = null
        ) = object : ReadWriteProperty<AppPreferencesUtils, Float> {
            private var prefValue = defaultValue
            private var initialized = false

            override fun getValue(thisRef: AppPreferencesUtils, property: KProperty<*>): Float {
                val finalKey = key ?: property.name
                if (!initialized) {
                    initialized = true
                    prefValue =
                        thisRef.preferencesDataStore.getFloat(finalKey, defaultValue)
                    thisRef.coroutineScope.launch {
                        thisRef.preferencesDataStore.data
                            .map { it[floatPreferencesKey(finalKey)] }
                            .distinctUntilChanged()
                            .collect {
                                prefValue = it ?: defaultValue
                            }
                    }
                }
                return prefValue
            }

            override fun setValue(
                thisRef: AppPreferencesUtils,
                property: KProperty<*>,
                value: Float
            ) {
                prefValue = value
                thisRef.coroutineScope.launch {
                    thisRef.preferencesDataStore.edit {
                        it[floatPreferencesKey(key ?: property.name)] = value
                    }
                }
            }
        }

        fun long(
            defaultValue: Long = 0L,
            key: String? = null
        ) = object : ReadWriteProperty<AppPreferencesUtils, Long> {
            private var prefValue = defaultValue
            private var initialized = false

            override fun getValue(thisRef: AppPreferencesUtils, property: KProperty<*>): Long {
                val finalKey = key ?: property.name
                if (!initialized) {
                    initialized = true
                    prefValue =
                        thisRef.preferencesDataStore.getLong(finalKey, defaultValue)
                    thisRef.coroutineScope.launch {
                        thisRef.preferencesDataStore.data
                            .map { it[longPreferencesKey(finalKey)] }
                            .distinctUntilChanged()
                            .collect {
                                prefValue = it ?: defaultValue
                            }
                    }
                }
                return prefValue
            }

            override fun setValue(
                thisRef: AppPreferencesUtils,
                property: KProperty<*>,
                value: Long
            ) {
                prefValue = value
                thisRef.coroutineScope.launch {
                    thisRef.preferencesDataStore.edit {
                        it[longPreferencesKey(key ?: property.name)] = value
                    }
                }
            }
        }

        fun boolean(
            defaultValue: Boolean = false,
            key: String? = null
        ) = object : ReadWriteProperty<AppPreferencesUtils, Boolean> {
            private var prefValue = defaultValue
            private var initialized = false

            override fun getValue(thisRef: AppPreferencesUtils, property: KProperty<*>): Boolean {
                val finalKey = key ?: property.name
                if (!initialized) {
                    initialized = true
                    prefValue =
                        thisRef.preferencesDataStore.getBoolean(finalKey, defaultValue)
                    thisRef.coroutineScope.launch {
                        thisRef.preferencesDataStore.data
                            .map { it[booleanPreferencesKey(finalKey)] }
                            .distinctUntilChanged()
                            .collect {
                                prefValue = it ?: defaultValue
                            }
                    }
                }
                return prefValue
            }

            override fun setValue(
                thisRef: AppPreferencesUtils,
                property: KProperty<*>,
                value: Boolean
            ) {
                prefValue = value
                thisRef.coroutineScope.launch {
                    thisRef.preferencesDataStore.edit {
                        it[booleanPreferencesKey(key ?: property.name)] = value
                    }
                }
            }
        }
    }
}

val Context.appPreferences: AppPreferencesUtils
    get() = AppPreferencesUtils.getInstance(this)
