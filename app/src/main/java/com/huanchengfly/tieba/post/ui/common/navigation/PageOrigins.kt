package com.huanchengfly.tieba.post.ui.common.navigation

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.SharedTransitionScope.ResizeMode.Companion.scaleToBounds
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import com.huanchengfly.tieba.post.ui.common.theme.compose.ExtendedTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.random.Random

/**
 * What pages open out of, such as a thread's card: a page opened from one grows out of it, and
 * going back shrinks it into it again, as one shared element of the page transition.
 *
 * Only the origin a page was opened from takes part, and only while that page comes or goes, so
 * the same thing shown twice, as a thread in two lists, never matches the wrong one.
 */
@Stable
class PageOrigins {
    /** The origin the next page opened leaves from, set just before it opens. */
    private var pending: PageOriginKey? = null

    /** The origin of each page opened from one, by the page's content key. */
    private val opened = mutableMapOf<Any, PageOriginKey>()

    /** The page and origin turning into each other in the transition under way. */
    internal var morph by mutableStateOf<Morph?>(null)
        private set

    internal fun leave(key: PageOriginKey) {
        pending = key
    }

    /**
     * Whether the transition from page [from] to page [to], each framed alone, is a morph:
     * opening a page from the origin left just before, or going back from it. Told each such
     * transition as it starts, which ends any morph before; [kept] are the pages left on the stack.
     * Going back by a [peek], the page morphs as soon as the peek starts, since an element can't
     * join a transition already under way; the peek may yet be called off.
     */
    internal fun morphs(from: Any, to: Any, opening: Boolean, peek: Boolean, kept: Set<Any>): Boolean {
        val origin = pending.also { pending = null }
        morph = when {
            opening -> origin?.let { Morph(page = to, origin = it) }
            else -> opened[from]?.let { Morph(page = from, origin = it) }
        }
        if (!peek) opened.keys.retainAll(kept)
        if (opening && origin != null) opened[to] = origin
        return morph != null
    }

    /** Ends any morph, for a transition that is not between pages framed alone. */
    internal fun clear() {
        pending = null
        morph = null
    }
}

@Immutable
internal data class Morph(val page: Any, val origin: PageOriginKey)

/** The morph under way, if any. */
@Composable
private fun currentMorph(): Morph? = LocalPageOrigins.current?.morph

/** One origin, kept across leaving and coming back to the page that shows it. */
@Immutable
data class PageOriginKey(val id: Long)

/** The [PageOrigins] of the pages around; null where pages don't morph, as in previews and tests. */
val LocalPageOrigins = staticCompositionLocalOf<PageOrigins?> { null }

/** Where the page transitions' shared elements are drawn. */
@OptIn(ExperimentalSharedTransitionApi::class)
val LocalPageSharedTransitionScope = staticCompositionLocalOf<SharedTransitionScope?> { null }

/** Lets pages in [content] open out of [PageOrigin]s, drawing them over everything else as they do. */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun PageMorphHost(content: @Composable () -> Unit) {
    val origins = remember { PageOrigins() }
    SharedTransitionLayout {
        CompositionLocalProvider(
            LocalPageSharedTransitionScope provides this,
            LocalPageOrigins provides origins,
        ) {
            content()
        }
    }
}

/**
 * Something pages open out of, such as a card. Call [leave] just before opening a page from it,
 * and give what it shows [pageOrigin]. A stand-in for it elsewhere, such as a larger look at it
 * in a menu, takes its place with [pageOriginStandIn] while it shows.
 */
@Stable
class PageOrigin internal constructor(private val origins: PageOrigins?, val key: PageOriginKey) {
    /** The page the origin shows on, coming and going, which a stand-in elsewhere goes with. */
    internal var visibility: AnimatedVisibilityScope? = null

    /** Whether a stand-in shows, so a page opened now grows out of it, and not the origin. */
    internal var standIn by mutableStateOf(false)

    /** The page about to open leaves from here. */
    fun leave() {
        origins?.leave(key)
    }

    /**
     * Waits for the page opened from here to have grown out of it, or returns at once if none
     * does: a stand-in has to stay for that.
     */
    suspend fun awaitOpened() {
        val origins = origins ?: return
        val leaving = visibility ?: return
        // The morph starts once the page is told to come, and the origin's page to go
        withTimeoutOrNull(StartTimeoutMillis) {
            snapshotFlow { origins.morph?.origin == key && leaving.transition.targetState == EnterExitState.PostExit }.first { it }
        } ?: return
        snapshotFlow { leaving.transition.currentState == leaving.transition.targetState }.first { it }
    }

    private companion object {
        const val StartTimeoutMillis = 500L
    }
}

/** An origin for pages, the same one each time this is shown again. */
@Composable
fun rememberPageOrigin(): PageOrigin {
    val origins = LocalPageOrigins.current
    val id = rememberSaveable { Random.nextLong() }
    return remember(origins, id) { PageOrigin(origins, PageOriginKey(id)) }
}

/**
 * Has [origin] turn into the page opened from it, and that page back into it. Always in place,
 * so where the origin is stays known, but only taking part in the morph of its own page.
 */
@Composable
fun Modifier.pageOrigin(origin: PageOrigin): Modifier {
    if (LocalPageSharedTransitionScope.current == null) return this
    val visibility = LocalNavAnimatedContentScope.current
    SideEffect { origin.visibility = visibility }
    val enabled = !origin.standIn && currentMorph()?.origin == origin.key
    // Lifted out of the page it shows on, it takes that page's ground along
    return morphing(origin.key, visibility, enabled)
        .then(if (enabled) Modifier.background(ExtendedTheme.colors.background) else Modifier)
}

/**
 * Has what shows in place of [origin], away from its page, turn into the page opened from it
 * while it shows. Opening a page has to wait for it ([PageOrigin.awaitOpened]).
 */
@Composable
fun Modifier.pageOriginStandIn(origin: PageOrigin): Modifier {
    val visibility = origin.visibility ?: return this
    DisposableEffect(origin) {
        origin.standIn = true
        onDispose { origin.standIn = false }
    }
    return morphing(origin.key, visibility, enabled = currentMorph()?.origin == origin.key)
}

/** Has the page framed alone with content [key] turn into the origin it opened from, and back. */
@Composable
internal fun Modifier.pageMorph(key: Any): Modifier {
    val morph = currentMorph()
    if (morph == null || morph.page != key || LocalPageSharedTransitionScope.current == null) return this
    return morphing(morph.origin, LocalNavAnimatedContentScope.current, enabled = true)
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun Modifier.morphing(key: PageOriginKey, visibility: AnimatedVisibilityScope, enabled: Boolean): Modifier {
    val scope = LocalPageSharedTransitionScope.current ?: return this
    return with(scope) {
        this@morphing.sharedBounds(
            sharedContentState = rememberSharedContentState(key, if (enabled) Enabled else Disabled),
            animatedVisibilityScope = visibility,
            enter = if (enabled) fadeIn(PageTransitions.seeked(end = MorphFade)) else EnterTransition.None,
            exit = if (enabled) fadeOut(PageTransitions.seeked(end = MorphFade)) else ExitTransition.None,
            boundsTransform = { _, _ -> PageTransitions.seeked() },
            resizeMode = scaleToBounds(ContentScale.FillWidth, Alignment.TopCenter),
        )
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
private class MorphConfig(private val enabled: Boolean) : SharedTransitionScope.SharedContentConfig {
    override val SharedTransitionScope.SharedContentState.isEnabled: Boolean get() = enabled
}

private val Enabled = MorphConfig(enabled = true)
private val Disabled = MorphConfig(enabled = false)

/** How far into a morph the page has faded in, and its origin out. */
internal const val MorphFade = 0.4f
