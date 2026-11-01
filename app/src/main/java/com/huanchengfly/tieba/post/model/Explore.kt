package com.huanchengfly.tieba.post.model

import androidx.compose.runtime.Immutable
import com.huanchengfly.tieba.post.api.models.protos.ThreadInfo
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/** A recommended thread, and the reasons the user may give for not wanting threads like it. */
@Immutable
data class RecommendedThread(
    val thread: ThreadInfo,
    val dislikeOptions: ImmutableList<DislikeOption> = persistentListOf(),
)

/** A reason for not wanting a recommended thread. */
@Immutable
data class DislikeOption(val id: Int, val text: String, val extra: String) {
    /** Whether the option is long enough to take a whole row of the menu. */
    val wide: Boolean get() = id == 7
}

/** A topic on the hot topics ranking. */
@Immutable
data class HotTopic(val id: Long, val name: String, val tag: Tag = Tag.None) {
    enum class Tag { None, New, Hot }
}

/** A topic on the full hot topics ranking. */
@Immutable
data class RankedTopic(
    val id: Long,
    val name: String,
    val description: String,
    val image: String,
    val discussCount: Long,
    val tag: HotTopic.Tag = HotTopic.Tag.None,
)

/** A category of the hot threads ranking. */
@Immutable
data class HotCategory(val code: String, val name: String) {
    companion object {
        /** The code of the ranking of every category. */
        const val ALL = "all"
    }
}

/** The hot threads ranking of one category, with the categories and the top hot topics. */
data class HotThreads(
    val topics: List<HotTopic>,
    val categories: List<HotCategory>,
    val threads: List<ThreadInfo>,
)
