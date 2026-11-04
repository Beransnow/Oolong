package oolong.client.ui.common.navigation

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
import androidx.compose.runtime.mutableStateMapOf
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import oolong.client.ui.common.theme.compose.ExtendedTheme
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

    /** The image the next page opened takes along, set just before it opens. */
    private var pendingHero: PageHero? = null

    /** The image each page opened with one took along, by the page's content key. */
    private val heroes = mutableStateMapOf<Any, PageHero>()

    /** The page whose image flies in the transition under way, to it or back. */
    internal var flight by mutableStateOf<Any?>(null)
        private set

    /** The origin of each page opened from one, by the page's content key. */
    private val opened = mutableMapOf<Any, PageOriginKey>()

    /** The page and origin turning into each other in the transition under way. */
    internal var morph by mutableStateOf<Morph?>(null)
        private set

    /** The page's end of the morph under way, which tells whether its origin was found. */
    @OptIn(ExperimentalSharedTransitionApi::class)
    internal var morphState by mutableStateOf<SharedTransitionScope.SharedContentState?>(null)

    /**
     * How opaque the page with content [key] is, [shown] or going, while the page over it may be
     * shrinking into its origin. Coming back, it goes over that page, so taps reach it at once:
     * the shrinking page is drawn above everything while its origin is found, and otherwise it is
     * this page that fades in over it, [covered] as it is, as that page would have faded out.
     * Turned round, it has nothing over it left to show.
     */
    @OptIn(ExperimentalSharedTransitionApi::class)
    internal fun comebackAlpha(key: Any, shown: Boolean, covered: Float): Float {
        val morph = morph ?: return 1f
        if (morph.opening || morph.page == key) return 1f
        if (!shown) return 0f
        if (morphState?.isMatchFound == true) return 1f
        return ((1f - covered) / MorphFade).coerceIn(0f, 1f)
    }

    internal fun leave(key: PageOriginKey) {
        pending = key
    }

    internal fun leaveWith(hero: PageHero) {
        pendingHero = hero
    }

    /** The image the page with content [key] was opened with, if one was taken along. */
    internal fun heroOf(key: Any): PageHero? = heroes[key]

    /** Whether [hero] flies in the transition under way. */
    internal fun flies(hero: PageHeroKey): Boolean = flight?.let { heroes[it]?.key } == hero

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
            opening -> origin?.let { Morph(page = to, origin = it, opening = true) }
            else -> opened[from]?.let { Morph(page = from, origin = it, opening = false) }
        }
        if (!peek) opened.keys.retainAll(kept)
        if (opening && origin != null) opened[to] = origin
        // An image taken along flies with the page sliding in, and back with it going, but not
        // past a peek: the page shrinks as a whole there, and the image would come away from it
        val hero = pendingHero.also { pendingHero = null }
        if (opening && hero != null) heroes[to] = hero
        flight = when {
            peek -> null
            opening -> to.takeIf { hero != null }
            else -> from.takeIf { heroes.containsKey(from) }
        }
        if (!peek) heroes.keys.retainAll(kept + listOfNotNull(flight))
        return morph != null
    }

    /** Ends any morph, for a transition that is not between pages framed alone. */
    internal fun clear() {
        pending = null
        morph = null
        pendingHero = null
        flight = null
    }
}

@Immutable
internal data class Morph(val page: Any, val origin: PageOriginKey, val opening: Boolean)

/** The morph under way, if any. */
@Composable
private fun currentMorph(): Morph? = LocalPageOrigins.current?.morph

/** An image a page is opened with, which flies from where it was into the page and back: its [key] and what it shows. */
@Immutable
data class PageHero(val key: PageHeroKey, val image: String?)

/** One image pages may be opened with, kept across leaving and coming back to the page that shows it. */
@Immutable
data class PageHeroKey(val id: Long)

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
    val state = rememberMorphState(origin.key, enabled) ?: return this
    // Lifted out of the page it shows on, it takes that page's ground along
    return morphing(state, visibility, enabled)
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
    val enabled = currentMorph()?.origin == origin.key
    val state = rememberMorphState(origin.key, enabled) ?: return this
    return morphing(state, visibility, enabled)
}

/**
 * An image pages open with, such as a forum's avatar on a chip: the page slides in as usual and
 * only the image flies, from here into the page and back. Call [leave] just before opening a
 * page, and give the image here [pageHero] and the one in the page [pageHeroTarget].
 */
@Stable
class PageHeroSource internal constructor(private val origins: PageOrigins?, val key: PageHeroKey) {
    /** The page about to open takes [image] along. */
    fun leave(image: String?) {
        origins?.leaveWith(PageHero(key, image))
    }
}

/** A source of an image pages open with, the same one each time this is shown again. */
@Composable
fun rememberPageHero(): PageHeroSource {
    val origins = LocalPageOrigins.current
    val id = rememberSaveable { Random.nextLong() }
    return remember(origins, id) { PageHeroSource(origins, PageHeroKey(id)) }
}

/** The content key of the page framed alone around here; null within a wider scene. */
val LocalFramedPage = staticCompositionLocalOf<Any?> { null }

/** The image the page around here was opened with, to show until it has its own. */
@Composable
fun openedWithHero(): PageHero? {
    val page = LocalFramedPage.current ?: return null
    return LocalPageOrigins.current?.heroOf(page)
}

/** Has the image [source] stands for fly into the page opened from it, and back. Always in place. */
@Composable
fun Modifier.pageHero(source: PageHeroSource): Modifier {
    val origins = LocalPageOrigins.current ?: return this
    return heroing(source.key, enabled = origins.flies(source.key))
}

/** Where the image the page around here was opened with lands; nothing if it was opened without one. */
@Composable
fun Modifier.pageHeroTarget(): Modifier {
    val origins = LocalPageOrigins.current ?: return this
    val page = LocalFramedPage.current ?: return this
    val hero = origins.heroOf(page) ?: return this
    return heroing(hero.key, enabled = origins.flight == page)
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun Modifier.heroing(key: PageHeroKey, enabled: Boolean): Modifier {
    val scope = LocalPageSharedTransitionScope.current ?: return this
    // Both ends show on the way, one fading into the other, so the image is the one already
    // shown from the start, while the other end may still be loading its own
    return with(scope) {
        this@heroing.sharedBounds(
            sharedContentState = rememberSharedContentState(key, if (enabled) Enabled else Disabled),
            animatedVisibilityScope = LocalNavAnimatedContentScope.current,
            enter = if (enabled) fadeIn(PageTransitions.seeked(end = HeroFade)) else EnterTransition.None,
            exit = if (enabled) fadeOut(PageTransitions.seeked(end = HeroFade)) else ExitTransition.None,
            boundsTransform = { _, _ -> PageTransitions.seeked() },
            resizeMode = scaleToBounds(ContentScale.Crop, Alignment.Center),
        )
    }
}

/** Has the page framed alone with content [key] turn into the origin it opened from, and back. */
@Composable
internal fun Modifier.pageMorph(key: Any): Modifier {
    val origins = LocalPageOrigins.current ?: return this
    val morph = origins.morph
    if (morph == null || morph.page != key) return this
    val state = rememberMorphState(morph.origin, enabled = true) ?: return this
    DisposableEffect(origins, state) {
        origins.morphState = state
        onDispose { if (origins.morphState === state) origins.morphState = null }
    }
    return morphing(state, LocalNavAnimatedContentScope.current, enabled = true)
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun rememberMorphState(key: PageOriginKey, enabled: Boolean): SharedTransitionScope.SharedContentState? {
    val scope = LocalPageSharedTransitionScope.current ?: return null
    return with(scope) { rememberSharedContentState(key, if (enabled) Enabled else Disabled) }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun Modifier.morphing(
    state: SharedTransitionScope.SharedContentState,
    visibility: AnimatedVisibilityScope,
    enabled: Boolean,
): Modifier {
    val scope = LocalPageSharedTransitionScope.current ?: return this
    return with(scope) {
        this@morphing.sharedBounds(
            sharedContentState = state,
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

/** How far into a flight one end of an image has faded into the other. */
private const val HeroFade = 0.6f

/** How far into a morph the page has faded in, and its origin out. */
internal const val MorphFade = 0.4f
