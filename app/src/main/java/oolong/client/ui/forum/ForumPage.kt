package oolong.client.ui.forum

import androidx.compose.runtime.Composable
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import oolong.client.navigation.ForumKey
import oolong.client.navigation.LocalNavigator
import oolong.client.navigation.Navigator

@Composable
fun ForumPage(
    key: ForumKey,
    navigator: Navigator = LocalNavigator.current,
    viewModel: ForumViewModel = hiltViewModel<ForumViewModel, ForumViewModel.Factory> { it.create(key) },
) {
    ForumRoute(navigator = navigator, viewModel = viewModel)
}
