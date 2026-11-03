package oolong.client.ui.page.search.forum

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import oolong.client.R
import oolong.client.model.SearchedForum
import oolong.client.ui.page.search.MatchList
import oolong.client.ui.page.search.MatchesState
import oolong.client.ui.widgets.compose.Avatar
import oolong.client.ui.widgets.compose.Sizes

/** The forums a search found. */
@Composable
fun SearchForumList(
    state: MatchesState<SearchedForum>,
    onRefresh: () -> Unit,
    onOpen: (SearchedForum) -> Unit,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    MatchList(
        state = state,
        exactTitle = stringResource(id = R.string.title_exact_match),
        fuzzyTitle = stringResource(id = R.string.title_fuzzy_match),
        onRefresh = onRefresh,
        contentPadding = contentPadding,
    ) { forum ->
        SearchForumItem(item = forum, onClick = { onOpen(forum) })
    }
}

@Composable
private fun SearchForumItem(
    item: SearchedForum,
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
            data = item.avatar,
            size = Sizes.Medium,
            contentDescription = item.nameShow
        )
        Column(
            modifier = Modifier
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(id = R.string.title_forum, item.nameShow),
                style = MaterialTheme.typography.subtitle1
            )
            if (item.slogan.isNotEmpty()) {
                Text(
                    text = item.slogan,
                    style = MaterialTheme.typography.body2,
                    maxLines = 1
                )
            }
        }
    }
}
