package com.huanchengfly.tieba.post.model

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList

/**
 * One block of a post body. Adjacent inline pieces (text, links, mentions, emoticons) form a
 * single [Text] block; images, videos and voice messages stand on their own.
 *
 * Nothing here depends on the theme: colours and icons are applied when the content is shown.
 */
@Immutable
sealed interface PostContent {
    @Immutable
    data class Text(val spans: ImmutableList<TextSpan>) : PostContent

    /**
     * @param urls the image in decreasing sizes, any of which may be empty; which one is shown
     *   depends on the image quality setting
     */
    @Immutable
    data class Image(
        val id: String,
        val urls: ImmutableList<String>,
        val originUrl: String,
        val width: Int,
        val height: Int,
        val showOriginButton: Boolean,
        val originSize: Int,
    ) : PostContent

    /** A video playable in place, or only openable on the web when [url] is empty. */
    @Immutable
    data class Video(
        val url: String,
        val coverUrl: String,
        val webUrl: String,
        val width: Int,
        val height: Int,
    ) : PostContent

    @Immutable
    data class Voice(val md5: String, val duration: Int) : PostContent
}

@Immutable
sealed interface TextSpan {
    val text: String

    @Immutable
    data class Plain(override val text: String) : TextSpan

    /**
     * @param actualHost where the link really goes, when [text] reads as an address elsewhere;
     * null when the text names no address or the right one
     */
    @Immutable
    data class Link(override val text: String, val url: String, val actualHost: String? = null) : TextSpan

    @Immutable
    data class Mention(override val text: String, val userId: Long) : TextSpan

    /** A link to a video that can only be opened on the web. */
    @Immutable
    data class VideoLink(override val text: String, val url: String) : TextSpan

    /** Shown as an image; [text] is how it is written in plain text, `#(name)`. */
    @Immutable
    data class Emoticon(val id: String, val name: String) : TextSpan {
        override val text: String get() = "#($name)"
    }
}

/** The content as plain text, for copying and for matching block rules. */
val List<PostContent>.plainText: String
    get() = joinToString("\n") { content ->
        when (content) {
            is PostContent.Text -> content.spans.joinToString("") { it.text }
            is PostContent.Image -> "[图片]"
            is PostContent.Video -> "[视频]"
            is PostContent.Voice -> "[语音]"
        }
    }
