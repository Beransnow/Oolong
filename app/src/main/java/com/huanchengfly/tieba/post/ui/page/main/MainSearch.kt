package com.huanchengfly.tieba.post.ui.page.main

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.ContentAlpha
import androidx.compose.material.Icon
import androidx.compose.material.LocalContentColor
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.huanchengfly.tieba.post.R
import com.huanchengfly.tieba.post.ui.common.theme.compose.ExtendedTheme
import com.huanchengfly.tieba.post.ui.common.theme.compose.Motion
import com.huanchengfly.tieba.post.ui.common.theme.compose.PressFeedback
import com.huanchengfly.tieba.post.ui.common.theme.compose.Symbols
import com.huanchengfly.tieba.post.ui.common.theme.compose.pressScale
import com.huanchengfly.tieba.post.ui.navigation.ForumKey
import com.huanchengfly.tieba.post.ui.navigation.Navigator
import com.huanchengfly.tieba.post.ui.navigation.ThreadKey
import com.huanchengfly.tieba.post.ui.navigation.UserProfileKey
import com.huanchengfly.tieba.post.ui.page.search.SearchScreen
import com.huanchengfly.tieba.post.ui.page.search.SearchState
import com.huanchengfly.tieba.post.ui.page.search.SearchViewModel
import com.huanchengfly.tieba.post.ui.widgets.compose.BaseTextField
import com.huanchengfly.tieba.post.ui.widgets.compose.floatingGlass
import kotlin.math.roundToInt

/**
 * Search on the main page, opened from the round button beside the floating bar: the button
 * stretches into a field above the keyboard, and the search page's history and results show over
 * the tabs. Closing shrinks the field back into the button, on the tab it was opened from.
 */
@Stable
class MainSearchState(open: Boolean) {
    var open by mutableStateOf(open)
        private set

    /** 0 the button, 1 the field. */
    val progress = Animatable(if (open) 1f else 0f, visibilityThreshold = Motion.ProgressThreshold)

    /** Where the bar left room for the button, in the root's pixels; empty while it has none. */
    var buttonBounds by mutableStateOf(Rect.Zero)

    val focusRequester = FocusRequester()

    /** Opened by the user, so the field takes the focus and brings up the keyboard once it shows. */
    var wantsFocus by mutableStateOf(false)

    suspend fun open() {
        open = true
        wantsFocus = true
        progress.animateTo(1f, Motion.morph())
    }

    suspend fun close() {
        open = false
        progress.animateTo(0f, Motion.dismiss())
    }
}

@Composable
fun rememberMainSearchState(): MainSearchState {
    var saved by rememberSaveable { mutableStateOf(false) }
    val state = remember { MainSearchState(saved) }
    saved = state.open
    return state
}

/** Room the field takes at the bottom, for the search content to keep clear of. */
@Composable
fun mainSearchPadding(): PaddingValues =
    WindowInsets.ime.union(WindowInsets.navigationBars)
        .add(WindowInsets(bottom = FieldHeight + NavPillMargin * 2))
        .asPaddingValues()

/**
 * The button and the field it stretches into, one piece of glass between the bar's room for it
 * and the bottom of the window above the keyboard. Before the bar for screen readers, so search
 * is found without passing the tabs.
 */
@Composable
fun MainSearchField(
    state: MainSearchState,
    input: String,
    onInputChange: (String) -> Unit,
    onSearch: (String) -> Unit,
    onOpen: () -> Unit,
    onClose: () -> Unit,
) {
    if (state.buttonBounds.isEmpty) return
    var origin by remember { mutableStateOf(Offset.Zero) }
    val colors = ExtendedTheme.colors
    val bottomInsets = WindowInsets.ime.union(WindowInsets.navigationBars)
    val sideInsets = WindowInsets.navigationBars.union(WindowInsets.displayCutout).only(WindowInsetsSides.Horizontal)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { origin = it.positionInRoot() }
    ) {
        // The field's and the close button's places, worked out where they are laid out so the
        // keyboard moving them only lays them out again
        fun Modifier.placedAt(bounds: MeasureScope.(width: Int, height: Int) -> Rect) = layout { measurable, constraints ->
            val rect = bounds(constraints.maxWidth, constraints.maxHeight)
            val placeable = measurable.measure(Constraints.fixed(rect.width.roundToInt().coerceAtLeast(0), rect.height.roundToInt().coerceAtLeast(0)))
            layout(placeable.width, placeable.height) { placeable.place(rect.left.roundToInt(), rect.top.roundToInt()) }
        }
        val margin = NavPillMargin
        Box(
            modifier = Modifier
                .placedAt { width, height ->
                    val m = margin.toPx()
                    val bottom = height - bottomInsets.getBottom(this) - m
                    val field = Rect(
                        left = sideInsets.getLeft(this, layoutDirection) + m,
                        top = bottom - FieldHeight.toPx(),
                        right = width - sideInsets.getRight(this, layoutDirection) - m - FieldHeight.toPx() - CloseGap.toPx(),
                        bottom = bottom,
                    )
                    lerp(state.buttonBounds.translate(-origin), field, state.progress.value)
                }
                .semantics { traversalIndex = -1f }
                .floatingGlass(CapsuleShape)
                .then(
                    if (state.open) {
                        Modifier
                    } else {
                        Modifier.clickable(
                            onClickLabel = stringResource(id = R.string.title_search),
                            role = Role.Button,
                            onClick = onOpen,
                        )
                    }
                ),
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val progress = state.progress.value
                // The icon keeps the same distance from the left as from the top and bottom, so it
                // sits in the middle of the round button and at the start of the field
                val inset = (maxHeight - IconSize) / 2
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxSize().padding(start = inset)) {
                    Icon(
                        imageVector = Symbols.Search,
                        contentDescription = if (state.open) null else stringResource(id = R.string.title_search),
                        tint = colors.text,
                        modifier = Modifier.size(IconSize),
                    )
                    if (progress > 0f) {
                        LaunchedEffect(state.wantsFocus) {
                            if (state.wantsFocus) {
                                state.focusRequester.requestFocus()
                                state.wantsFocus = false
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        BaseTextField(
                            value = input,
                            onValueChange = onInputChange,
                            enabled = state.open,
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { onSearch(input) }),
                            placeholder = {
                                Text(
                                    text = stringResource(id = R.string.hint_search),
                                    color = colors.text.copy(alpha = ContentAlpha.medium),
                                    maxLines = 1,
                                )
                            },
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .graphicsLayer { alpha = ((state.progress.value - 0.4f) / 0.6f).coerceIn(0f, 1f) }
                                .focusRequester(state.focusRequester),
                        )
                        if (input.isNotEmpty() && state.open) {
                            CompositionLocalProvider(LocalContentColor provides colors.textSecondary) {
                                FieldButton(
                                    icon = Symbols.Close,
                                    label = stringResource(id = R.string.button_clear),
                                    onClick = { onInputChange("") },
                                )
                            }
                        }
                    }
                }
            }
        }

        if (state.progress.value > 0f) {
            val interactionSource = remember { MutableInteractionSource() }
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .placedAt { width, height ->
                        val m = margin.toPx()
                        val size = FieldHeight.toPx()
                        val bottom = height - bottomInsets.getBottom(this) - m
                        val right = width - sideInsets.getRight(this, layoutDirection) - m
                        Rect(right - size, bottom - size, right, bottom)
                    }
                    .graphicsLayer {
                        val p = state.progress.value
                        alpha = p
                        scaleX = lerp(0.6f, 1f, p)
                        scaleY = scaleX
                    }
                    .pressScale(interactionSource)
                    .floatingGlass(CapsuleShape)
                    .clickable(
                        interactionSource = interactionSource,
                        indication = PressFeedback.Overlay,
                        role = Role.Button,
                        enabled = state.open,
                        onClick = onClose,
                    ),
            ) {
                Icon(
                    imageVector = Symbols.Close,
                    contentDescription = stringResource(id = R.string.button_close_search),
                    tint = colors.text,
                    modifier = Modifier.size(IconSize),
                )
            }
        }
    }
}

@Composable
private fun FieldButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(FieldHeight)
            .clip(CapsuleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = PressFeedback.Overlay,
                role = Role.Button,
                onClick = onClick,
            ),
    ) {
        Icon(imageVector = icon, contentDescription = label, modifier = Modifier.size(20.dp))
    }
}

private val CapsuleShape = RoundedCornerShape(percent = 50)
private val FieldHeight = 48.dp
private val CloseGap = 8.dp
private val IconSize = 24.dp

/** The search page's history, suggestions and results, for the field at the bottom. */
@Composable
fun MainSearchContent(
    state: SearchState,
    input: String,
    viewModel: SearchViewModel,
    navigator: Navigator,
    onSearch: (String) -> Unit,
) {
    SearchScreen(
        state = state,
        input = input,
        onInputChange = viewModel::onInputChange,
        onSearch = onSearch,
        onBack = {},
        onOpenTab = viewModel::open,
        onRefreshTab = viewModel::refresh,
        onLoadMoreThreads = viewModel::loadMoreThreads,
        onSetOrder = viewModel::setOrder,
        onForget = viewModel::forget,
        onClearHistory = viewModel::clearHistory,
        onOpenForum = { navigator.navigate(ForumKey(it)) },
        onOpenUser = { navigator.navigate(UserProfileKey(it)) },
        onOpenThread = { navigator.navigate(ThreadKey(threadId = it.threadId)) },
        showField = false,
    )
}
