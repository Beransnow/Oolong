package com.huanchengfly.tieba.post.repository

import com.huanchengfly.tieba.post.api.models.protos.PbContent
import com.huanchengfly.tieba.post.model.PostContent
import com.huanchengfly.tieba.post.model.TextSpan
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

/** Learns the emoticons that appear in posts, so they can be used when replying. */
fun interface EmoticonRegistry {
    fun register(id: String, name: String)
}

/**
 * The blocks of a post body. Adjacent inline pieces are merged into one text block; pieces of
 * unknown types are left out.
 */
fun List<PbContent>.toPostContent(emoticons: EmoticonRegistry): ImmutableList<PostContent> {
    val blocks = mutableListOf<PostContent>()
    val spans = mutableListOf<TextSpan>()
    fun flushText() {
        if (spans.isNotEmpty()) {
            blocks += PostContent.Text(spans.toImmutableList())
            spans.clear()
        }
    }
    for (piece in this) {
        val span = piece.toTextSpan()
        if (span != null) {
            if (span is TextSpan.Emoticon) emoticons.register(span.id, span.name)
            spans += span
            continue
        }
        val block = piece.toBlock() ?: continue
        flushText()
        blocks += block
    }
    flushText()
    return blocks.toImmutableList()
}

private fun PbContent.toTextSpan(): TextSpan? = when (type) {
    // 35: ad-like text, 40: keyword links in replies; both are shown as plain text
    0, 9, 27, 35, 40 -> TextSpan.Plain(text)
    1 -> TextSpan.Link(text, link)
    2 -> TextSpan.Emoticon(id = text, name = c)
    4 -> TextSpan.Mention(text, uid)
    5 -> if (src.isBlank()) TextSpan.VideoLink(text, url = text) else null
    else -> null
}

private fun PbContent.toBlock(): PostContent? {
    val (width, height) = size()
    return when (type) {
        3 -> PostContent.Image(
            id = picId(originSrc),
            urls = persistentListOf(bigCdnSrc, bigSrc, dynamic_, cdnSrc, cdnSrcActive, src),
            originUrl = originSrc,
            width = width,
            height = height,
            showOriginButton = showOriginalBtn == 1,
            originSize = originSize,
        )

        20 -> PostContent.Image(
            id = picId(src),
            urls = persistentListOf(src),
            originUrl = src,
            width = width,
            height = height,
            showOriginButton = showOriginalBtn == 1,
            originSize = originSize,
        )

        5 -> PostContent.Video(url = link, coverUrl = src, webUrl = text, width = width, height = height)
        10 -> PostContent.Voice(md5 = voiceMD5, duration = duringTime)
        else -> null
    }
}

/** `bsize` is `width,height`. */
private fun PbContent.size(): Pair<Int, Int> {
    val parts = bsize.split(",")
    return (parts.getOrNull(0)?.trim()?.toIntOrNull() ?: 0) to (parts.getOrNull(1)?.trim()?.toIntOrNull() ?: 0)
}

/** The file name of an image URL without its `.jpg` extension, which identifies the image. */
internal fun picId(url: String): String =
    url.substringBefore('#').substringBefore('?').substringAfterLast('/').replace(".jpg", "")
