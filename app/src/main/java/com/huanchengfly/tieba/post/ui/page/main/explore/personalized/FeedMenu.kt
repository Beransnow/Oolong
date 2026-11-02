package com.huanchengfly.tieba.post.ui.page.main.explore.personalized

import android.content.Context
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.size
import androidx.compose.material.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.huanchengfly.tieba.post.R
import com.huanchengfly.tieba.post.model.DislikeOption
import com.huanchengfly.tieba.post.ui.common.theme.compose.ExtendedTheme
import com.huanchengfly.tieba.post.ui.navigation.Navigator
import com.huanchengfly.tieba.post.ui.page.LocalNavigator
import com.huanchengfly.tieba.post.ui.widgets.compose.ButtonSwell
import com.huanchengfly.tieba.post.ui.widgets.compose.ContextMenuItem
import com.huanchengfly.tieba.post.ui.widgets.compose.IconButton
import com.huanchengfly.tieba.post.ui.widgets.compose.LocalContextMenus
import com.huanchengfly.tieba.post.utils.TiebaUtil
import com.huanchengfly.tieba.post.utils.launchUrl
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * The ⋯ of a recommended thread, which opens into what the server offers for not wanting it, each
 * done in one tap, then sharing it or copying its link.
 */
@Composable
fun FeedMenu(
    threadId: Long,
    title: String,
    options: ImmutableList<DislikeOption>,
    onDislike: (clickTime: Long, reasons: ImmutableList<DislikeOption>) -> Unit,
) {
    val context = LocalContext.current
    val navigator = LocalNavigator.current
    val menus = LocalContextMenus.current
    var bounds by remember { mutableStateOf(Rect.Zero) }
    var menuId by remember { mutableStateOf<Long?>(null) }
    // The menu holds the icon while it shows, and gives it back swollen
    val open = menus?.isShowing(menuId) == true
    val scale = remember { Animatable(1f) }
    LaunchedEffect(open) {
        if (!open && menuId != null) {
            scale.snapTo(ButtonSwell)
            scale.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 600f))
        }
    }

    IconButton(
        onClick = {
            menus ?: return@IconButton
            val dislikes = dislikeMenuItems(context, navigator, options, onDislike)
            val items = dislikes + shareMenuItems(context, threadId, title).mapIndexed { i, item ->
                if (i == 0) item.copy(startsGroup = dislikes.isNotEmpty()) else item
            }
            menuId = menus.show(items, anchor = bounds, icon = Icons.Rounded.MoreHoriz)
        },
        modifier = Modifier
            .size(24.dp)
            .onGloballyPositioned { bounds = it.boundsInWindow() },
    ) {
        Icon(
            imageVector = Icons.Rounded.MoreHoriz,
            contentDescription = stringResource(id = R.string.btn_more),
            tint = ExtendedTheme.colors.textSecondary,
            modifier = Modifier.graphicsLayer {
                alpha = if (open) 0f else 1f
                scaleX = scale.value
                scaleY = scale.value
            },
        )
    }
}

/**
 * The server's options for not wanting a thread, each done in one tap: those that lead somewhere,
 * such as reporting it, open it instead.
 */
fun dislikeMenuItems(
    context: Context,
    navigator: Navigator,
    options: List<DislikeOption>,
    onDislike: (clickTime: Long, reasons: ImmutableList<DislikeOption>) -> Unit,
): List<ContextMenuItem> {
    val clickTime = System.currentTimeMillis()
    return options.map { option ->
        val schema = option.schema
        ContextMenuItem(
            icon = when {
                schema != null -> Icons.Rounded.Flag
                option.id == BlockForumOption -> Icons.Rounded.Block
                else -> Icons.Rounded.VisibilityOff
            },
            label = option.text.trim(),
        ) {
            if (schema != null) {
                launchUrl(context, navigator, schema)
            } else {
                onDislike(clickTime, persistentListOf(option))
            }
        }
    }
}

/** Sharing thread [threadId], titled [title], or copying its link. */
fun shareMenuItems(context: Context, threadId: Long, title: String): List<ContextMenuItem> {
    val url = "https://tieba.baidu.com/p/$threadId"
    return listOf(
        ContextMenuItem(Icons.Rounded.Share, context.getString(R.string.title_share)) {
            TiebaUtil.shareText(context, url, title.takeIf(String::isNotBlank))
        },
        ContextMenuItem(Icons.Rounded.Link, context.getString(R.string.title_copy_link)) { TiebaUtil.copyText(context, url) },
    )
}

/** The option that keeps the thread's forum out of the recommendations. */
private const val BlockForumOption = 7
