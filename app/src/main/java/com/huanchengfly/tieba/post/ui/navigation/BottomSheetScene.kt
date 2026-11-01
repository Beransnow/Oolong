package com.huanchengfly.tieba.post.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.ModalBottomSheetLayout
import androidx.compose.material.ModalBottomSheetValue
import androidx.compose.material.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavMetadataKey
import androidx.navigation3.runtime.contains
import androidx.navigation3.runtime.metadata
import androidx.navigation3.scene.OverlayScene
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.scene.SceneStrategyScope
import com.huanchengfly.tieba.post.ui.common.theme.compose.ExtendedTheme
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/** Shows the top entry in a modal bottom sheet over the pages below, when it asks for one. */
class BottomSheetSceneStrategy<T : Any> : SceneStrategy<T> {
    override fun SceneStrategyScope<T>.calculateScene(entries: List<NavEntry<T>>): Scene<T>? {
        val entry = entries.last()
        if (BottomSheetKey !in entry.metadata) return null
        return BottomSheetScene(entry = entry, previousEntries = entries.dropLast(1), onBack = onBack)
    }

    companion object {
        object BottomSheetKey : NavMetadataKey<Unit>

        fun bottomSheet(): Map<String, Any> = metadata { put(BottomSheetKey, Unit) }
    }
}

private class BottomSheetScene<T : Any>(
    private val entry: NavEntry<T>,
    override val previousEntries: List<NavEntry<T>>,
    private val onBack: () -> Unit,
) : OverlayScene<T> {
    override val key: Any = entry.contentKey
    override val entries = listOf(entry)
    override val overlaidEntries = previousEntries

    /** Completed when the entry leaves the back stack, so the sheet slides away first. */
    private val removed = CompletableDeferred<Unit>()
    private val hidden = CompletableDeferred<Unit>()

    @OptIn(ExperimentalMaterialApi::class)
    override val content: @Composable () -> Unit = {
        val state = rememberModalBottomSheetState(
            ModalBottomSheetValue.Hidden,
            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
            skipHalfExpanded = true,
        )
        LaunchedEffect(state) {
            state.show()
            // Swiped down or the scrim tapped: leave the page as back would
            snapshotFlow { state.currentValue }.first { it == ModalBottomSheetValue.Hidden }
            if (!removed.isCompleted) onBack()
        }
        // NavDisplay's own handler would pop everything above the page under the sheet
        BackHandler(onBack = onBack)
        LaunchedEffect(state) {
            removed.await()
            state.hide()
            hidden.complete(Unit)
        }
        ModalBottomSheetLayout(
            sheetContent = { entry.Content() },
            sheetState = state,
            sheetShape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp),
            sheetBackgroundColor = ExtendedTheme.colors.sheet,
            scrimColor = Color.Black.copy(alpha = 0.32f),
            content = {},
        )
    }

    override suspend fun onRemove() {
        removed.complete(Unit)
        withTimeoutOrNull(HIDE_TIMEOUT_MILLIS) { hidden.await() }
    }

    private companion object {
        const val HIDE_TIMEOUT_MILLIS = 1_000L
    }
}
