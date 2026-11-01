package com.huanchengfly.tieba.post.ui.page.thread

import androidx.compose.runtime.Composable
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.huanchengfly.tieba.post.api.models.protos.ThreadInfo
import com.huanchengfly.tieba.post.ui.navigation.Navigator
import com.huanchengfly.tieba.post.ui.page.LocalNavigator
import com.ramcosta.composedestinations.annotation.DeepLink
import com.ramcosta.composedestinations.annotation.Destination
import kotlinx.serialization.Serializable

object ThreadPageFrom {
    const val FROM_FORUM = "forum"

    // 收藏
    const val FROM_STORE = "store_thread"
    const val FROM_PERSONALIZED = "personalized"
    const val FROM_HISTORY = "history"
}

@Serializable
sealed interface ThreadPageExtra

@Serializable
data object ThreadPageNoExtra : ThreadPageExtra

@Serializable
data class ThreadPageFromStoreExtra(
    val maxPid: Long,
    val maxFloor: Int,
) : ThreadPageExtra

object ThreadSortType {
    const val SORT_TYPE_ASC = 0
    const val SORT_TYPE_DESC = 1
    const val SORT_TYPE_HOT = 2
    const val SORT_TYPE_DEFAULT = SORT_TYPE_ASC
}

@Destination(
    deepLinks = [
        DeepLink(uriPattern = "tblite://thread/{threadId}"),
    ]
)
@Suppress("UNUSED_PARAMETER") // Navigation arguments, read by the view model
@Composable
fun ThreadPage(
    threadId: Long,
    navigator: Navigator = LocalNavigator.current,
    forumId: Long? = null,
    postId: Long = 0,
    seeLz: Boolean = false,
    sortType: Int = 0,
    from: String = "",
    extra: ThreadPageExtra? = null,
    threadInfo: ThreadInfo? = null,
    scrollToReply: Boolean = false,
    viewModel: ThreadViewModel = hiltViewModel(),
) {
    ThreadRoute(navigator = navigator, from = from, extra = extra, viewModel = viewModel)
}
