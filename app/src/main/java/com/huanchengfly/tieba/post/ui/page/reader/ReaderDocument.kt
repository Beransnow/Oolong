package com.huanchengfly.tieba.post.ui.page.reader

import androidx.compose.runtime.Immutable
import com.huanchengfly.tieba.post.model.Post
import com.huanchengfly.tieba.post.model.PostContent
import com.huanchengfly.tieba.post.model.wordText

/**
 * The author's floors of a thread read as one text: each floor's words split into paragraphs, chat
 * passages and scene breaks, headings lifted out where the thread numbers them, short notes between
 * chapters folded together, and a mark where the author came back after a pause.
 */
@Immutable
data class ReaderDocument(
    val blocks: List<ReaderBlock>,
    val chapters: List<ReaderChapter>,
    /** Each time the author came back to post, oldest first; only those that posted more than notes. */
    val updates: List<ReaderUpdate>,
    /** The heading shapes this thread numbers its chapters with. */
    val kinds: Set<HeadingKind>,
) {
    /** The index of the last chapter starting at or before block [index], or -1. */
    fun chapterAt(index: Int): Int = chapters.indexOfLast { it.block <= index }

    /** The index of the last update starting at or before block [index], or -1. */
    fun updateAt(index: Int): Int = updates.indexOfLast { it.block <= index }

    companion object {
        val Empty = ReaderDocument(emptyList(), emptyList(), emptyList(), emptySet())
    }
}

/** One piece of the text, from the floor [floor] posted at [time]. */
@Immutable
sealed interface ReaderBlock {
    val key: String
    val floor: Int
    val time: Long

    /** Where the author came back after more than six hours; [gap] is null for the first post. */
    data class UpdateMark(override val key: String, override val floor: Int, override val time: Long, val gap: Long?) : ReaderBlock

    data class Heading(
        override val key: String,
        override val floor: Int,
        override val time: Long,
        val title: String,
        /** 1 for a chapter, 2 for a section inside one. */
        val level: Int,
    ) : ReaderBlock

    data class Paragraph(override val key: String, override val floor: Int, override val time: Long, val text: String) : ReaderBlock

    /** Lines written as `name: words`, set as a chat; [room] names the group chat, when the text does. */
    data class Chat(
        override val key: String,
        override val floor: Int,
        override val time: Long,
        val room: String?,
        val lines: List<ChatLine>,
    ) : ReaderBlock

    /** A scene break the author wrote as a column of lone "。". */
    data class Break(override val key: String, override val floor: Int, override val time: Long) : ReaderBlock

    data class Media(
        override val key: String,
        override val floor: Int,
        override val time: Long,
        val postId: Long,
        val content: PostContent,
    ) : ReaderBlock

    /** The author's short floors between two chapters, folded. */
    data class Notes(override val key: String, override val floor: Int, override val time: Long, val notes: List<Note>) : ReaderBlock

    /** The end of a floor: its number and time, and its replies. */
    data class Foot(
        override val key: String,
        override val floor: Int,
        override val time: Long,
        val postId: Long,
        val subPostCount: Int,
    ) : ReaderBlock
}

@Immutable
data class ChatLine(val name: String, val text: String)

@Immutable
data class Note(val postId: Long, val floor: Int, val time: Long, val text: String)

@Immutable
data class ReaderChapter(val title: String, val level: Int, val block: Int, val floor: Int, val time: Long)

@Immutable
data class ReaderUpdate(
    val block: Int,
    val time: Long,
    /** Seconds since the update before, or null for the first. */
    val gap: Long?,
    val floors: Int,
    val chars: Int,
    /** The first line worth showing, or null when the update opens with pictures only. */
    val preview: String?,
)

/** A way of writing chapter headings; a thread's own way is found by [detectKinds]. */
enum class HeadingKind(val level: Int, internal val regex: Regex) {
    /** 【12】 */
    Bracket(1, Regex("""^【(\d+)】\s*(.*)$""")),

    /** 第十二章 title */
    Chapter(1, Regex("""^第($NUM)[章卷](?:[：:\s]*(.{0,24}))?$""")),

    /** 第三节 title */
    Section(2, Regex("""^第($NUM)节(?:[：:\s]*(.{0,24}))?$""")),

    /** (12) title */
    Paren(1, Regex("""^[（(](\d+)[)）]\s*(.{0,24})$""")),

    /** 十二、title */
    Dun(1, Regex("""^($NUM)、(.{0,20})$""")),

    /** Chapter 12 title */
    English(1, Regex("""^chapter\s*(\d+)\b\s*(.{0,24})$""", RegexOption.IGNORE_CASE)),
}

private const val NUM = "[零〇一二两三四五六七八九十百\\d]+"

/** A pause longer than this starts a new update. */
private const val UPDATE_GAP = 6 * 3600L

/** Floors without heading or media shorter than this are notes. */
private const val NOTE_LENGTH = 100

/** Heading lines read at the top of a floor. */
private const val HEADINGS_PER_FLOOR = 2

private val CHAT = Regex("""^([^：:\s“"「（(【]{1,12})[：:](.*)$""")
private val ROOM = Regex("""^【[^\d】]{1,10}群】$""")
private val BREAK = Regex("""^[。.·]$""")
private val BREAK_OR_BLANK = Regex("""^[。.·]?$""")

private val CN_DIGITS = mapOf(
    '零' to 0, '〇' to 0, '一' to 1, '二' to 2, '两' to 2, '三' to 3, '四' to 4,
    '五' to 5, '六' to 6, '七' to 7, '八' to 8, '九' to 9,
)

/** [s] written in Arabic or Chinese numerals as a number, or null. */
internal fun parseNumber(s: String): Int? {
    s.toIntOrNull()?.let { return it }
    var n = 0
    var digit = 0
    for (ch in s) {
        when (ch) {
            '十' -> {
                n += (if (digit == 0) 1 else digit) * 10
                digit = 0
            }
            '百' -> {
                n += (if (digit == 0) 1 else digit) * 100
                digit = 0
            }
            else -> digit = CN_DIGITS[ch] ?: return null
        }
    }
    return n + digit
}

/** A heading read off a line: its number, how it reads, and text after it that belongs to the body. */
internal data class HeadingLine(val number: Int, val title: String, val rest: String)

internal fun HeadingKind.read(line: String): HeadingLine? {
    if (this == HeadingKind.Chapter && line == "序章") return HeadingLine(0, line, "")
    val match = regex.matchEntire(line) ?: return null
    val number = parseNumber(match.groupValues[1]) ?: return null
    if (this == HeadingKind.Bracket) {
        // 【12】 may run straight into the text, unless what follows is a 〖subtitle〗
        val rest = match.groupValues[2]
        return if (rest.startsWith("〖")) HeadingLine(number, line, "") else HeadingLine(number, "【${match.groupValues[1]}】", rest)
    }
    return HeadingLine(number, line, "")
}

private fun normalized(title: String) = title.replace(Regex("""[：:\s【】]"""), "")

/** A floor's words as trimmed lines, without the blank ones at the top. */
private fun Post.lines(): List<String> = content.wordText.split('\n').map { it.trim() }.dropWhile { it.isEmpty() }

/**
 * The heading shapes [posts] use as a numbered sequence: at least three headings, nearly all
 * numbered above the one before or starting again from one, with at least two different numbers.
 * A shape used now and then by chance doesn't count, so a wrong guess stays rare.
 */
fun detectKinds(posts: List<Post>): Set<HeadingKind> {
    val lines = posts.map { it.lines().take(HEADINGS_PER_FLOOR) }
    return HeadingKind.entries.filterTo(mutableSetOf()) { kind ->
        val seq = mutableListOf<HeadingLine>()
        lines.forEach { floor ->
            floor.forEach { line ->
                val heading = kind.read(line) ?: return@forEach
                if (seq.isEmpty() || normalized(seq.last().title) != normalized(heading.title)) seq += heading
            }
        }
        if (seq.size < 3) return@filterTo false
        val rising = seq.zipWithNext().count { (a, b) -> b.number > a.number || b.number <= 1 }
        rising >= (seq.size - 1) * 0.8 && seq.map { it.number }.toSet().size >= 2
    }
}

/**
 * Builds the document from the author's [posts], oldest first. Heading shapes in [known] count
 * even when the posts loaded so far don't show them as a sequence, so a chapter once lifted out
 * stays a chapter as more floors load.
 */
fun buildReaderDocument(posts: List<Post>, known: Set<HeadingKind> = emptySet()): ReaderDocument {
    val kinds = known + detectKinds(posts)
    val blocks = mutableListOf<ReaderBlock>()
    val chapters = mutableListOf<ReaderChapter>()
    val updates = mutableListOf<ReaderUpdate>()
    var notes = mutableListOf<Note>()
    var notesStart: Post? = null
    var lastTime: Long? = null

    // An update is marked only once it brings more than notes, so notes posted on their own,
    // after one pause or several, fold together until the text goes on
    var updateStart: Post? = null
    var lastTextTime: Long? = null
    var lastChapter = ""

    fun flushNotes() {
        val start = notesStart ?: return
        blocks += ReaderBlock.Notes("n${start.id}", start.floor, start.time, notes)
        notes = mutableListOf()
        notesStart = null
    }

    for (post in posts) {
        val previous = lastTime
        if (previous == null || post.time - previous > UPDATE_GAP) updateStart = post
        lastTime = post.time

        // The floor in order: its lines, and pictures and videos where they stand between them
        val pieces = post.content.flatMapTo(mutableListOf<Any>()) { content ->
            when (content) {
                is PostContent.Text -> listOf(content).wordText.split('\n').map { it.trim() }
                is PostContent.Voice -> emptyList()
                else -> listOf(content)
            }
        }
        val heads = mutableListOf<Pair<HeadingLine, HeadingKind>>()
        while (heads.size < HEADINGS_PER_FLOOR) {
            val at = pieces.indexOfFirst { it is String && it.isNotEmpty() }
            if (at < 0) break
            val kind = kinds.firstOrNull { it.read(pieces[at] as String) != null } ?: break
            val heading = kind.read(pieces.removeAt(at) as String)!!
            heads += heading to kind
            if (heading.rest.isNotEmpty()) {
                pieces.add(at, heading.rest)
                break
            }
        }
        val lines = pieces.filterIsInstance<String>().dropWhile { it.isEmpty() }
        val media = pieces.filterIsInstance<PostContent>()

        val length = post.content.wordText.trim().length
        if (heads.isEmpty() && media.isEmpty() && length < NOTE_LENGTH) {
            if (notesStart == null) notesStart = post
            notes += Note(post.id, post.floor, post.time, lines.joinToString(" ").trim())
            continue
        }
        flushNotes()
        updateStart?.let { start ->
            val gap = lastTextTime?.let { start.time - it }
            updates += ReaderUpdate(blocks.size, start.time, gap, floors = 0, chars = 0, preview = null)
            blocks += ReaderBlock.UpdateMark("u${start.id}", start.floor, start.time, gap)
            updateStart = null
        }
        lastTextTime = post.time

        val update = updates.last()
        updates[updates.lastIndex] = update.copy(
            floors = update.floors + 1,
            chars = update.chars + length,
            preview = update.preview ?: lines.firstOrNull { it.length > 4 && !BREAK.matches(it) },
        )

        var n = 0
        fun key() = "${post.id}-${n++}"
        heads.forEach { (heading, kind) ->
            if (kind.level == 1 && normalized(heading.title) == normalized(lastChapter)) return@forEach
            chapters += ReaderChapter(heading.title, kind.level, blocks.size, post.floor, post.time)
            blocks += ReaderBlock.Heading(key(), post.floor, post.time, heading.title, kind.level)
            if (kind.level == 1) lastChapter = heading.title
        }
        var run = mutableListOf<String>()
        fun flushRun() {
            typeset(run) { make -> blocks += make(key(), post.floor, post.time) }
            run = mutableListOf()
        }
        pieces.forEach { piece ->
            if (piece is String) {
                run += piece
            } else {
                flushRun()
                blocks += ReaderBlock.Media(key(), post.floor, post.time, post.id, piece as PostContent)
            }
        }
        flushRun()
        blocks += ReaderBlock.Foot("f${post.id}", post.floor, post.time, post.id, post.subPostCount)
    }
    flushNotes()

    return ReaderDocument(blocks, chapters, updates, kinds)
}

/** Splits [lines] into paragraphs, chat passages of three lines or more, and scene breaks. */
private fun typeset(lines: List<String>, add: ((String, Int, Long) -> ReaderBlock) -> Unit) {
    var i = 0
    while (i < lines.size) {
        val line = lines[i]
        if (BREAK.matches(line)) {
            while (i < lines.size && BREAK_OR_BLANK.matches(lines[i])) i++
            add { key, floor, time -> ReaderBlock.Break(key, floor, time) }
            continue
        }
        val room = if (ROOM.matches(line)) line.substring(1, line.length - 1) else null
        val start = if (room != null) i + 1 else i
        var end = start
        while (end < lines.size && CHAT.matches(lines[end])) end++
        if (end - start >= 3) {
            val chat = lines.subList(start, end).map { CHAT.matchEntire(it)!!.let { m -> ChatLine(m.groupValues[1], m.groupValues[2]) } }
            add { key, floor, time -> ReaderBlock.Chat(key, floor, time, room, chat) }
            i = end
            continue
        }
        if (line.isNotEmpty()) add { key, floor, time -> ReaderBlock.Paragraph(key, floor, time, line) }
        i++
    }
}
