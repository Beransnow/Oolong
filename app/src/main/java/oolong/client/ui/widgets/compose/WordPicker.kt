package oolong.client.ui.widgets.compose

import android.icu.text.BreakIterator
import android.icu.util.ULocale
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.withFrameNanos
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import oolong.client.ui.common.theme.compose.Corners
import oolong.client.ui.common.theme.compose.ExtendedTheme
import oolong.client.ui.common.theme.compose.Motion
import oolong.client.ui.common.theme.compose.TextStyles
import java.util.Locale
import kotlin.math.abs

/**
 * A piece of text in a word picker: a word, which can be picked, or what stands between words. A
 * line break in what stands between starts a new line of chips.
 */
@Immutable
data class WordPiece(
    val text: String,
    val isWord: Boolean,
    val picked: Boolean = false,
    /** Whether it is one character of a word split apart. */
    val split: Boolean = false,
    /** Which piece it is, kept while pieces around it split. */
    val id: Int = 0,
    /** Whether it stays on the line of the piece before it, as punctuation does. */
    val clings: Boolean = false,
    val look: Look = Look.Plain,
) {
    enum class Look {
        Plain,

        /** A link or a mention, kept whole. */
        Link,

        /** An emoticon, written as `#(name)` and kept whole. */
        Emoticon,
    }
}

/**
 * [text] cut into words and what stands between them, with the system's dictionary for languages
 * such as Chinese that don't put spaces between words. With [punctuation], marks and symbols can
 * be picked too, each staying with the piece before it.
 */
fun splitWords(text: String, locale: Locale = Locale.getDefault(), punctuation: Boolean = false): List<WordPiece> {
    val iterator = BreakIterator.getWordInstance(ULocale.forLocale(locale))
    iterator.setText(text)
    val pieces = mutableListOf<WordPiece>()
    var start = iterator.first()
    var end = iterator.next()
    while (end != BreakIterator.DONE) {
        val piece = text.substring(start, end)
        pieces += when {
            iterator.ruleStatus >= BreakIterator.WORD_NONE_LIMIT -> WordPiece(piece, isWord = true)
            punctuation && piece.isNotBlank() -> WordPiece(piece, isWord = true, clings = true)
            else -> WordPiece(piece, isWord = false)
        }
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

    val anyPicked: Boolean get() = pieces.any { it.picked }

    val allPicked: Boolean get() = pieces.any { it.isWord } && pieces.all { !it.isWord || it.picked }

    /**
     * What copying takes: with nothing picked, the whole text. Otherwise the picked pieces in
     * order, keeping what stood between two of them and setting parts further apart off by a
     * space.
     */
    val copied: String
        get() {
            if (!anyPicked) return pieces.joinToString("") { it.text }
            val out = StringBuilder()
            pieces.forEachIndexed { i, piece ->
                when {
                    piece.picked -> out.append(piece.text)
                    !piece.isWord -> {
                        val before = pieces.subList(0, i).lastOrNull { it.isWord }
                        val after = pieces.subList(i + 1, pieces.size).firstOrNull { it.isWord }
                        if (before?.picked == true && after?.picked == true) out.append(piece.text)
                    }
                    out.isNotEmpty() && !out.endsWith(' ') && pieces.subList(i + 1, pieces.size).any { it.picked } -> out.append(' ')
                }
            }
            return out.toString().replace(Spaces, " ").trim()
        }

    fun toggle(index: Int) = set(index..index, !pieces[index].picked)

    fun pickAll(picked: Boolean) = set(pieces.indices, picked)

    /** Picks, or with [picked] false clears, the words in [range]; the other pieces become [others]. */
    fun set(range: IntRange, picked: Boolean, others: List<Boolean>? = null) {
        pieces = pieces.mapIndexed { i, piece ->
            val value = if (i in range && piece.isWord) picked else others?.getOrNull(i) ?: piece.picked
            if (piece.picked == value) piece else piece.copy(picked = value)
        }
    }

    /** Splits the word at [index] into its characters, each picked as the word was; false if it has one or is kept whole. */
    fun split(index: Int): Boolean {
        val piece = pieces[index]
        if (piece.look != WordPiece.Look.Plain) return false
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

private val Spaces = Regex(" +")

/**
 * The words of a text as chips to pick: tap one to pick it, slide from one to pick or clear every
 * word up to the finger, in reading order, and hold one to split it into characters. Slides that
 * start upwards or downwards are left to whatever scrolls the picker; given that [scroll], a slide
 * held near its top or bottom edge scrolls it on.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WordPicker(state: WordPickerState, modifier: Modifier = Modifier, scroll: ScrollState? = null) {
    val haptic = LocalHapticFeedback.current
    val edge = with(LocalDensity.current) { AutoScrollEdge.toPx() }
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
            .pointerInput(state, scroll) {
                coroutineScope {
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
                                fun slideTo(position: Offset) {
                                    val over = at(position) ?: return
                                    if (over == last) return
                                    last = over
                                    state.set(minOf(start, over)..maxOf(start, over), picking, before)
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                                // The finger where the picker shows it, which scrolling moves over the words
                                var finger = down.position - Offset(0f, scroll?.value?.toFloat() ?: 0f)
                                val autoScroll = scroll?.let {
                                    launch {
                                        while (true) {
                                            val height = it.viewportSize.toFloat()
                                            val step = when {
                                                finger.y < edge -> -(edge - finger.y.coerceAtLeast(0f))
                                                finger.y > height - edge -> finger.y.coerceAtMost(height) - (height - edge)
                                                else -> 0f
                                            } * AutoScrollSpeed
                                            if (step != 0f && it.scrollBy(step) != 0f) slideTo(finger + Offset(0f, it.value.toFloat()))
                                            withFrameNanos { }
                                        }
                                    }
                                }
                                while (true) {
                                    val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                                    if (!change.pressed) break
                                    change.consume()
                                    finger = change.position - Offset(0f, scroll?.value?.toFloat() ?: 0f)
                                    slideTo(change.position)
                                }
                                autoScroll?.cancel()
                            }
                        }
                    }
                }
            },
    ) {
        lines(state.pieces).forEach { line ->
            when (line) {
                // A line break takes a line of its own, with nothing on it
                null -> Box(modifier = Modifier.fillMaxWidth())
                else -> key(state.pieces[line.first].id) {
                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        line.forEach { index -> PieceAt(state, index, places, { container }) }
                    }
                }
            }
        }
    }
}

/**
 * The pieces' indices as the picker lays them out: runs that stay on one line, each a word and
 * any marks clinging to it, with null for each line break. What else stands between is left out.
 */
private fun lines(pieces: List<WordPiece>): List<IntRange?> = buildList {
    var run: Int? = null
    fun close(end: Int) {
        run?.let { add(it until end) }
        run = null
    }
    pieces.forEachIndexed { index, piece ->
        when {
            piece.isWord && piece.clings && run != null -> Unit
            piece.isWord || piece.text.isNotBlank() -> {
                close(index)
                run = index
            }
            else -> {
                close(index)
                if ('\n' in piece.text && lastOrNull() != null) add(null)
            }
        }
    }
    close(pieces.size)
}

@Composable
private fun PieceAt(state: WordPickerState, index: Int, places: MutableMap<Int, Rect>, container: () -> LayoutCoordinates?) {
    val piece = state.pieces[index]
    key(piece.id) {
        Piece(
            piece = piece,
            // Out of the text in reading order at first; a split word's characters at once
            appearDelay = if (piece.split) 0L else minOf(index * AppearStagger, AppearStaggerMax),
            modifier = Modifier
                .onPlaced { coordinates -> container()?.let { places[piece.id] = it.localBoundingBoxOf(coordinates) } }
                .semantics {
                    if (piece.isWord) {
                        role = Role.Checkbox
                        selected = piece.picked
                        onClick {
                            state.toggle(state.pieces.indexOfFirst { it.id == piece.id })
                            true
                        }
                    }
                },
        )
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
            piece.look == WordPiece.Look.Link -> colors.accent
            else -> colors.text
        },
        animationSpec = Motion.snap(),
        label = "piece text",
    )
    val lift by animateFloatAsState(if (piece.picked) PickedScale else 1f, Motion.snap(), label = "piece lift")
    val shape = if (piece.split || piece.clings) Corners.xs else Corners.sm
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
                        .padding(horizontal = if (piece.split || piece.clings) 8.dp else 11.dp, vertical = 6.dp)
                } else {
                    Modifier.padding(horizontal = 2.dp, vertical = 6.dp)
                },
            ),
    ) {
        if (piece.look == WordPiece.Look.Emoticon) {
            EmoticonText(text = piece.text, style = TextStyles.bodyCompact, color = content)
        } else {
            Text(text = piece.text, style = TextStyles.bodyCompact, color = content)
        }
    }
}

/** How near its top or bottom edge a slide scrolls the picker, and how fast for each pixel nearer. */
private val AutoScrollEdge = 48.dp
private const val AutoScrollSpeed = 0.25f

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
