package com.huanchengfly.tieba.post.ui.page.subposts

import androidx.compose.runtime.Composable
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.huanchengfly.tieba.post.ui.navigation.Navigator
import com.huanchengfly.tieba.post.ui.page.LocalNavigator
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.spec.DestinationStyleBottomSheet

@Suppress("UNUSED_PARAMETER") // Navigation arguments, read by the view model
@Destination
@Composable
fun SubPostsPage(
    navigator: Navigator = LocalNavigator.current,
    threadId: Long,
    forumId: Long = 0L,
    postId: Long = 0L,
    subPostId: Long = 0L,
    loadFromSubPost: Boolean = false,
    viewModel: SubPostsViewModel = hiltViewModel(),
) {
    SubPostsRoute(navigator = navigator, isSheet = false, viewModel = viewModel)
}

@Suppress("UNUSED_PARAMETER") // Navigation arguments, read by the view model
@Destination(style = DestinationStyleBottomSheet::class)
@Composable
fun SubPostsSheetPage(
    navigator: Navigator = LocalNavigator.current,
    threadId: Long,
    forumId: Long = 0L,
    postId: Long = 0L,
    subPostId: Long = 0L,
    loadFromSubPost: Boolean = false,
    viewModel: SubPostsViewModel = hiltViewModel(),
) {
    SubPostsRoute(navigator = navigator, isSheet = true, viewModel = viewModel)
}
