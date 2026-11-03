package oolong.client.ui.common.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.rememberLifecycleOwner
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.scene.OverlayScene
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.scene.SceneStrategyScope

/**
 * The scenes a back stack shows as: the one on top, those under it, and any overlays above, as
 * Navigation 3's `SceneState`. Calculated here rather than by Navigation 3, as its calculation
 * wraps each entry in a way only its own display can serve ([PageDisplay] does the same).
 */
@Immutable
class PageScenes<T : Any>(
    val entries: List<NavEntry<T>>,
    /** The overlays, top first. */
    val overlayScenes: List<OverlayScene<T>>,
    /** The scene on top, under any overlays. */
    val currentScene: Scene<T>,
    /** The scenes under [currentScene], bottom first. */
    val previousScenes: List<Scene<T>>,
)

/**
 * Calculates the [PageScenes] for [entries], trying [strategies] in order for each scene; the
 * last strategy must always give one. Scenes are recalculated only when the entries change.
 */
@Composable
fun <T : Any> rememberPageScenes(
    entries: List<NavEntry<T>>,
    strategies: List<SceneStrategy<T>>,
): PageScenes<T> {
    val setup = remember { SceneSetupDecorator<T>() }
    val lifecycle = rememberBackStackLifecycleDecorator(entries)
    val decorated = rememberDecoratedNavEntries(entries, listOf(setup, lifecycle))
    return remember(strategies, decorated) {
        val scope = SceneStrategyScope<T>()
        fun sceneFor(entries: List<NavEntry<T>>): Scene<T> =
            strategies.firstNotNullOfOrNull { with(it) { scope.calculateScene(entries) } }
                ?: error("No scene strategy took ${entries.map { it.contentKey }}")

        // Overlays first, down to the scene they sit over
        val overlays = mutableListOf<OverlayScene<T>>()
        var scene = sceneFor(decorated)
        while (scene is OverlayScene<T>) {
            overlays += scene
            scene = sceneFor(scene.overlaidEntries.also { require(it.isNotEmpty()) { "$scene overlays nothing" } })
        }
        // Then the scenes under it, down to the bottom of the stack
        val previous = ArrayDeque<Scene<T>>()
        var under = scene.previousEntries
        while (under.isNotEmpty()) {
            val previousScene = sceneFor(under)
            previous.addFirst(previousScene)
            under = previousScene.previousEntries
        }
        PageScenes(decorated, overlays, scene, previous.toList())
    }
}

/**
 * The entries another scene already shows, so the scene being drawn leaves them out: while two
 * scenes are on screen, as one turns into the other, a page in both is drawn only once.
 */
internal val LocalEntriesShownElsewhere: ProvidableCompositionLocal<Set<Any>> = compositionLocalOf { emptySet() }

/**
 * Puts each entry's content in a movable slot, so it keeps its state as it moves between scenes,
 * and leaves it out where [LocalEntriesShownElsewhere] says another scene shows it.
 */
private class SceneSetupDecorator<T : Any>(
    val slots: MutableMap<Any, @Composable (@Composable () -> Unit) -> Unit> = mutableStateMapOf(),
) : NavEntryDecorator<T>(
    onPop = { contentKey -> slots.remove(contentKey) },
    decorate = { entry ->
        val contentKey = entry.contentKey
        if (contentKey !in LocalEntriesShownElsewhere.current) {
            key(contentKey) {
                // Remembered here, so a page still sliding away keeps its slot after it left the stack
                val slot = remember { slots.getOrPut(contentKey) { movableContentOf { content -> content() } } }
                slot { entry.Content() }
            }
        }
    },
)

/** Caps the lifecycle of an entry that has left the back stack at created, while it still shows. */
@Composable
private fun <T : Any> rememberBackStackLifecycleDecorator(entries: List<NavEntry<T>>): NavEntryDecorator<T> {
    val current by rememberUpdatedState(entries)
    return remember {
        NavEntryDecorator { entry ->
            val inStack = current.any { it.contentKey == entry.contentKey }
            val owner = rememberLifecycleOwner(maxLifecycle = if (inStack) Lifecycle.State.RESUMED else Lifecycle.State.CREATED)
            CompositionLocalProvider(LocalLifecycleOwner provides owner) { entry.Content() }
        }
    }
}
