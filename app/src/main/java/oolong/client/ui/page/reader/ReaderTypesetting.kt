package oolong.client.ui.page.reader

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextIndent

/**
 * What the text needs from the reader beyond the platform's own typesetting. Some systems already
 * space Chinese from Latin letters and digits (HyperOS's text layout setting does), so this is
 * measured rather than assumed, and the reader only adds what is missing.
 */
@Immutable
data class Typesetting(
    /** How far one Chinese character advances, in pixels, spacing included. */
    val advance: Float,
    /** Whether the reader puts the space between Chinese and Latin text itself. */
    val addSpace: Boolean,
)

/** Measures [style] once for the reader: a character's advance, and whether the platform spaces mixed text. */
@Composable
fun rememberTypesetting(style: TextStyle): Typesetting {
    val measurer = rememberTextMeasurer()
    return remember(style, measurer) {
        // Without the first-line indent, which would count as part of the text
        val plain = style.copy(textIndent = TextIndent.None)
        fun width(text: String) = measurer.measure(text, plain, softWrap = false, maxLines = 1).size.width.toFloat()
        val advance = width(SAMPLE) / SAMPLE.length
        val joined = width("中A") - width("中") - width("A")
        Typesetting(advance = advance, addSpace = joined < advance * SPACED)
    }
}

/** [text] with a quarter em between Chinese and the Latin letters and digits next to it. */
fun spaced(text: String): String = MIXED.replace(text, SPACE)

private const val SAMPLE = "中文排版中文排版中文排版"

/** A gap wider than this share of a character means the platform spaces mixed text already. */
private const val SPACED = 0.08f

private const val SPACE = " "

private val MIXED = Regex("""(?<=\p{IsHan})(?=[A-Za-z0-9])|(?<=[A-Za-z0-9])(?=\p{IsHan})""")
