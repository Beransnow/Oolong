package com.huanchengfly.tieba.post.ui.page.forum

import androidx.compose.runtime.Composable
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.huanchengfly.tieba.post.ui.navigation.Navigator
import com.huanchengfly.tieba.post.ui.page.LocalNavigator
import com.ramcosta.composedestinations.annotation.DeepLink
import com.ramcosta.composedestinations.annotation.Destination

@Destination(
    deepLinks = [
        DeepLink(uriPattern = "tblite://forum/{forumName}")
    ]
)
@Suppress("UNUSED_PARAMETER") // Navigation argument, read by the view model
@Composable
fun ForumPage(
    forumName: String,
    navigator: Navigator = LocalNavigator.current,
    viewModel: ForumViewModel = hiltViewModel(),
) {
    ForumRoute(navigator = navigator, viewModel = viewModel)
}
