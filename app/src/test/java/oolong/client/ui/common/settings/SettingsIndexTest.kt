package oolong.client.ui.common.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import oolong.client.ui.page.settings.AllSettingsPages
import oolong.client.ui.page.settings.SettingsHub
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class SettingsIndexTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val index = settingsIndex(SettingsHub, AllSettingsPages, SettingsEnvironment(context, signedIn = true), context.resources)

    @Test
    fun `every declared page is reached from the hub`() {
        val reached = index.map { it.page }.toSet() + SettingsHub.key
        assertEquals(emptyList(), AllSettingsPages.map { it.key }.filterNot { it in reached })
    }

    @Test
    fun `a settings page linked to from another is declared, so search goes into it`() {
        val declared = AllSettingsPages.map { it.key }.toSet()
        val linked = AllSettingsPages.flatMap { page ->
            page.groups.flatMap { group -> group.items.filterIsInstance<SettingsRow<*>>().mapNotNull { it.target } }
        }
        val undeclared = linked.filter { it::class.simpleName.orEmpty().endsWith("SettingsKey") && it !in declared }
        assertEquals(emptyList(), undeclared)
    }

    @Test
    fun `a setting is found by its name, by another word, and says where it is`() {
        val dim = index.search("夜间").first()
        assertEquals("夜间模式下压暗图片", dim.title)
        assertEquals(listOf("浏览"), dim.path)
        assertTrue(index.search("清除缓存").any { it.title == "存储空间" })
    }

    @Test
    fun `what search leaves out isn't found`() {
        assertTrue(index.none { it.title == "上次签到" })
        assertTrue(index.search("测试自动签到").isEmpty())
    }

    @Test
    fun `rows that don't exist here aren't found`() {
        val signedOut = settingsIndex(SettingsHub, AllSettingsPages, SettingsEnvironment(context, signedIn = false), context.resources)
        assertTrue(signedOut.none { it.title == "隐私" })
    }
}
