package oolong.client.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.rememberLifecycleOwner
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavMetadataKey
import androidx.navigation3.runtime.get
import androidx.navigation3.runtime.metadata
import androidx.navigation3.scene.OverlayScene
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.scene.SceneStrategyScope

/**
 * Shows the top entry in a dialog window over the pages below, when it asks for one, leaving
 * through [onBack] when dismissed. As Navigation 3's own, which takes back from the scene scope
 * only its own display can fill in.
 */
class DialogSceneStrategy<T : Any>(private val onBack: () -> Unit) : SceneStrategy<T> {
    override fun SceneStrategyScope<T>.calculateScene(entries: List<NavEntry<T>>): Scene<T>? {
        val entry = entries.last()
        val properties = entry.metadata[DialogKey] ?: return null
        return DialogScene(entry, previousEntries = entries.dropLast(1), properties, this@DialogSceneStrategy.onBack)
    }

    companion object {
        object DialogKey : NavMetadataKey<DialogProperties>

        fun dialog(properties: DialogProperties = DialogProperties()): Map<String, Any> =
            metadata { put(DialogKey, properties) }
    }
}

private data class DialogScene<T : Any>(
    private val entry: NavEntry<T>,
    override val previousEntries: List<NavEntry<T>>,
    private val properties: DialogProperties,
    private val onBack: () -> Unit,
) : OverlayScene<T> {
    override val key: Any = entry.contentKey
    override val entries: List<NavEntry<T>> = listOf(entry)
    override val overlaidEntries: List<NavEntry<T>> = previousEntries

    override val content: @Composable () -> Unit = {
        val lifecycleOwner = rememberLifecycleOwner()
        Dialog(onDismissRequest = onBack, properties = properties) {
            CompositionLocalProvider(LocalLifecycleOwner provides lifecycleOwner) { entry.Content() }
        }
    }
}
