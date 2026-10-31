package com.huanchengfly.tieba.post.repository

import com.huanchengfly.tieba.post.api.models.protos.PbContent
import com.huanchengfly.tieba.post.model.PostContent
import com.huanchengfly.tieba.post.model.TextSpan
import com.huanchengfly.tieba.post.model.plainText
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlin.test.Test
import kotlin.test.assertEquals

class PostContentMappingTest {
    private val registered = mutableListOf<Pair<String, String>>()
    private val emoticons = EmoticonRegistry { id, name -> registered += id to name }

    private fun map(vararg pieces: PbContent) = pieces.toList().toPostContent(emoticons)

    @Test
    fun `adjacent inline pieces form one text block`() {
        val content = map(
            PbContent(type = 0, text = "看 "),
            PbContent(type = 4, text = "@楼主", uid = 10),
            PbContent(type = 0, text = " 的链接 "),
            PbContent(type = 1, text = "tieba.baidu.com", link = "https://tieba.baidu.com"),
            PbContent(type = 2, text = "image_emoticon25", c = "滑稽"),
        )

        val expected = PostContent.Text(
            listOf<TextSpan>(
                TextSpan.Plain("看 "),
                TextSpan.Mention("@楼主", userId = 10),
                TextSpan.Plain(" 的链接 "),
                TextSpan.Link("tieba.baidu.com", url = "https://tieba.baidu.com"),
                TextSpan.Emoticon(id = "image_emoticon25", name = "滑稽"),
            ).toImmutableList()
        )
        assertEquals(listOf<PostContent>(expected), content)
        assertEquals("看 @楼主 的链接 tieba.baidu.com#(滑稽)", content.plainText)
    }

    @Test
    fun `emoticons are registered so they can be used in replies`() {
        map(PbContent(type = 2, text = "image_emoticon25", c = "滑稽"))
        assertEquals(listOf("image_emoticon25" to "滑稽"), registered)
    }

    @Test
    fun `text types shown as plain text`() {
        val content = map(
            PbContent(type = 9, text = "a"),
            PbContent(type = 27, text = "b"),
            PbContent(type = 35, text = "c"),
            PbContent(type = 40, text = "d"),
        )
        assertEquals("abcd", content.plainText)
        assertEquals(1, content.size)
    }

    @Test
    fun `an image splits the text around it and keeps its urls in decreasing size`() {
        val content = map(
            PbContent(type = 0, text = "前"),
            PbContent(
                type = 3,
                originSrc = "https://tiebapic.baidu.com/forum/pic/item/abc123.jpg?tbpicau=x",
                bigCdnSrc = "big-cdn", bigSrc = "big", dynamic_ = "dynamic",
                cdnSrc = "cdn", cdnSrcActive = "cdn-active", src = "src",
                bsize = "1080,720", showOriginalBtn = 1, originSize = 2048,
            ),
            PbContent(type = 0, text = "后"),
        )

        assertEquals(3, content.size)
        val image = content[1] as PostContent.Image
        assertEquals("abc123", image.id)
        assertEquals(listOf("big-cdn", "big", "dynamic", "cdn", "cdn-active", "src"), image.urls)
        assertEquals("https://tiebapic.baidu.com/forum/pic/item/abc123.jpg?tbpicau=x", image.originUrl)
        assertEquals(1080 to 720, image.width to image.height)
        assertEquals(true, image.showOriginButton)
        assertEquals(2048, image.originSize)
        assertEquals("前\n[图片]\n后", content.plainText)
    }

    @Test
    fun `a type 20 image only has its src`() {
        val image = map(PbContent(type = 20, src = "https://host/meme/xyz.jpg", bsize = "200,200"))
            .single() as PostContent.Image
        assertEquals("xyz", image.id)
        assertEquals(listOf("https://host/meme/xyz.jpg"), image.urls)
        assertEquals("https://host/meme/xyz.jpg", image.originUrl)
    }

    @Test
    fun `a video with a cover plays in place, one without is a web link`() {
        val content = map(
            PbContent(type = 5, link = "https://video/v.mp4", src = "cover", text = "https://web/v", bsize = "640,360"),
            PbContent(type = 5, text = "https://web/only"),
        )

        assertEquals(PostContent.Video("https://video/v.mp4", "cover", "https://web/v", 640, 360), content[0])
        assertEquals(
            PostContent.Text(persistentListOf(TextSpan.VideoLink("https://web/only", "https://web/only"))),
            content[1],
        )
        assertEquals("[视频]\nhttps://web/only", content.plainText)
    }

    @Test
    fun `voice messages and unknown types`() {
        val content = map(PbContent(type = 10, voiceMD5 = "md5", duringTime = 3000), PbContent(type = 999, text = "?"))
        assertEquals(listOf<PostContent>(PostContent.Voice("md5", 3000)), content)
        assertEquals("[语音]", content.plainText)
    }

    @Test
    fun `a malformed size is read as zero rather than crashing`() {
        val image = map(PbContent(type = 3, originSrc = "o.jpg", bsize = "")).single() as PostContent.Image
        assertEquals(0 to 0, image.width to image.height)
    }
}
