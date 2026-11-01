package com.huanchengfly.tieba.post.ui.page.subposts

import androidx.compose.runtime.Composable
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.huanchengfly.tieba.post.ui.navigation.Navigator
import com.huanchengfly.tieba.post.ui.navigation.SubPostsKey
import com.huanchengfly.tieba.post.ui.navigation.SubPostsSheetKey
import com.huanchengfly.tieba.post.ui.page.LocalNavigator
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.spec.DestinationStyleBottomSheet

@Suppress("UNUSED_PARAMETER") // Navigation arguments, read by the view model
@Destination(navArgsDelegate = SubPostsKey::class)
@Composable
fun SubPostsPage(
    key: SubPostsKey,
    navigator: Navigator = LocalNavigator.current,
    viewModel: SubPostsViewModel = hiltViewModel<SubPostsViewModel, SubPostsViewModel.Factory> { it.create(key) },
) {
    SubPostsRoute(navigator = navigator, isSheet = false, viewModel = viewModel)
}

@Suppress("UNUSED_PARAMETER") // Navigation arguments, read by the view model
@Destination(navArgsDelegate = SubPostsSheetKey::class, style = DestinationStyleBottomSheet::class)
@Composable
fun SubPostsSheetPage(
    key: SubPostsSheetKey,
    navigator: Navigator = LocalNavigator.current,
    viewModel: SubPostsViewModel = hiltViewModel<SubPostsViewModel, SubPostsViewModel.Factory> { it.create(key.asPage()) },
) {
    SubPostsRoute(navigator = navigator, isSheet = true, viewModel = viewModel)
}
