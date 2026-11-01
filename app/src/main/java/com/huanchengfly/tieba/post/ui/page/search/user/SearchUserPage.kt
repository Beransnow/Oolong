package com.huanchengfly.tieba.post.ui.page.search.user

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.huanchengfly.tieba.post.R
import com.huanchengfly.tieba.post.model.SearchedUser
import com.huanchengfly.tieba.post.ui.page.search.MatchList
import com.huanchengfly.tieba.post.ui.page.search.MatchesState
import com.huanchengfly.tieba.post.ui.widgets.compose.Avatar
import com.huanchengfly.tieba.post.ui.widgets.compose.Sizes
import com.huanchengfly.tieba.post.utils.StringUtil

/** The users a search found. */
@Composable
fun SearchUserList(
    state: MatchesState<SearchedUser>,
    onRefresh: () -> Unit,
    onOpen: (SearchedUser) -> Unit,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    MatchList(
        state = state,
        exactTitle = stringResource(id = R.string.title_exact_match),
        fuzzyTitle = stringResource(id = R.string.title_fuzzy_match_user),
        onRefresh = onRefresh,
        contentPadding = contentPadding,
    ) { user ->
        SearchUserItem(item = user, onClick = { onOpen(user) })
    }
}

@Composable
private fun SearchUserItem(
    item: SearchedUser,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Avatar(
            data = StringUtil.getAvatarUrl(item.portrait),
            size = Sizes.Medium,
            contentDescription = item.name
        )
        Column(
            modifier = Modifier
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = StringUtil.getUsernameAnnotatedString(LocalContext.current, item.name, item.nameShow),
                style = MaterialTheme.typography.subtitle1
            )
            if (item.intro.isNotEmpty()) {
                Text(
                    text = item.intro,
                    style = MaterialTheme.typography.body2,
                    maxLines = 1
                )
            }
        }
    }
}
