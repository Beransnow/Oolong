package oolong.client.ui.page.subposts

import androidx.compose.runtime.Composable
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import oolong.client.ui.navigation.Navigator
import oolong.client.ui.navigation.SubPostsKey
import oolong.client.ui.navigation.SubPostsSheetKey
import oolong.client.ui.page.LocalNavigator

@Composable
fun SubPostsPage(
    key: SubPostsKey,
    navigator: Navigator = LocalNavigator.current,
    viewModel: SubPostsViewModel = hiltViewModel<SubPostsViewModel, SubPostsViewModel.Factory> { it.create(key) },
) {
    SubPostsRoute(navigator = navigator, isSheet = false, viewModel = viewModel)
}

@Composable
fun SubPostsSheetPage(
    key: SubPostsSheetKey,
    navigator: Navigator = LocalNavigator.current,
    viewModel: SubPostsViewModel = hiltViewModel<SubPostsViewModel, SubPostsViewModel.Factory> { it.create(key.asPage()) },
) {
    SubPostsRoute(navigator = navigator, isSheet = true, viewModel = viewModel)
}
