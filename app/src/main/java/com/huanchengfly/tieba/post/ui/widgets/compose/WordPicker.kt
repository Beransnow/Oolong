package com.huanchengfly.tieba.post.ui.widgets.compose

import android.icu.text.BreakIterator
import android.icu.util.ULocale
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.huanchengfly.tieba.post.ui.common.theme.compose.ExtendedTheme
import com.huanchengfly.tieba.post.ui.common.theme.compose.Motion
import com.huanchengfly.tieba.post.ui.common.theme.compose.TextStyles
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.math.abs

/** A piece of text in a word picker: a word, which can be picked, or what stands between words. */
@Immutable
data class WordPiece(
    val text: String,
    val isWord: Boolean,
    val picked: Boolean = false,
    /** Whether it is one character of a word split apart. */
    val split: Boolean = false,
    /** Which piece it is, kept while pieces around it split. */
    val id: Int = 0,
)

/**
 * [text] cut into words and what stands between them, with the system's dictionary for languages
 * such as Chinese that don't put spaces between words.
 */
fun splitWords(text: String, locale: Locale = Locale.getDefault()): List<WordPiece> {
    val iterator = BreakIterator.getWordInstance(ULocale.forLocale(locale))
    iterator.setText(text)
    val pieces = mutableListOf<WordPiece>()
    var start = iterator.first()
    var end = iterator.next()
    while (end != BreakIterator.DONE) {
        pieces += WordPiece(text.substring(start, end), isWord = iterator.ruleStatus >= BreakIterator.WORD_NONE_LIMIT)
        start = end
        end = iterator.next()
    }
    return pieces
}

/** The pieces of a word picker and which are picked. */
@Stable
class WordPickerState(pieces: List<WordPiece>) {
    var pieces by mutableStateOf(pieces.mapIndexed { i, piece -> piece.copy(id = i) })
        private set

    private var nextId = pieces.size

    /** The picked pieces as words: pieces picked next to each other make one, as 剧 and 透 make 剧透. */
    val words: List<String>
        get() = buildList {
            var current: StringBuilder? = null
            pieces.forEach { piece ->
                current = if (piece.picked) {
                    (current ?: StringBuilder().also { add(it) }).append(piece.text)
                } else {
                    null
                }
            }
        }.map { it.toString() }

    fun toggle(index: Int) = set(index..index, !pieces[index].picked)

    /** Picks, or with [picked] false clears, the words in [range]; the other pieces become [others]. */
    fun set(range: IntRange, picked: Boolean, others: List<Boolean>? = null) {
        pieces = pieces.mapIndexed { i, piece ->
            val value = if (i in range && piece.isWord) picked else others?.getOrNull(i) ?: piece.picked
            if (piece.picked == value) piece else piece.copy(picked = value)
        }
    }

    /** Splits the word at [index] into its characters, each picked as the word was; false if it has one. */
    fun split(index: Int): Boolean {
        val piece = pieces[index]
        val characters = piece.text.codePoints().toArray().map { String(Character.toChars(it)) }
        if (characters.size < 2) return false
        pieces = pieces.subList(0, index) +
            characters.map { WordPiece(it, isWord = true, picked = piece.picked, split = true, id = nextId++) } +
            pieces.subList(index + 1, pieces.size)
        return true
    }
}

@Composable
fun rememberWordPickerState(text: String): WordPickerState = remember(text) { WordPickerState(splitWords(text)) }

/**
 * The words of a text as chips to pick: tap one to pick it, slide from one to pick or clear every
 * word up to the finger, in reading order, and hold one to split it into characters. Slides that
 * start upwards or downwards are left to whatever scrolls the picker.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WordPicker(state: WordPickerState, modifier: Modifier = Modifier) {
    val colors = ExtendedTheme.colors
    val haptic = LocalHapticFeedback.current
    // Where each piece is, to find the one under the finger
    val places = remember { mutableStateMapOf<Int, Rect>() }
    var container by remember { mutableStateOf<LayoutCoordinates?>(null) }

    // By piece id, so splitting one leaves no place behind; found as the piece's index
    fun at(position: Offset): Int? =
        state.pieces.indexOfFirst { piece -> piece.isWord && places[piece.id]?.contains(position) == true }.takeIf { it >= 0 }

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier
            .onPlaced { container = it }
            .pointerInput(state) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val start = at(down.position) ?: return@awaitEachGesture
                    val picking = !state.pieces[start].picked
                    val before = state.pieces.map { it.picked }
                    when (withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) { awaitStart(down.id, down.position, viewConfiguration.touchSlop) }) {
                        // Held still: the word splits
                        null -> if (state.split(start)) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        GestureStart.Lift -> state.toggle(start)
                        GestureStart.Scroll -> Unit
                        GestureStart.Slide -> {
                            state.set(start..start, picking, before)
                            var last = start
                            while (true) {
                                val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) break
                                change.consume()
                                val over = at(change.position) ?: continue
                                if (over == last) continue
                                last = over
                                state.set(minOf(start, over)..maxOf(start, over), picking, before)
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                        }
                    }
                }
            },
    ) {
        state.pieces.forEachIndexed { index, piece ->
            if (piece.text.isBlank()) return@forEachIndexed
            key(piece.id) {
                Piece(
                    piece = piece,
                    // Out of the text in reading order at first; a split word's characters at once
                    appearDelay = if (piece.split) 0L else minOf(index * AppearStagger, AppearStaggerMax),
                    modifier = Modifier
                        .onPlaced { coordinates -> container?.let { places[piece.id] = it.localBoundingBoxOf(coordinates) } }
                        .semantics {
                            if (piece.isWord) {
                                role = Role.Checkbox
                                selected = piece.picked
                                onClick {
                                    state.toggle(index)
                                    true
                                }
                            }
                        },
                )
            }
        }
    }
}

/**
 * One piece: a word as a chip, which turns over and rises a little when picked, or what stands
 * between words as quiet text. Each comes in from blurred and small (design-system.md §7.2).
 */
@Composable
private fun Piece(piece: WordPiece, appearDelay: Long, modifier: Modifier = Modifier) {
    val colors = ExtendedTheme.colors
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(appearDelay)
        appear.animateTo(1f, Motion.morph(Motion.ProgressThreshold))
    }
    val background by animateColorAsState(
        targetValue = if (piece.picked) colors.invertChip else colors.chip,
        animationSpec = Motion.snap(),
        label = "piece",
    )
    val content by animateColorAsState(
        targetValue = when {
            !piece.isWord -> colors.textDisabled
            piece.picked -> colors.onInvertChip
            else -> colors.text
        },
        animationSpec = Motion.snap(),
        label = "piece text",
    )
    val lift by animateFloatAsState(if (piece.picked) PickedScale else 1f, Motion.snap(), label = "piece lift")
    val shape = RoundedCornerShape(if (piece.split) 6.dp else 10.dp)
    Box(
        modifier = modifier
            .graphicsLayer {
                val a = appear.value
                val scale = lerp(AppearScale, 1f, a) * lift
                scaleX = scale
                scaleY = scale
                alpha = (a / 0.3f).coerceIn(0f, 1f)
                val blur = AppearBlur.toPx() * (1f - a).coerceIn(0f, 1f)
                renderEffect = if (blur > 0.5f) BlurEffect(blur, blur) else null
            }
            .then(
                if (piece.isWord) {
                    Modifier
                        .clip(shape)
                        .background(background)
                        .padding(horizontal = if (piece.split) 8.dp else 11.dp, vertical = 6.dp)
                } else {
                    Modifier.padding(horizontal = 2.dp, vertical = 6.dp)
                },
            ),
    ) {
        Text(text = piece.text, style = TextStyles.bodyCompact, color = content)
    }
}

private const val AppearStagger = 14L
private const val AppearStaggerMax = 280L
private const val AppearScale = 0.6f
private const val PickedScale = 1.06f
private val AppearBlur = 8.dp

private enum class GestureStart { Lift, Scroll, Slide }

/**
 * How the finger [id] that went down at [from] starts: lifting, or moving past [slop] mostly up
 * or down, which is left to scrolling, or across, which slides over words and is kept.
 */
private suspend fun AwaitPointerEventScope.awaitStart(id: PointerId, from: Offset, slop: Float): GestureStart {
    while (true) {
        val change = awaitPointerEvent().changes.firstOrNull { it.id == id } ?: return GestureStart.Lift
        if (!change.pressed) return GestureStart.Lift
        val moved = change.position - from
        if (moved.getDistance() > slop) {
            if (abs(moved.y) > abs(moved.x)) return GestureStart.Scroll
            change.consume()
            return GestureStart.Slide
        }
    }
}
