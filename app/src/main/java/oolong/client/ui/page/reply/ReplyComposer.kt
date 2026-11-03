package oolong.client.ui.page.reply

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import kotlinx.collections.immutable.ImmutableList
import oolong.client.R
import oolong.client.ui.common.theme.compose.Corners
import oolong.client.ui.common.theme.compose.ExtendedTheme
import oolong.client.ui.common.theme.compose.Symbols
import oolong.client.ui.widgets.compose.Avatar
import oolong.client.ui.widgets.compose.Sizes
import oolong.client.utils.Emoticon
import oolong.client.utils.EmoticonManager
import oolong.client.utils.StringUtil

/** Whom a reply answers, shown as a chip at the top of the composer; null answers the thread. */
@Immutable
data class ReplyTo(
    val name: String,
    val portrait: String,
    val floor: Int,
    /** The words replied to, shown when the composer fills the screen. */
    val quote: String,
)

/** What the person does in the composer. */
sealed interface ReplyComposerEvent {
    /** Answer the thread instead of [ReplyTo]. */
    data object ReplyToThread : ReplyComposerEvent
    data object ToggleFullScreen : ReplyComposerEvent
    data object DismissNotice : ReplyComposerEvent
    data object OpenOfficialClient : ReplyComposerEvent
    data class ShowPanel(val panel: ReplyPanelType) : ReplyComposerEvent
    data object PickImages : ReplyComposerEvent
    data class RemoveImage(val index: Int) : ReplyComposerEvent
    data class SetOriginalImages(val original: Boolean) : ReplyComposerEvent
    data class SetWithTail(val withTail: Boolean) : ReplyComposerEvent
    data object EditText : ReplyComposerEvent
    data object Send : ReplyComposerEvent
}

private val ComposerIconSize = 22.dp
private val ChipShape = Corners.full

/**
 * The inside of the reply composer: whom it answers, the text, the pictures and what the reply
 * carries along, and the tools above the keyboard. In [fullScreen] the text takes the room left
 * and the words replied to are quoted under the header.
 *
 * @param handle the drag handle at the top, which the host wires to resize the composer
 * @param hasTail whether a little tail is set in settings, which shows the chip that turns it off
 */
@Composable
fun ReplyComposerContent(
    replyTo: ReplyTo?,
    editor: ReplyEditorState,
    state: ReplyState,
    fullScreen: Boolean,
    showNotice: Boolean,
    hasTail: Boolean,
    canAttachImages: Boolean,
    onEvent: (ReplyComposerEvent) -> Unit,
    handle: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ExtendedTheme.colors
    Column(modifier = modifier) {
        handle()
        // Whom it answers; a reply to the thread needs no line for it
        if (replyTo != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ReplyToChip(replyTo, onDrop = { onEvent(ReplyComposerEvent.ReplyToThread) })
            }
        }
        if (fullScreen && replyTo != null && replyTo.quote.isNotBlank()) {
            Text(
                text = replyTo.quote,
                fontSize = 13.5.sp,
                lineHeight = 21.sp,
                color = colors.textSecondary,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .fillMaxWidth(),
            )
        }
        val error = state.error
        when {
            error != null -> ReplyErrorRow(error, onEvent)
            showNotice -> NoticeRow(
                icon = Symbols.Warning,
                text = stringResource(id = R.string.reply_notice_risk),
            ) {
                RowButton(stringResource(id = R.string.reply_official_client)) {
                    onEvent(ReplyComposerEvent.OpenOfficialClient)
                }
                ComposerIconButton(
                    icon = Symbols.Close,
                    contentDescription = stringResource(id = R.string.reply_notice_dismiss),
                    onClick = { onEvent(ReplyComposerEvent.DismissNotice) },
                    size = 28.dp,
                    iconSize = 16.dp,
                )
            }
        }
        ReplyEditor(
            state = editor,
            hint = if (replyTo != null) stringResource(id = R.string.hint_reply, replyTo.name)
            else stringResource(id = R.string.reply_hint_thread),
            enabled = !state.busy,
            onFocus = { onEvent(ReplyComposerEvent.EditText) },
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .fillMaxWidth()
                .then(if (fullScreen) Modifier.weight(1f) else Modifier.heightIn(min = 26.dp, max = MaxCompactTextHeight)),
        )
        if (state.images.isNotEmpty()) {
            PictureStrip(
                images = state.images,
                onAdd = { onEvent(ReplyComposerEvent.PickImages) },
                onRemove = { onEvent(ReplyComposerEvent.RemoveImage(it)) },
            )
        }
        if (state.images.isNotEmpty() || hasTail) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (state.images.isNotEmpty()) {
                    ToggleChip(
                        text = stringResource(id = R.string.origin_image),
                        icon = Symbols.Hd,
                        checked = state.originalImages,
                        onCheckedChange = { onEvent(ReplyComposerEvent.SetOriginalImages(it)) },
                    )
                }
                if (hasTail) {
                    ToggleChip(
                        text = stringResource(id = R.string.reply_little_tail),
                        icon = Symbols.Gesture,
                        checked = state.withTail,
                        onCheckedChange = { onEvent(ReplyComposerEvent.SetWithTail(it)) },
                    )
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 8.dp, top = 2.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ComposerIconButton(
                icon = Symbols.Mood,
                contentDescription = stringResource(id = R.string.insert_emotions),
                selected = state.panel == ReplyPanelType.EMOJI,
                onClick = {
                    onEvent(
                        ReplyComposerEvent.ShowPanel(
                            if (state.panel == ReplyPanelType.EMOJI) ReplyPanelType.NONE else ReplyPanelType.EMOJI
                        )
                    )
                },
            )
            ComposerIconButton(
                icon = Symbols.Image,
                contentDescription = stringResource(id = R.string.insert_photo),
                enabled = canAttachImages && state.images.size < MaxImages,
                badge = state.images.size.takeIf { it > 0 },
                onClick = { onEvent(ReplyComposerEvent.PickImages) },
            )
            Spacer(modifier = Modifier.weight(1f))
            if (fullScreen) {
                Icon(
                    imageVector = Symbols.Check,
                    contentDescription = null,
                    tint = colors.textSecondary,
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    text = stringResource(id = R.string.reply_draft_saved),
                    fontSize = 11.5.sp,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(start = 4.dp, end = 10.dp),
                )
            }
            SendButton(
                busy = state.busy,
                enabled = !state.busy && (editor.text.isNotBlank() || state.images.isNotEmpty()),
                onClick = { onEvent(ReplyComposerEvent.Send) },
            )
        }
    }
}

/**
 * The reply capsule's content at rest: the viewer's avatar and the invitation to reply, or the
 * start of the draft left unsent.
 */
@Composable
fun RowScope.ReplyCapsuleContent(portrait: String, draft: String) {
    val colors = ExtendedTheme.colors
    Avatar(
        data = StringUtil.getAvatarUrl(portrait),
        size = Sizes.Tiny,
        contentDescription = null,
    )
    Text(
        text = if (draft.isBlank()) {
            AnnotatedString(stringResource(id = R.string.tip_reply_thread))
        } else {
            buildAnnotatedString {
                withStyle(SpanStyle(color = colors.text, fontWeight = FontWeight.Medium)) {
                    append(stringResource(id = R.string.reply_draft))
                }
                append("  ")
                append(draft.replace('\n', ' '))
            }
        },
        fontSize = 14.sp,
        color = colors.textSecondary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(1f),
    )
}

/** At most this many pictures go in one reply. */
const val MaxImages = 9

/** About five lines; past that the text scrolls inside the field, or the composer goes full screen. */
private val MaxCompactTextHeight = 140.dp

@Composable
private fun ReplyToChip(replyTo: ReplyTo, onDrop: () -> Unit) {
    val colors = ExtendedTheme.colors
    Row(
        modifier = Modifier
            .height(28.dp)
            .clip(ChipShape)
            .background(colors.chip)
            .padding(start = 8.dp, end = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            imageVector = Symbols.Reply,
            contentDescription = null,
            tint = colors.textSecondary,
            modifier = Modifier.size(16.dp),
        )
        Avatar(
            data = StringUtil.getAvatarUrl(replyTo.portrait),
            size = 18.dp,
            contentDescription = null,
        )
        Text(
            text = replyTo.name,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Medium,
            color = colors.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        Text(
            text = stringResource(id = R.string.reply_floor, replyTo.floor),
            fontSize = 12.sp,
            color = colors.textSecondary,
        )
        ComposerIconButton(
            icon = Symbols.Close,
            contentDescription = stringResource(id = R.string.reply_to_thread),
            onClick = onDrop,
            size = 24.dp,
            iconSize = 14.dp,
        )
    }
}

@Composable
private fun ReplyErrorRow(error: ReplyError, onEvent: (ReplyComposerEvent) -> Unit) {
    val unknown = stringResource(id = R.string.error_unknown)
    val text = when (error) {
        is ReplyError.Verification -> stringResource(id = R.string.reply_error_verification)
        is ReplyError.Upload -> stringResource(id = R.string.reply_error_upload, error.message.ifEmpty { unknown })
        is ReplyError.Send -> stringResource(id = R.string.reply_error_send, error.message.ifEmpty { unknown })
    }
    NoticeRow(
        icon = if (error is ReplyError.Verification) Symbols.VerifiedUser else Symbols.Warning,
        text = text,
    ) {
        RowButton(stringResource(id = R.string.reply_official_client)) {
            onEvent(ReplyComposerEvent.OpenOfficialClient)
        }
    }
}

/** A line under the header for what the person should know before sending, with what to do about it. */
@Composable
private fun NoticeRow(
    icon: ImageVector,
    text: String,
    actions: @Composable () -> Unit,
) {
    val colors = ExtendedTheme.colors
    Row(
        modifier = Modifier
            .padding(horizontal = 12.dp, vertical = 2.dp)
            .fillMaxWidth()
            .heightIn(min = 36.dp)
            .clip(Corners.lg)
            .background(colors.chip)
            .padding(start = 10.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = WarnColor, modifier = Modifier.size(16.dp))
        Text(
            text = text,
            fontSize = 12.5.sp,
            lineHeight = 17.sp,
            color = colors.text,
            modifier = Modifier.weight(1f),
        )
        actions()
    }
}

private val WarnColor = Color(0xFFD9822B)

@Composable
private fun RowButton(text: String, onClick: () -> Unit) {
    val colors = ExtendedTheme.colors
    Text(
        text = text,
        fontSize = 12.sp,
        color = colors.text,
        maxLines = 1,
        modifier = Modifier
            .clip(ChipShape)
            .background(colors.sheet)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    )
}

@Composable
private fun ToggleChip(
    text: String,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val colors = ExtendedTheme.colors
    val background = if (checked) colors.text else colors.chip
    val content = if (checked) colors.sheet else colors.textSecondary
    Row(
        modifier = Modifier
            .height(26.dp)
            .clip(ChipShape)
            .background(background)
            .clickable(role = Role.Checkbox) { onCheckedChange(!checked) }
            .padding(start = 7.dp, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            imageVector = if (checked) Symbols.Check else icon,
            contentDescription = null,
            tint = content,
            modifier = Modifier.size(15.dp),
        )
        Text(text = text, fontSize = 12.sp, color = content)
    }
}

@Composable
internal fun ComposerIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selected: Boolean = false,
    badge: Int? = null,
    size: androidx.compose.ui.unit.Dp = 38.dp,
    iconSize: androidx.compose.ui.unit.Dp = ComposerIconSize,
) {
    val colors = ExtendedTheme.colors
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(if (selected) colors.chip else Color.Transparent)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = when {
                !enabled -> colors.textDisabled
                selected -> colors.text
                else -> colors.textSecondary
            },
            modifier = Modifier.size(iconSize),
        )
        if (badge != null) {
            Text(
                text = "$badge",
                fontSize = 10.sp,
                lineHeight = 15.sp,
                fontWeight = FontWeight.Bold,
                color = colors.sheet,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 3.dp, end = 3.dp)
                    .size(15.dp)
                    .clip(CircleShape)
                    .background(colors.text),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}

@Composable
private fun SendButton(busy: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val colors = ExtendedTheme.colors
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(if (enabled || busy) colors.text else colors.text.copy(alpha = 0.25f))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (busy) {
            CircularProgressIndicator(
                color = colors.sheet,
                strokeWidth = 2.dp,
                modifier = Modifier.size(16.dp),
            )
        } else {
            Icon(
                imageVector = Symbols.ArrowUpward,
                contentDescription = stringResource(id = R.string.send_reply),
                tint = colors.sheet,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
private fun ColumnScope.PictureStrip(
    images: ImmutableList<String>,
    onAdd: () -> Unit,
    onRemove: (Int) -> Unit,
) {
    val colors = ExtendedTheme.colors
    LazyRow(
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itemsIndexed(images, key = { _, uri -> uri }) { index, uri ->
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(Corners.sm)
                    .background(colors.chip)
            ) {
                AsyncImage(
                    model = uri,
                    contentDescription = stringResource(id = R.string.desc_image),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(60.dp),
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(3.dp)
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.55f))
                        .clickable(role = Role.Button) { onRemove(index) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Symbols.Close,
                        contentDescription = stringResource(id = R.string.desc_remove_image),
                        tint = Color.White,
                        modifier = Modifier.size(12.dp),
                    )
                }
            }
        }
        if (images.size < MaxImages) {
            item {
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .clip(Corners.sm)
                        .background(colors.chip)
                        .clickable(role = Role.Button, onClick = onAdd),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Symbols.Add,
                        contentDescription = stringResource(id = R.string.desc_add_image),
                        tint = colors.textSecondary,
                    )
                }
            }
        }
    }
}

/** The emoticons, shown in place of the keyboard; tapping one inserts its code. */
@Composable
fun EmoticonPanel(
    onEmoticonClick: (Emoticon) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val emoticons = remember { EmoticonManager.getAllEmoticon().filter { it.name.isNotEmpty() } }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(44.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(8.dp),
        modifier = modifier,
    ) {
        items(emoticons, key = { it.id }) { emoticon ->
            Image(
                painter = rememberDrawablePainter(
                    drawable = EmoticonManager.getEmoticonDrawable(context, emoticon.id)
                ),
                contentDescription = stringResource(id = R.string.emoticon, emoticon.name),
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .size(44.dp)
                    .clip(Corners.sm)
                    .clickable { onEmoticonClick(emoticon) }
                    .padding(8.dp),
            )
        }
    }
}
