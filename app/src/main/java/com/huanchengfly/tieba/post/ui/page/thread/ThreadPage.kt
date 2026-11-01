package com.huanchengfly.tieba.post.ui.page.thread

import androidx.compose.runtime.Composable
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.huanchengfly.tieba.post.ui.navigation.Navigator
import com.huanchengfly.tieba.post.ui.navigation.ThreadKey
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
    navArgsDelegate = ThreadKey::class,
    deepLinks = [
        DeepLink(uriPattern = "tblite://thread/{threadId}"),
    ]
)
@Composable
fun ThreadPage(
    key: ThreadKey,
    navigator: Navigator = LocalNavigator.current,
    viewModel: ThreadViewModel = hiltViewModel<ThreadViewModel, ThreadViewModel.Factory> { it.create(key) },
) {
    ThreadRoute(navigator = navigator, from = key.from, extra = key.extra, viewModel = viewModel)
}
