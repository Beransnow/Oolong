package com.huanchengfly.tieba.post.ui.page.thread

import com.huanchengfly.tieba.post.model.Agree
import com.huanchengfly.tieba.post.model.BawuType
import com.huanchengfly.tieba.post.model.BlockRule
import com.huanchengfly.tieba.post.model.Post
import com.huanchengfly.tieba.post.model.PostContent
import com.huanchengfly.tieba.post.model.SubPost
import com.huanchengfly.tieba.post.model.TextSpan
import com.huanchengfly.tieba.post.model.User
import com.huanchengfly.tieba.post.repository.BlockVerdict
import com.huanchengfly.tieba.post.testing.LegacyAppRule
import com.huanchengfly.tieba.post.testing.captureScreenshot
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ThreadPostCardScreenshotTest {
    @get:Rule
    val legacyApp = LegacyAppRule()

    private val threadAuthor = User(id = 10, name = "louzhu", nameShow = "楼主本人", portrait = "", level = 12)
    private val replier = User(id = 20, name = "replier", nameShow = "回复的人", portrait = "", level = 4)
    private val options = PostCardOptions(threadAuthorId = threadAuthor.id)

    private fun text(vararg spans: TextSpan) = PostContent.Text(spans.toList().toImmutableList())

    private fun post(
        floor: Int,
        author: User,
        content: List<PostContent>,
        title: String = "",
        subPosts: List<SubPost> = emptyList(),
        subPostCount: Int = subPosts.size,
    ) = Post(
        id = floor.toLong(),
        floor = floor,
        title = title,
        time = 1_700_000_000,
        author = author,
        content = content.toImmutableList(),
        agree = Agree(count = 12, agreed = false),
        subPosts = subPosts.toImmutableList(),
        subPostCount = subPostCount,
    )

    @Test
    fun reply() {
        val post = post(floor = 2, author = replier, content = listOf(text(TextSpan.Plain("这是一条普通的回复。"))))
        captureScreenshot { ThreadPostCard(PostItem(post), options, onEvent = {}) }
    }

    @Test
    fun firstFloorWithTitle() {
        val post = post(
            floor = 1,
            author = threadAuthor,
            title = "帖子标题",
            content = listOf(text(TextSpan.Plain("一楼的正文内容，比较长的时候会换行显示，看看换行之后的排版是什么样子。"))),
        )
        captureScreenshot { ThreadPostCard(PostItem(post), options, onEvent = {}, showSubPosts = false) }
    }

    @Test
    fun replyByBawuWithLinksAndMention() {
        val post = post(
            floor = 3,
            author = replier.copy(bawuType = BawuType.Manager),
            content = listOf(
                text(
                    TextSpan.Plain("回复 "),
                    TextSpan.Mention("@楼主本人", userId = threadAuthor.id),
                    TextSpan.Plain(" 看这个链接："),
                    TextSpan.Link("https://tieba.baidu.com", url = "https://tieba.baidu.com"),
                )
            ),
        )
        captureScreenshot { ThreadPostCard(PostItem(post), options, onEvent = {}) }
    }

    @Test
    fun replyWithSubPosts() {
        val subPosts = listOf(
            SubPost(101, threadAuthor, persistentListOf(text(TextSpan.Plain("楼主的楼中楼回复"))), time = 0),
            SubPost(102, replier, persistentListOf(text(TextSpan.Plain("另一条楼中楼回复"))), time = 0),
        )
        val post = post(
            floor = 4,
            author = replier,
            content = listOf(text(TextSpan.Plain("有楼中楼的回复"))),
            subPosts = subPosts,
            subPostCount = 5,
        )
        captureScreenshot { ThreadPostCard(PostItem(post), options, onEvent = {}) }
    }

    @Test
    fun blocked() {
        val post = post(floor = 5, author = replier, content = listOf(text(TextSpan.Plain("被屏蔽的回复"))))
        captureScreenshot {
            ThreadPostCard(PostItem(post, block = BlockVerdict(BlockRule(match = BlockRule.Match.User(0, replier.name)), hidden = false)), options.copy(threadAuthorId = 0), onEvent = {})
        }
    }
}
