package com.huanchengfly.tieba.post.ui.page.forum.rule

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.LocalContentColor
import androidx.compose.material.MaterialTheme
import androidx.compose.material.ProvideTextStyle
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEach
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.huanchengfly.tieba.post.R
import com.huanchengfly.tieba.post.arch.LoadState
import com.huanchengfly.tieba.post.model.ForumRuleAuthor
import com.huanchengfly.tieba.post.model.ForumRuleDetail
import com.huanchengfly.tieba.post.model.ForumRuleItem
import com.huanchengfly.tieba.post.ui.common.TextContentRender
import com.huanchengfly.tieba.post.ui.common.theme.compose.TiebaLiteTheme
import com.huanchengfly.tieba.post.ui.navigation.ForumRuleDetailKey
import com.huanchengfly.tieba.post.ui.navigation.Navigator
import com.huanchengfly.tieba.post.ui.page.LocalNavigator
import com.huanchengfly.tieba.post.ui.page.ProvideNavigator
import com.huanchengfly.tieba.post.ui.widgets.compose.Avatar
import com.huanchengfly.tieba.post.ui.widgets.compose.BackNavigationIcon
import com.huanchengfly.tieba.post.ui.widgets.compose.GlassScaffold
import com.huanchengfly.tieba.post.ui.widgets.compose.GlassTopBar
import com.huanchengfly.tieba.post.ui.widgets.compose.GlassTopBarTitle
import com.huanchengfly.tieba.post.ui.widgets.compose.Sizes
import com.huanchengfly.tieba.post.ui.widgets.compose.UserHeader
import com.huanchengfly.tieba.post.ui.widgets.compose.states.LoadStateScreen
import com.huanchengfly.tieba.post.utils.StringUtil
import com.ramcosta.composedestinations.annotation.Destination
import kotlinx.collections.immutable.persistentListOf

@Destination(navArgsDelegate = ForumRuleDetailKey::class)
@Composable
fun ForumRuleDetailPage(
    key: ForumRuleDetailKey,
    navigator: Navigator = LocalNavigator.current,
    viewModel: ForumRuleDetailViewModel = hiltViewModel<ForumRuleDetailViewModel, ForumRuleDetailViewModel.Factory> { it.create(key) },
) {
    val state by viewModel.ruleDetail.state.collectAsStateWithLifecycle()

    ProvideNavigator(navigator = navigator) {
        ForumRuleDetailScreen(
            state = state,
            onRetry = viewModel.ruleDetail::retry,
            onBack = navigator::navigateUp,
        )
    }
}

@Composable
fun ForumRuleDetailScreen(
    state: LoadState<ForumRuleDetail>,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GlassScaffold(
        modifier = modifier,
        topBar = {
            GlassTopBar(
                title = { GlassTopBarTitle(text = stringResource(id = R.string.title_forum_rule)) },
                navigationIcon = { BackNavigationIcon(onBackPressed = onBack) }
            )
        }
    ) { contentPadding ->
        LoadStateScreen(
            state = state,
            onRetry = onRetry,
            modifier = Modifier.fillMaxSize(),
            isEmpty = { it.rules.isEmpty() },
        ) { detail ->
            ForumRuleDetailContent(detail, contentPadding)
        }
    }
}

@Composable
private fun ForumRuleDetailContent(detail: ForumRuleDetail, contentPadding: PaddingValues) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(text = detail.title, style = MaterialTheme.typography.h5)
        UserHeader(
            avatar = {
                Avatar(
                    data = StringUtil.getAvatarUrl(detail.author.portrait),
                    size = Sizes.Small,
                    contentDescription = null
                )
            },
            name = {
                Text(
                    text = StringUtil.getUsernameAnnotatedString(
                        LocalContext.current,
                        detail.author.name,
                        detail.author.nameShow,
                        LocalContentColor.current
                    )
                )
            },
            desc = (@Composable {
                Text(text = detail.publishTime)
            }).takeIf { detail.publishTime.isNotEmpty() }
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ProvideTextStyle(value = MaterialTheme.typography.body1) {
                Text(text = detail.preface)
                detail.rules.fastForEach { rule ->
                    if (rule.title.isNotEmpty()) {
                        Text(
                            text = rule.title,
                            style = MaterialTheme.typography.subtitle1
                        )
                    }
                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        rule.content.fastForEach { render ->
                            render.Render()
                        }
                    }
                }
            }
        }
    }
}

private val previewDetail = ForumRuleDetail(
    title = "本吧吧规",
    publishTime = "2026-10-31",
    preface = "为维护良好的讨论氛围，请遵守以下规定。",
    author = ForumRuleAuthor(name = "bazhu", nameShow = "吧主", portrait = ""),
    rules = persistentListOf(
        ForumRuleItem("一、发帖规范", persistentListOf(TextContentRender("标题需清楚描述帖子内容。"))),
        ForumRuleItem("二、禁止行为", persistentListOf(TextContentRender("禁止广告、引战和人身攻击。"))),
    ),
)

@Preview("ForumRuleDetailScreen", showBackground = true)
@Composable
private fun PreviewForumRuleDetailScreen() {
    TiebaLiteTheme {
        ForumRuleDetailScreen(state = LoadState.Success(previewDetail), onRetry = {}, onBack = {})
    }
}

@Preview("ForumRuleDetailScreen Loading", showBackground = true)
@Composable
private fun PreviewForumRuleDetailScreenLoading() {
    TiebaLiteTheme {
        ForumRuleDetailScreen(state = LoadState.Loading, onRetry = {}, onBack = {})
    }
}

@Preview("ForumRuleDetailScreen Error", showBackground = true)
@Composable
private fun PreviewForumRuleDetailScreenError() {
    TiebaLiteTheme {
        ForumRuleDetailScreen(
            state = LoadState.Error(IllegalStateException("加载失败")),
            onRetry = {},
            onBack = {},
        )
    }
}
