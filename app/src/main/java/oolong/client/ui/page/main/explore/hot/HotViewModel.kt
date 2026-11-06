package oolong.client.ui.page.main.explore.hot

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import oolong.client.R
import oolong.client.arch.LoadStatus
import oolong.client.arch.MessageController
import oolong.client.model.ForumMeasure
import oolong.client.model.ForumRanking
import oolong.client.model.RankedForum
import oolong.client.model.RankedThread
import oolong.client.model.RankedTopic
import oolong.client.model.ThreadMeasure
import oolong.client.repository.BlockPlace
import oolong.client.repository.BlockRuleSource
import oolong.client.repository.BlockVerdict
import oolong.client.repository.ExploreRepository
import oolong.client.repository.ForumRepository
import oolong.client.repository.check
import javax.inject.Inject

/** A thread on the ranking, and how the block rules block it, if they do. */
@Immutable
data class HotThread(val ranked: RankedThread, val block: BlockVerdict? = null)

@Immutable
data class HotState(
    /** The top of the hot topics ranking. */
    val topics: ImmutableList<RankedTopic> = persistentListOf(),
    val threadMeasure: ThreadMeasure = ThreadMeasure.MostRead,
    val threads: ImmutableList<HotThread> = persistentListOf(),
    /** Whether the threads of another measure are on their way. */
    val switchingThreads: Boolean = false,
    val forumMeasure: ForumMeasure = ForumMeasure.MostFollowed,
    val forums: ForumRanking? = null,
    val switchingForums: Boolean = false,
    /** How loading the whole page went. */
    val load: LoadStatus = LoadStatus.Loading,
)

/**
 * The day's hot topics, threads and forums, each ranking by one measure the user picks; loaded
 * the first time the tab shows. Each measure is loaded once until the page is refreshed.
 */
@HiltViewModel
class HotViewModel @Inject constructor(
    private val repository: ExploreRepository,
    private val forumRepository: ForumRepository,
    private val messages: MessageController,
    blockRuleSource: BlockRuleSource,
) : ViewModel() {
    private val local = MutableStateFlow(Local())
    private var loadJob: Job? = null
    private var threadJob: Job? = null
    private var forumJob: Job? = null

    private data class Local(
        val topics: List<RankedTopic> = emptyList(),
        val threadMeasure: ThreadMeasure = ThreadMeasure.MostRead,
        val threads: Map<ThreadMeasure, List<RankedThread>> = emptyMap(),
        val switchingThreads: Boolean = false,
        val forumMeasure: ForumMeasure = ForumMeasure.MostFollowed,
        val forums: Map<ForumMeasure, ForumRanking> = emptyMap(),
        val switchingForums: Boolean = false,
        /** Forums followed from here, shown followed whatever the ranking says. */
        val followed: Set<Long> = emptySet(),
        val load: LoadStatus = LoadStatus.Loading,
        val opened: Boolean = false,
    )

    val state: StateFlow<HotState> = combine(local, blockRuleSource.rules) { local, rules ->
        HotState(
            topics = local.topics.take(TopTopics).toImmutableList(),
            threadMeasure = local.threadMeasure,
            threads = local.threads[local.threadMeasure].orEmpty()
                .map { HotThread(it, rules.check(it.thread, BlockPlace.Feeds)) }
                .toImmutableList(),
            switchingThreads = local.switchingThreads,
            forumMeasure = local.forumMeasure,
            forums = local.forums[local.forumMeasure]?.let { ranking ->
                ranking.copy(forums = ranking.forums.map { it.withFollowed(local.followed) }.toImmutableList())
            },
            switchingForums = local.switchingForums,
            load = local.load,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, HotState())

    /** Starts loading the page unless it already has. */
    fun open() {
        if (!local.value.opened) refresh()
    }

    /** Loads every section again, on the measures picked. */
    fun refresh() {
        loadJob?.cancel()
        threadJob?.cancel()
        forumJob?.cancel()
        local.update { it.copy(load = LoadStatus.Loading, opened = true, switchingThreads = false, switchingForums = false) }
        loadJob = viewModelScope.launch {
            val current = local.value
            val topics = async { runCatching { repository.topics() } }
            val threads = async { runCatching { repository.threadRanking(current.threadMeasure) } }
            val forums = async { runCatching { repository.forumRanking(current.forumMeasure) } }
            val results = listOf(topics.await(), threads.await(), forums.await())
            results.firstNotNullOfOrNull { it.exceptionOrNull() as? CancellationException }?.let { throw it }
            if (results.all { it.isFailure }) {
                local.update { it.copy(load = LoadStatus.Error(results.first().exceptionOrNull()!!)) }
                return@launch
            }
            local.update {
                it.copy(
                    topics = topics.await().getOrDefault(emptyList()),
                    threads = threads.await().getOrNull()?.let { list -> mapOf(it.threadMeasure to list) }.orEmpty(),
                    forums = forums.await().getOrNull()?.let { ranking -> mapOf(it.forumMeasure to ranking) }.orEmpty(),
                    followed = emptySet(),
                    load = LoadStatus.Idle,
                )
            }
        }
    }

    fun selectThreadMeasure(measure: ThreadMeasure) {
        val previous = local.value.threadMeasure
        if (measure == previous) return
        threadJob?.cancel()
        if (measure in local.value.threads) {
            local.update { it.copy(threadMeasure = measure, switchingThreads = false) }
            return
        }
        local.update { it.copy(threadMeasure = measure, switchingThreads = true) }
        threadJob = viewModelScope.launch {
            try {
                val threads = repository.threadRanking(measure)
                local.update { it.copy(threads = it.threads + (measure to threads), switchingThreads = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                local.update { it.copy(threadMeasure = previous, switchingThreads = false) }
                messages.show(R.string.toast_load_failed)
            }
        }
    }

    fun selectForumMeasure(measure: ForumMeasure) {
        val previous = local.value.forumMeasure
        if (measure == previous) return
        forumJob?.cancel()
        if (measure in local.value.forums) {
            local.update { it.copy(forumMeasure = measure, switchingForums = false) }
            return
        }
        local.update { it.copy(forumMeasure = measure, switchingForums = true) }
        forumJob = viewModelScope.launch {
            try {
                val forums = repository.forumRanking(measure)
                local.update { it.copy(forums = it.forums + (measure to forums), switchingForums = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                local.update { it.copy(forumMeasure = previous, switchingForums = false) }
                messages.show(R.string.toast_load_failed)
            }
        }
    }

    /** Follows [forum], showing it followed at once and taking that back if it fails. */
    fun follow(forum: RankedForum) {
        val id = forum.forum.id
        if (forum.liked) return
        local.update { it.copy(followed = it.followed + id) }
        viewModelScope.launch {
            try {
                forumRepository.like(id, forum.forum.name)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                local.update { it.copy(followed = it.followed - id) }
                messages.show(R.string.toast_follow_forum_failed, forum.forum.name)
            }
        }
    }
}

private fun RankedForum.withFollowed(followed: Set<Long>) = if (forum.id in followed) copy(liked = true) else this

private const val TopTopics = 5
