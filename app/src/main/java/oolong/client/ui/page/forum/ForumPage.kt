package oolong.client.ui.page.forum

import androidx.compose.runtime.Composable
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import oolong.client.ui.navigation.ForumKey
import oolong.client.ui.navigation.Navigator
import oolong.client.ui.page.LocalNavigator

@Composable
fun ForumPage(
    key: ForumKey,
    navigator: Navigator = LocalNavigator.current,
    viewModel: ForumViewModel = hiltViewModel<ForumViewModel, ForumViewModel.Factory> { it.create(key) },
) {
    ForumRoute(navigator = navigator, viewModel = viewModel)
}
