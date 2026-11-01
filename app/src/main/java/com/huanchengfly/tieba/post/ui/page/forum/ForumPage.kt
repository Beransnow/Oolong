package com.huanchengfly.tieba.post.ui.page.forum

import androidx.compose.runtime.Composable
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.ramcosta.composedestinations.annotation.DeepLink
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator

@Destination(
    deepLinks = [
        DeepLink(uriPattern = "tblite://forum/{forumName}")
    ]
)
@Suppress("UNUSED_PARAMETER") // Navigation argument, read by the view model
@Composable
fun ForumPage(
    forumName: String,
    navigator: DestinationsNavigator,
    viewModel: ForumViewModel = hiltViewModel(),
) {
    ForumRoute(navigator = navigator, viewModel = viewModel)
}
