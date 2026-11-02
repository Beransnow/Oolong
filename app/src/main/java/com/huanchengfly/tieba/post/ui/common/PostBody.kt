package com.huanchengfly.tieba.post.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withAnnotation
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.huanchengfly.tieba.post.R
import com.huanchengfly.tieba.post.model.PostContent
import com.huanchengfly.tieba.post.model.SubPost
import com.huanchengfly.tieba.post.model.TextSpan
import com.huanchengfly.tieba.post.models.PhotoViewData
import com.huanchengfly.tieba.post.ui.common.theme.compose.ExtendedTheme
import com.huanchengfly.tieba.post.ui.common.theme.compose.SmoothCornerShape
import com.huanchengfly.tieba.post.ui.common.windowsizeclass.LocalWindowSizeClass
import com.huanchengfly.tieba.post.ui.common.windowsizeclass.WindowWidthSizeClass
import com.huanchengfly.tieba.post.ui.navigation.WebViewKey
import com.huanchengfly.tieba.post.ui.page.LocalNavigator
import com.huanchengfly.tieba.post.ui.widgets.compose.NetworkImage
import com.huanchengfly.tieba.post.ui.widgets.compose.VideoPlayer
import com.huanchengfly.tieba.post.ui.widgets.compose.VoicePlayer
import com.huanchengfly.tieba.post.utils.EmoticonUtil.emoticonString
import com.huanchengfly.tieba.post.utils.ImageUtil
import com.huanchengfly.tieba.post.utils.StringUtil
import com.huanchengfly.tieba.post.utils.appPreferences
import kotlinx.collections.immutable.ImmutableList

/**
 * The body of a post, block by block.
 *
 * @param photoViewData what opening [PostContent.Image] in the image viewer needs, or null to
 *   show it without opening
 */
@Composable
fun PostBody(
    content: ImmutableList<PostContent>,
    modifier: Modifier = Modifier,
    photoViewData: (PostContent.Image) -> PhotoViewData? = { null },
    fontSize: TextUnit = 15.sp,
    lineHeight: TextUnit = TextUnit.Unspecified,
    blockSpacing: Dp = 8.dp,
    textPadding: PaddingValues = PaddingValues(0.dp),
) {
    // Images run edge to edge of the body; padding the other blocks lets a caller bleed them
    val padded = Modifier.padding(textPadding)
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(blockSpacing),
    ) {
        content.forEach { block ->
            when (block) {
                is PostContent.Text -> Box(padded) { PostText(block.spans, fontSize, lineHeight) }
                is PostContent.Image -> PostImage(block, photoViewData(block), rounded = textPadding == PaddingValues(0.dp))
                is PostContent.Video -> Box(padded) { PostVideo(block) }
                is PostContent.Voice -> Box(padded) { PostVoice(block) }
            }
        }
    }
}

@Composable
private fun PostText(spans: ImmutableList<TextSpan>, fontSize: TextUnit, lineHeight: TextUnit) {
    val accent = ExtendedTheme.colors.accent
    val videoTag = stringResource(id = R.string.tag_video)
    val warning = MaterialTheme.colors.error
    val actualHost = stringResource(id = R.string.link_actual_host)
    val text = remember(spans, accent, videoTag, warning, actualHost) { spans.toAnnotatedString(accent, videoTag, warning, actualHost) }
    PbContentText(
        text = text,
        fontSize = fontSize,
        lineHeight = lineHeight,
        style = MaterialTheme.typography.body1,
        lineSpacing = if (lineHeight == TextUnit.Unspecified) 0.8.sp else 0.sp,
    )
}

/** The size an image or video takes: the full width on phones, half of it on wider windows. */
@Composable
private fun mediaWidthFraction(): Float =
    if (LocalWindowSizeClass.current.widthSizeClass == WindowWidthSizeClass.Compact) 1f else 0.5f

@Composable
private fun PostImage(image: PostContent.Image, photoViewData: PhotoViewData?, rounded: Boolean = true) {
    val context = LocalContext.current
    val url = remember(image) {
        ImageUtil.getUrl(context, true, image.originUrl, *image.urls.toTypedArray())
    }
    NetworkImage(
        imageUri = url,
        contentDescription = null,
        modifier = Modifier
            .clip(SmoothCornerShape(if (rounded) context.appPreferences.radius.dp else 0.dp))
            .fillMaxWidth(mediaWidthFraction())
            .aspectRatio(image.aspectRatio),
        photoViewData = photoViewData,
        contentScale = ContentScale.Crop,
    )
}

@Composable
private fun PostVideo(video: PostContent.Video) {
    if (video.coverUrl.isBlank()) return
    val context = LocalContext.current
    val navigator = LocalNavigator.current
    val modifier = Modifier
        .clip(SmoothCornerShape(context.appPreferences.radius.dp))
        .fillMaxWidth(mediaWidthFraction())
        .aspectRatio(video.width * 1f / video.height)
    if (video.url.isNotBlank()) {
        // Taps belong to the player, not to the post around it
        Box(
            modifier = Modifier.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
            )
        ) {
            VideoPlayer(videoUrl = video.url, thumbnailUrl = video.coverUrl, modifier = modifier)
        }
    } else {
        AsyncImage(
            model = video.coverUrl,
            contentDescription = stringResource(id = R.string.desc_video),
            modifier = modifier.clickable { navigator.navigate(WebViewKey(video.webUrl)) },
            contentScale = ContentScale.Crop,
        )
    }
}

@Composable
private fun PostVoice(voice: PostContent.Voice) {
    val url = remember(voice) {
        "https://tiebac.baidu.com/c/p/voice?voice_md5=${voice.md5}&play_from=pb_voice_play"
    }
    VoicePlayer(url = url, duration = voice.duration)
}

private val PostContent.Image.aspectRatio: Float
    get() = if (width > 0 && height > 0) width * 1f / height else 1f

/**
 * Inline text with links, mentions and video links in [accent], marked for [PbContentText] to
 * open, and emoticons as inline images. A link whose text names another site is followed by
 * where it really goes, in [warning], written with [actualHostFormat].
 */
@OptIn(ExperimentalTextApi::class)
fun List<TextSpan>.toAnnotatedString(
    accent: Color,
    videoTag: String,
    warning: Color,
    actualHostFormat: String,
): AnnotatedString =
    buildAnnotatedString {
        forEach { span ->
            when (span) {
                is TextSpan.Plain -> append(span.text)
                is TextSpan.Emoticon -> append(span.text.emoticonString)
                is TextSpan.Link -> {
                    appendInlineContent("link_icon", alternateText = "🔗")
                    withAnnotation(tag = "url", annotation = span.url) {
                        withStyle(SpanStyle(color = accent)) { append(span.text) }
                        span.actualHost?.let { host ->
                            withStyle(SpanStyle(color = warning)) { append(actualHostFormat.format(host)) }
                        }
                    }
                }

                is TextSpan.Mention -> withAnnotation(tag = "user", annotation = "${span.userId}") {
                    withStyle(SpanStyle(color = accent)) { append(span.text) }
                }

                is TextSpan.VideoLink -> {
                    appendInlineContent("video_icon", alternateText = "🎥")
                    withAnnotation(tag = "url", annotation = span.url) {
                        withStyle(SpanStyle(color = accent)) {
                            append(videoTag)
                            append(span.text)
                        }
                    }
                }
            }
        }
    }

/**
 * A reply inside a post as one line: the author's name in [nameColor], a thread author tag when
 * [isLz], then what they said. Only the text of the reply is kept.
 */
@OptIn(ExperimentalTextApi::class)
@Composable
fun rememberSubPostText(subPost: SubPost, isLz: Boolean, nameColor: Color = ExtendedTheme.colors.emphasis): AnnotatedString {
    val context = LocalContext.current
    val accent = ExtendedTheme.colors.accent
    val videoTag = stringResource(id = R.string.tag_video)
    val warning = MaterialTheme.colors.error
    val actualHost = stringResource(id = R.string.link_actual_host)
    return remember(subPost, isLz, nameColor, accent, videoTag, warning, actualHost) {
        buildAnnotatedString {
            withAnnotation("user", "${subPost.author.id}") {
                withStyle(SpanStyle(color = nameColor, fontWeight = FontWeight.Bold)) {
                    append(
                        StringUtil.getUsernameAnnotatedString(context, subPost.author.name, subPost.author.nameShow)
                    )
                }
                if (isLz) appendInlineContent("Lz")
                append(": ")
            }
            subPost.content.forEach { block ->
                if (block is PostContent.Text) append(block.spans.toAnnotatedString(accent, videoTag, warning, actualHost))
            }
        }
    }
}
