package oolong.client.migration

import androidx.datastore.core.DataMigration
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.mutablePreferencesOf

/**
 * Settings brought over from the old app name it in a few values: the launcher icon chosen is a
 * class name under its package, the translucent theme's wallpaper a path in its data folder.
 * This points them at Oolong's. Without such values it does nothing.
 */
object LegacyPackageMigration : DataMigration<Preferences> {
    override suspend fun shouldMigrate(currentData: Preferences): Boolean =
        currentData.asMap().values.any { value -> value.texts().any { LegacyImport.PACKAGE in it } }

    override suspend fun migrate(currentData: Preferences): Preferences {
        val out = mutablePreferencesOf()
        for ((key, value) in currentData.asMap()) {
            @Suppress("UNCHECKED_CAST")
            when (value) {
                is String -> out[key as Preferences.Key<String>] = LegacyImport.rewritePackage(value)
                is Set<*> -> out[key as Preferences.Key<Set<String>>] = value.map { LegacyImport.rewritePackage(it as String) }.toSet()
                else -> out[key as Preferences.Key<Any>] = value
            }
        }
        return out
    }

    override suspend fun cleanUp() {}

    private fun Any.texts(): List<String> = when (this) {
        is String -> listOf(this)
        is Set<*> -> filterIsInstance<String>()
        else -> emptyList()
    }
}
