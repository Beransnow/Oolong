package com.huanchengfly.tieba.post.ui.page.main.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.huanchengfly.tieba.post.arch.LoadStatus
import com.huanchengfly.tieba.post.model.FollowedForum
import com.huanchengfly.tieba.post.model.VisitedForum
import com.huanchengfly.tieba.post.ui.common.theme.compose.TiebaLiteTheme
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import java.io.IOException

/** Sample home page states, shared by the previews below and the screenshot tests. */
internal object HomePreviewData {
    private val forums = listOf("测试", "安卓", "贴吧 Lite", "一个名字很长很长很长的吧", "minecraft", "steam")
        .mapIndexed { index, name -> FollowedForum(index + 1L, name, "", level = 18 - index * 2, signedIn = index % 2 == 0) }
        .toImmutableList()

    val loaded = HomeState(
        forums = forums,
        pinnedIds = persistentSetOf(2L),
        visited = persistentListOf(VisitedForum("测试", "测试吧", ""), VisitedForum("安卓", "安卓吧", "")),
        loggedIn = true,
    )

    val loading = HomeState(load = LoadStatus.Loading, loggedIn = true)

    val guest = HomeState()

    val error = HomeState(load = LoadStatus.Error(IOException("网络不可用")), loggedIn = true)
}

@Preview("HomeScreen", showBackground = true)
@Composable
private fun PreviewHomeScreen() {
    TiebaLiteTheme { HomeScreen(state = HomePreviewData.loaded, onEvent = {}) }
}

@Preview("HomeScreen List", showBackground = true)
@Composable
private fun PreviewHomeScreenList() {
    TiebaLiteTheme { HomeScreen(state = HomePreviewData.loaded, onEvent = {}, listSingle = true) }
}
