package oolong.client.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavMetadataKey
import androidx.navigation3.runtime.contains
import androidx.navigation3.runtime.metadata
import androidx.navigation3.scene.OverlayScene
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.scene.SceneStrategyScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import oolong.client.ui.designsystem.adaptive.LocalWindowSizeClass
import oolong.client.ui.designsystem.adaptive.WindowSizeClass
import oolong.client.ui.designsystem.maskTo
import oolong.client.ui.designsystem.theme.ExtendedTheme
import oolong.client.ui.designsystem.theme.scrim
import oolong.core.designsystem.theme.LocalReduceMotion
import oolong.core.designsystem.theme.Motion
import oolong.core.designsystem.theme.Radius
import oolong.core.designsystem.theme.SmoothCornerShape

/**
 * Shows the top entry, when it asks for one, as a sheet of a phone's size in the middle of the
 * window over the pages below, on windows with room around it; elsewhere it is left to the pages.
 * A task that is done with and left, such as logging in, rather than a place to go on from.
 */
class FormSheetSceneStrategy<T : Any>(private val roomy: Boolean, private val onBack: () -> Unit) : SceneStrategy<T> {
    override fun SceneStrategyScope<T>.calculateScene(entries: List<NavEntry<T>>): Scene<T>? {
        val entry = entries.last()
        if (!roomy || FormSheetKey !in entry.metadata) return null
        return FormSheetScene(entry = entry, previousEntries = entries.dropLast(1), onBack = this@FormSheetSceneStrategy.onBack)
    }

    companion object {
        object FormSheetKey : NavMetadataKey<Unit>

        fun formSheet(): Map<String, Any> = metadata { put(FormSheetKey, Unit) }
    }
}

/** Whether [scene] is a form sheet, which dims the whole window, chrome and all. */
fun isFormSheet(scene: Scene<*>): Boolean = scene is FormSheetScene<*>

private class FormSheetScene<T : Any>(
    private val entry: NavEntry<T>,
    override val previousEntries: List<NavEntry<T>>,
    private val onBack: () -> Unit,
) : OverlayScene<T> {
    override val key: Any = entry.contentKey
    override val entries = listOf(entry)
    override val overlaidEntries = previousEntries

    /** In once composed, out once the entry leaves the back stack, before it is let go. */
    private val shown = MutableTransitionState(false)

    override val content: @Composable () -> Unit = {
        SideEffect { if (!shown.targetState && !shown.currentState) shown.targetState = true }
        // NavDisplay's own handler would pop everything above the page under the sheet. The page
        // goes first: its own handler, as a web page's going back, is composed later and wins
        BackHandler(onBack = onBack)
        val transition = rememberTransition(shown, label = "FormSheet")
        val dim by transition.animateFloat(
            transitionSpec = { tween(if (targetState) ScrimInMillis else ScrimOutMillis) },
            label = "dim",
        ) { if (it) 1f else 0f }
        val scrim = ExtendedTheme.colors.scrim
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind { drawRect(scrim, alpha = dim) }
                // Taps beside it don't reach the pages, nor close it, losing what was typed
                .pointerInput(Unit) { detectTapGestures { } }
                .safeDrawingPadding(),
            contentAlignment = Alignment.Center,
        ) {
            // A sheet condenses where it stands, as a centred dialog (design-system.md §9.3)
            val reduced = LocalReduceMotion.current
            AnimatedVisibility(
                visibleState = shown,
                enter = if (reduced) fadeIn(Motion.fadeIn()) else fadeIn(Motion.fadeIn()) + scaleIn(Motion.morph(), initialScale = 0.9f),
                exit = if (reduced) fadeOut(Motion.fadeOut()) else fadeOut(Motion.fadeOut()) + scaleOut(Motion.dismiss(), targetScale = 0.94f),
            ) {
                BoxWithConstraints(modifier = Modifier.padding(SheetMargin)) {
                    val size = DpSize(min(maxWidth, SheetWidth), min(maxHeight, SheetHeight))
                    Box(
                        modifier = Modifier
                            .size(size)
                            .shadow(SheetElevation, SheetShape, clip = false)
                            .clipToBounds()
                            .maskTo(SheetShape)
                            .background(ExtendedTheme.colors.background)
                            // Away from the window's edges, the page has no bars or cutouts to keep clear of
                            .consumeWindowInsets(WindowInsets.safeDrawing),
                    ) {
                        // The page lays itself out as on a phone: the sheet is its window
                        CompositionLocalProvider(LocalWindowSizeClass provides WindowSizeClass.calculateFromSize(size)) {
                            entry.Content()
                        }
                    }
                }
            }
        }
    }

    // The same sheet whenever the scenes are worked out again; the one first shown keeps its motion
    override fun equals(other: Any?): Boolean =
        other is FormSheetScene<*> && other.key == key && other.previousEntries == previousEntries

    override fun hashCode(): Int = 31 * key.hashCode() + previousEntries.hashCode()

    override suspend fun onRemove() {
        shown.targetState = false
        withTimeoutOrNull(HideTimeoutMillis) { snapshotFlow { shown.isIdle }.first { it } }
    }

    private companion object {
        const val HideTimeoutMillis = 1_000L
        const val ScrimInMillis = 150
        const val ScrimOutMillis = 120
    }
}

/** About a phone's screen, so a page made for one, as the web's login, fits it as it is. */
private val SheetWidth = 480.dp
private val SheetHeight = 760.dp
private val SheetMargin = 24.dp
private val SheetElevation = 24.dp
private val SheetShape = SmoothCornerShape(Radius.xl)
