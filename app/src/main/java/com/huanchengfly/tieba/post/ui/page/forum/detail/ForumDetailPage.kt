package com.huanchengfly.tieba.post.ui.page.forum.detail

import android.graphics.Typeface
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.huanchengfly.tieba.post.R
import com.huanchengfly.tieba.post.arch.LoadStatus
import com.huanchengfly.tieba.post.model.ForumIntro
import com.huanchengfly.tieba.post.ui.common.theme.compose.Corners
import com.huanchengfly.tieba.post.ui.common.theme.compose.ExtendedTheme
import com.huanchengfly.tieba.post.ui.common.theme.compose.TiebaLiteTheme
import com.huanchengfly.tieba.post.ui.navigation.ForumDetailKey
import com.huanchengfly.tieba.post.ui.navigation.Navigator
import com.huanchengfly.tieba.post.ui.page.LocalNavigator
import com.huanchengfly.tieba.post.ui.widgets.compose.Avatar
import com.huanchengfly.tieba.post.ui.widgets.compose.BackNavigationIcon
import com.huanchengfly.tieba.post.ui.widgets.compose.Chip
import com.huanchengfly.tieba.post.ui.widgets.compose.Container
import com.huanchengfly.tieba.post.ui.widgets.compose.ErrorScreen
import com.huanchengfly.tieba.post.ui.widgets.compose.GlassScaffold
import com.huanchengfly.tieba.post.ui.widgets.compose.GlassTopBar
import com.huanchengfly.tieba.post.ui.widgets.compose.GlassTopBarTitle
import com.huanchengfly.tieba.post.ui.widgets.compose.HorizontalDivider
import com.huanchengfly.tieba.post.ui.widgets.compose.Sizes
import com.huanchengfly.tieba.post.ui.widgets.compose.states.StateScreen
import com.huanchengfly.tieba.post.utils.StringUtil.getShortNumeralString

@Composable
fun ForumDetailPage(
    key: ForumDetailKey,
    navigator: Navigator = LocalNavigator.current,
    viewModel: ForumDetailViewModel = hiltViewModel<ForumDetailViewModel, ForumDetailViewModel.Factory> { it.create(key) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ForumDetailScreen(state = state, onBack = { navigator.navigateUp() }, onReload = viewModel::refresh)
}

/** A forum's avatar, counts, slogan and introduction. */
@Composable
fun ForumDetailScreen(
    state: ForumDetailState,
    onBack: () -> Unit,
    onReload: () -> Unit,
) {
    val error = (state.load as? LoadStatus.Error)?.error
    StateScreen(
        isEmpty = state.intro == null,
        isError = error != null,
        isLoading = state.load == LoadStatus.Loading,
        onReload = onReload,
        errorScreen = { ErrorScreen(error = error) },
        modifier = Modifier.fillMaxSize(),
    ) {
        GlassScaffold(
            topBar = {
                GlassTopBar(
                    title = {
                        GlassTopBarTitle(text = stringResource(id = R.string.title_forum_info))
                    },
                    navigationIcon = {
                        BackNavigationIcon(onBackPressed = onBack)
                    }
                )
            }
        ) { paddingValues ->
            Container {
                state.intro?.let {
                    ForumDetailContent(
                        forumInfo = it,
                        contentPadding = paddingValues,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun ForumDetailContent(
    forumInfo: ForumIntro,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(contentPadding)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Avatar(
                data = forumInfo.avatar,
                size = Sizes.Medium,
                contentDescription = null,
            )
            Text(
                text = stringResource(id = R.string.title_forum, forumInfo.name),
                style = MaterialTheme.typography.h6
            )
        }
        Row(
            modifier = Modifier
                .clip(Corners.sm)
                .background(color = ExtendedTheme.colors.chip)
                .padding(vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StatCardItem(
                statNum = forumInfo.memberCount,
                statText = stringResource(id = R.string.forum_stat_members)
            )
            HorizontalDivider(color = Color(if (ExtendedTheme.colors.isNightMode) 0xFF808080 else 0xFFDEDEDE))
            StatCardItem(
                statNum = forumInfo.threadCount,
                statText = stringResource(id = R.string.text_stat_threads)
            )
        }
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Chip(text = stringResource(id = R.string.title_forum_intro))
            Column {
                Text(text = forumInfo.slogan, style = MaterialTheme.typography.body1)
                Text(text = forumInfo.intro, style = MaterialTheme.typography.body1)
            }
        }
    }
}

@Composable
private fun RowScope.StatCardItem(
    statNum: Int,
    statText: String,
) {
    Column(
        modifier = Modifier.weight(1f),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = statNum.getShortNumeralString(),
            fontSize = 20.sp,
            fontFamily = FontFamily(
                Typeface.createFromAsset(
                    LocalContext.current.assets,
                    "bebas.ttf"
                )
            ),
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = statText,
            fontSize = 12.sp,
            color = ExtendedTheme.colors.textSecondary
        )
    }
}

@Preview("ForumDetailPage", backgroundColor = 0xFFFFFFFF, showBackground = true)
@Composable
fun PreviewForumDetailPage() {
    TiebaLiteTheme {
        ForumDetailContent(
            forumInfo = ForumDetailPreviewData.intro,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/** A sample forum, shared by the preview above and the screenshot tests. */
internal object ForumDetailPreviewData {
    val intro = ForumIntro(
        name = "minecraft",
        avatar = "",
        memberCount = 2520287,
        threadCount = 31531580,
        slogan = "位于百度贴吧的像素点之家",
        intro = "minecraft……",
    )
}
