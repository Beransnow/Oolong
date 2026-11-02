package com.huanchengfly.tieba.post.repository

import com.huanchengfly.tieba.post.model.PostContent
import com.huanchengfly.tieba.post.model.TextSpan
import com.huanchengfly.tieba.post.model.User
import kotlinx.collections.immutable.persistentListOf
import kotlin.test.Test
import kotlin.test.assertEquals

class BlockableTest {
    private val someone = User(id = 7, name = "someone", nameShow = "", portrait = "")

    private fun text(vararg spans: TextSpan) = PostContent.Text(persistentListOf(*spans))

    private fun image(sticker: Boolean = false) =
        PostContent.Image("id", persistentListOf("url"), "url", 1, 1, showOriginButton = false, originSize = 0, sticker = sticker)

    private fun of(vararg content: PostContent) = Blockable(someone, content.toList(), BlockPlace.Thread)

    @Test
    fun `only the post's own letters and digits count towards its length`() {
        val item = of(
            text(
                TextSpan.Plain("哈哈 3！😂"),
                TextSpan.Emoticon("image_emoticon", "滑稽"),
                TextSpan.Link("看这里", "https://example.com"),
                TextSpan.Mention("@someone", 7),
            ),
            image(),
        )

        assertEquals(3, item.length)
    }

    @Test
    fun `emoticons are not words, and media are not text`() {
        val item = of(text(TextSpan.Plain("看"), TextSpan.Emoticon("image_emoticon", "滑稽")), image(), PostContent.Voice("md5", 3))

        assertEquals("看\n\n\n", item.content)
    }

    @Test
    fun `what content holds besides words is known`() {
        assertEquals(
            setOf(ContentKind.Emoticon, ContentKind.Link, ContentKind.Mention, ContentKind.Image, ContentKind.Voice),
            of(
                text(TextSpan.Plain("😂"), TextSpan.Link("a", "b"), TextSpan.Mention("@x", 1)),
                image(),
                PostContent.Voice("md5", 3),
            ).kinds,
        )
        assertEquals(setOf(ContentKind.Emoticon), of(image(sticker = true)).kinds)
        assertEquals(emptySet(), of(text(TextSpan.Plain("只是文字，带标点。"))).kinds)
    }
}
