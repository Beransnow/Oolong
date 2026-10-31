package com.huanchengfly.tieba.post.testing

import android.content.Context
import android.content.ContextWrapper
import com.huanchengfly.tieba.post.App
import com.huanchengfly.tieba.post.ui.common.theme.utils.ThemeUtils
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.robolectric.RuntimeEnvironment
import java.util.Locale
import java.util.TimeZone

/**
 * Provides what legacy UI code reads from globals ([App.INSTANCE], [App.ScreenInfo], [ThemeUtils])
 * without starting [App] itself, whose `onCreate` opens the database and syncs with the server.
 *
 * Also pins the time zone and locale, which formatted dates in screenshots depend on.
 */
class LegacyAppRule : TestWatcher() {
    private lateinit var timeZone: TimeZone
    private lateinit var locale: Locale

    override fun starting(description: Description) {
        timeZone = TimeZone.getDefault()
        locale = Locale.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"))
        Locale.setDefault(Locale.SIMPLIFIED_CHINESE)

        val context = RuntimeEnvironment.getApplication()
        val app = App()
        ContextWrapper::class.java
            .getDeclaredMethod("attachBaseContext", Context::class.java)
            .apply { isAccessible = true }
            .invoke(app, context)
        App::class.java.getDeclaredField("INSTANCE")
            .apply { isAccessible = true }
            .set(null, app)
        App.ScreenInfo.DENSITY = context.resources.displayMetrics.density
        ThemeUtils.init(App.ThemeDelegate)
    }

    override fun finished(description: Description) {
        TimeZone.setDefault(timeZone)
        Locale.setDefault(locale)
    }
}
