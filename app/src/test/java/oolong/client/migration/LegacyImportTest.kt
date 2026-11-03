package oolong.client.migration

import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LegacyImportTest {
    private val icon = stringPreferencesKey("app_icon")
    private val wallpaper = stringPreferencesKey("translucent_theme_background_path")
    private val words = stringSetPreferencesKey("words")

    @Test
    fun `class names and paths under the old package point at Oolong`() {
        assertEquals("oolong.client.MainActivityV2", LegacyImport.rewritePackage("com.huanchengfly.tieba.post.MainActivityV2"))
        assertEquals("/data/user/0/oolong.client/files/background_1.jpg", LegacyImport.rewritePackage("/data/user/0/com.huanchengfly.tieba.post/files/background_1.jpg"))
    }

    @Test
    fun `imported settings are rewritten and nothing else changes`() = runTest {
        val before = preferencesOf(
            icon to "com.huanchengfly.tieba.post.MainActivityIconInvert",
            wallpaper to "/data/user/0/com.huanchengfly.tieba.post/files/background_1.jpg",
            words to setOf("a", "com.huanchengfly.tieba.post"),
            stringPreferencesKey("other") to "kept",
        )
        assertTrue(LegacyPackageMigration.shouldMigrate(before))
        val after = LegacyPackageMigration.migrate(before)
        assertEquals("oolong.client.MainActivityIconInvert", after[icon])
        assertEquals("/data/user/0/oolong.client/files/background_1.jpg", after[wallpaper])
        assertEquals(setOf("a", "oolong.client"), after[words])
        assertEquals("kept", after[stringPreferencesKey("other")])
        assertFalse(LegacyPackageMigration.shouldMigrate(after))
    }
}
