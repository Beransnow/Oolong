package com.huanchengfly.tieba.post.repository

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.huanchengfly.tieba.post.arch.ApplicationScope
import com.huanchengfly.tieba.post.dataStore
import com.huanchengfly.tieba.post.database.BlockDao
import com.huanchengfly.tieba.post.model.BlockRule
import com.huanchengfly.tieba.post.model.PostContent
import com.huanchengfly.tieba.post.model.TextSpan
import com.huanchengfly.tieba.post.model.ThreadSummary
import com.huanchengfly.tieba.post.model.User
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import java.text.Normalizer
import javax.inject.Inject
import javax.inject.Singleton

/** Where content is shown, for rules that apply only in some places. */
enum class BlockPlace { Feeds, Forum, Thread, Messages }

/** What content holds besides plain words. */
enum class ContentKind { Link, Image, Video, Voice, Mention, Emoticon }

/** What block rules look at: who wrote it, what it says, what else it holds, and where. */
data class Blockable(
    val author: User,
    val content: String,
    val place: BlockPlace,
    val title: String = "",
    /** The forum it is in, by name without 吧; null when unknown. */
    val forum: String? = null,
    val kinds: Set<ContentKind> = emptySet(),
    /** How many letters and digits its own words have; emoticons, links, mentions, spaces and punctuation don't count. */
    val length: Int = letterCount(content),
    /** Whether whoever started the thread wrote it. */
    val byThreadAuthor: Boolean = false,
)

/** [content] by [author] as block rules see it: its words for keywords, and what else it holds. */
fun Blockable(
    author: User,
    content: List<PostContent>,
    place: BlockPlace,
    title: String = "",
    forum: String? = null,
    byThreadAuthor: Boolean = false,
): Blockable {
    val kinds = mutableSetOf<ContentKind>()
    var length = 0
    val text = buildString {
        content.forEach { block ->
            when (block) {
                is PostContent.Text -> block.spans.forEach { span ->
                    when (span) {
                        is TextSpan.Plain -> {
                            length += letterCount(span.text)
                            if (span.text.hasEmoji()) kinds += ContentKind.Emoticon
                        }
                        is TextSpan.Link -> kinds += ContentKind.Link
                        is TextSpan.VideoLink -> kinds += ContentKind.Video
                        is TextSpan.Mention -> kinds += ContentKind.Mention
                        is TextSpan.Emoticon -> kinds += ContentKind.Emoticon
                    }
                    // Emoticons are pictures; their names are not words the post says
                    if (span !is TextSpan.Emoticon) append(span.text)
                }
                is PostContent.Image -> kinds += if (block.sticker) ContentKind.Emoticon else ContentKind.Image
                is PostContent.Video -> kinds += ContentKind.Video
                is PostContent.Voice -> kinds += ContentKind.Voice
            }
            append('\n')
        }
    }
    return Blockable(author, text, place, title, forum, kinds, length, byThreadAuthor)
}

/** Content a [rule] matched, and whether it is hidden rather than folded. */
data class BlockVerdict(val rule: BlockRule, val hidden: Boolean)

/** The user's block rules, as they stand. */
fun interface BlockRules {
    /** How [item] is blocked, or null when it is shown. */
    fun check(item: Blockable): BlockVerdict?
}

/** Whether the rules block something by [author] saying [content] inside a thread. */
fun BlockRules.blocks(author: User, content: String): Boolean =
    check(Blockable(author, content, BlockPlace.Thread)) != null

/**
 * How the rules block [thread] in a list at [place], by its author, its forum, or its first floor,
 * or what lists know of it where that is missing.
 */
fun BlockRules.check(thread: ThreadSummary, place: BlockPlace): BlockVerdict? {
    val item = if (thread.firstPost.isNotEmpty()) {
        Blockable(thread.author, thread.firstPost, place, title = thread.title, forum = thread.forum.name, byThreadAuthor = true)
    } else {
        Blockable(
            thread.author, thread.abstract, place, title = thread.title, forum = thread.forum.name,
            kinds = buildSet {
                if (thread.images.isNotEmpty()) add(ContentKind.Image)
                if (thread.video != null) add(ContentKind.Video)
            },
            byThreadAuthor = true,
        )
    }
    return check(item)
}

/** Rules that fold whatever [predicate] picks, for previews and tests. */
fun blockRulesMatching(predicate: (Blockable) -> Boolean): BlockRules {
    val rule = BlockRule(match = BlockRule.Match.Keyword(emptyList()))
    return BlockRules { item -> if (predicate(item)) BlockVerdict(rule, hidden = false) else null }
}

/** What the block settings add to the rules. */
data class BlockOptions(
    /** Content the rules match is hidden, not folded, unless a rule says otherwise. */
    val hideByDefault: Boolean = false,
    /** Users below this level are folded or hidden; null when off. */
    val lowLevel: Int? = null,
)

/**
 * The rules in force at [now]: an exception that matches shows the content whatever else does;
 * otherwise the first rule that matches blocks it. [onHit] hears of each block.
 */
fun blockRulesOf(
    rules: List<BlockRule>,
    options: BlockOptions = BlockOptions(),
    now: Long = System.currentTimeMillis(),
    onHit: (BlockRule, Blockable) -> Unit = { _, _ -> },
): BlockRules {
    val active = rules.filter { it.isActive(now) } +
        listOfNotNull(options.lowLevel?.let { BlockRule(id = LowLevelRuleId, match = BlockRule.Match.Level(it)) })
    val (exceptions, blocks) = active.map(::CompiledRule).partition { it.rule.action == BlockRule.Action.Allow }
    return BlockRules { item ->
        if (exceptions.any { it.matches(item) }) return@BlockRules null
        val rule = blocks.firstOrNull { it.matches(item) }?.rule ?: return@BlockRules null
        onHit(rule, item)
        val hidden = when (rule.action) {
            BlockRule.Action.Hide -> true
            BlockRule.Action.Fold -> false
            else -> options.hideByDefault
        }
        BlockVerdict(rule, hidden)
    }
}

/** The id of the rule the low level setting stands for, which is not stored. */
const val LowLevelRuleId = -1L

private class CompiledRule(val rule: BlockRule) {
    private val inForum = rule.inForum?.let(::forumKey)
    private val test: (Blockable) -> Boolean = when (val match = rule.match) {
        is BlockRule.Match.User -> { item ->
            // A missing id (0) or blank name must never match, otherwise one rule saved without a
            // name would block every user whose name the server left empty
            (match.uid != 0L && match.uid == item.author.id) ||
                (match.name.isNotBlank() && match.name == item.author.name)
        }

        // Opening the forum itself shows its threads
        is BlockRule.Match.Forum -> { item ->
            item.place != BlockPlace.Forum && item.forum != null && forumKey(item.forum) == forumKey(match.name)
        }
        is BlockRule.Match.Level -> { item -> item.author.level in 1 until match.level }
        is BlockRule.Match.Keyword -> keywordTest(match)
    }

    private fun keywordTest(match: BlockRule.Match.Keyword): (Blockable) -> Boolean {
        fun text(item: Blockable) = if (rule.titleOnly) item.title else item.title + "\n" + item.content
        if (match.regex) {
            val regex = match.words.firstOrNull()?.takeIf { it.isNotEmpty() }
                ?.let { runCatching { Regex(it, RegexOption.IGNORE_CASE) }.getOrNull() }
                ?: return { false }
            return { item -> regex.containsMatchIn(text(item)) }
        }
        val words = match.words.map(::normalizeForMatching).filter { it.isNotEmpty() }
        if (words.isEmpty()) return { false }
        return { item ->
            val text = normalizeForMatching(text(item))
            words.all { it in text }
        }
    }

    fun matches(item: Blockable): Boolean {
        if (!rule.scope.covers(item.place)) return false
        if (inForum != null && (item.forum == null || forumKey(item.forum) != inForum)) return false
        return test(item)
    }
}

private fun BlockRule.Scope.covers(place: BlockPlace) = when (this) {
    BlockRule.Scope.All -> true
    BlockRule.Scope.Feeds -> place == BlockPlace.Feeds
    BlockRule.Scope.Forum -> place == BlockPlace.Forum
    BlockRule.Scope.Thread -> place == BlockPlace.Thread
}

private fun forumKey(name: String) = name.trim().removeSuffix("吧").lowercase()

/** How many letters and digits [text] has. */
fun letterCount(text: String): Int {
    var count = 0
    var i = 0
    while (i < text.length) {
        val code = text.codePointAt(i)
        if (Character.isLetterOrDigit(code)) count++
        i += Character.charCount(code)
    }
    return count
}

/** Whether [this] has emoji or other pictographs, which Unicode files as symbols. */
private fun String.hasEmoji(): Boolean {
    var i = 0
    while (i < length) {
        val code = codePointAt(i)
        if (Character.getType(code) == Character.OTHER_SYMBOL.toInt()) return true
        i += Character.charCount(code)
    }
    return false
}

/**
 * [text] as keywords are matched against it: full-width forms made plain, case folded, and
 * spaces, punctuation and symbols dropped, so "剧 透" and "ＡＢＣ" match "剧透" and "abc".
 */
fun normalizeForMatching(text: String): String {
    val plain = Normalizer.normalize(text, Normalizer.Form.NFKC).lowercase()
    return buildString(plain.length) {
        var i = 0
        while (i < plain.length) {
            val code = plain.codePointAt(i)
            if (Character.isLetterOrDigit(code)) appendCodePoint(code)
            i += Character.charCount(code)
        }
    }
}

interface BlockRuleSource {
    /** The current rules, and new ones whenever the user changes them or their settings. */
    val rules: Flow<BlockRules>
}

@Singleton
class DatabaseBlockRuleSource @Inject constructor(
    private val dao: BlockDao,
    @ApplicationContext context: Context,
    @ApplicationScope private val scope: CoroutineScope,
) : BlockRuleSource {
    private val hits = HitCounter()

    override val rules: Flow<BlockRules> = combine(
        // Hit counts change as content is blocked; matching doesn't look at them
        dao.all().map { entities -> entities.map { it.toBlockRule().copy(hits = 0, lastHitAt = null) } }.distinctUntilChanged(),
        context.dataStore.data.map { prefs ->
            BlockOptions(
                hideByDefault = prefs[booleanPreferencesKey(HideBlockedContentKey)] ?: false,
                lowLevel = (prefs[stringPreferencesKey(LowLevelKey)]?.toIntOrNull() ?: DefaultLowLevel)
                    .takeIf { prefs[booleanPreferencesKey(LowLevelEnabledKey)] == true },
            )
        },
    ) { rules, options -> blockRulesOf(rules, options, onHit = hits::record) }
        .shareIn(scope, SharingStarted.Eagerly, replay = 1)

    /**
     * Counts each piece of content once per rule while the app runs, however often lists show it
     * again, and writes the counts a few seconds after they change.
     */
    private inner class HitCounter {
        private val seen = LinkedHashSet<Long>()
        private val pending = mutableMapOf<Long, Long>()
        private var flushing = false

        @Synchronized
        fun record(rule: BlockRule, item: Blockable) {
            if (rule.id <= 0) return
            val key = rule.id * 31 + (item.author.id * 31 + item.content.hashCode()) * 1_000_003
            if (!seen.add(key)) return
            if (seen.size > MaxSeen) seen.remove(seen.first())
            pending[rule.id] = (pending[rule.id] ?: 0) + 1
            if (!flushing) {
                flushing = true
                scope.launch {
                    delay(FlushDelayMillis)
                    flush()
                }
            }
        }

        private suspend fun flush() {
            val counts = synchronized(this) {
                flushing = false
                pending.toMap().also { pending.clear() }
            }
            val at = System.currentTimeMillis()
            counts.forEach { (id, count) -> dao.addHits(id, count, at) }
        }
    }

    private companion object {
        const val MaxSeen = 5_000
        const val FlushDelayMillis = 5_000L
    }
}

const val HideBlockedContentKey = "hideBlockedContent"
const val LowLevelEnabledKey = "blockLowLevel"
const val LowLevelKey = "blockLowLevelBelow"
const val DefaultLowLevel = 3
