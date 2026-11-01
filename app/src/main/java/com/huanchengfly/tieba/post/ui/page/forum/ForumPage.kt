package com.huanchengfly.tieba.post.ui.page.forum

import androidx.compose.runtime.Composable
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.huanchengfly.tieba.post.ui.navigation.ForumKey
import com.huanchengfly.tieba.post.ui.navigation.Navigator
import com.huanchengfly.tieba.post.ui.page.LocalNavigator

@Composable
fun ForumPage(
    key: ForumKey,
    navigator: Navigator = LocalNavigator.current,
    viewModel: ForumViewModel = hiltViewModel<ForumViewModel, ForumViewModel.Factory> { it.create(key) },
) {
    ForumRoute(navigator = navigator, viewModel = viewModel)
}
