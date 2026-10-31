package com.huanchengfly.tieba.post.ui.page.thread

import com.huanchengfly.tieba.post.api.models.protos.Agree
import com.huanchengfly.tieba.post.api.models.protos.OriginThreadInfo
import com.huanchengfly.tieba.post.api.models.protos.PbContent
import com.huanchengfly.tieba.post.api.models.protos.Post
import com.huanchengfly.tieba.post.api.models.protos.SubPost
import com.huanchengfly.tieba.post.api.models.protos.SubPostList
import com.huanchengfly.tieba.post.api.models.protos.User
import com.huanchengfly.tieba.post.api.models.protos.contentRenders
import com.huanchengfly.tieba.post.api.models.protos.subPosts
import com.huanchengfly.tieba.post.arch.wrapImmutable
import com.huanchengfly.tieba.post.testing.LegacyAppRule
import com.huanchengfly.tieba.post.testing.captureScreenshot
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PostCardScreenshotTest {
    @get:Rule
    val legacyApp = LegacyAppRule()

    private val threadAuthor = User(id = 10, name = "louzhu", nameShow = "楼主本人", level_id = 12)
    private val replier = User(id = 20, name = "replier", nameShow = "回复的人", level_id = 4)

    private fun post(
        floor: Int,
        author: User,
        content: List<PbContent>,
        title: String = "",
        subPosts: List<SubPostList> = emptyList(),
        subPostNumber: Int = subPosts.size,
    ) = Post(
        id = floor.toLong(),
        floor = floor,
        title = title,
        time = 1_700_000_000,
        author = author,
        author_id = author.id,
        agree = Agree(agreeNum = 12, diffAgreeNum = 12),
        content = content,
        sub_post_list = SubPost(sub_post_list = subPosts),
        sub_post_number = subPostNumber,
        origin_thread_info = OriginThreadInfo(author = threadAuthor),
    )

    private fun text(text: String) = PbContent(type = 0, text = text)

    @Test
    fun reply() {
        val post = post(floor = 2, author = replier, content = listOf(text("这是一条普通的回复。")))
        captureScreenshot {
            PostCard(post.wrapImmutable(), post.contentRenders, threadAuthorId = threadAuthor.id)
        }
    }

    @Test
    fun firstFloorWithTitle() {
        val post = post(
            floor = 1,
            author = threadAuthor,
            title = "帖子标题",
            content = listOf(text("一楼的正文内容，比较长的时候会换行显示，看看换行之后的排版是什么样子。")),
        )
        captureScreenshot {
            PostCard(
                post.wrapImmutable(),
                post.contentRenders,
                threadAuthorId = threadAuthor.id,
                showSubPosts = false,
            )
        }
    }

    @Test
    fun replyByBawuWithLinksAndMention() {
        val bawu = replier.copy(is_bawu = 1, bawu_type = "manager")
        val post = post(
            floor = 3,
            author = bawu,
            content = listOf(
                text("回复 "),
                PbContent(type = 4, text = "@楼主本人", uid = threadAuthor.id),
                text(" 看这个链接："),
                PbContent(type = 1, text = "https://tieba.baidu.com", link = "https://tieba.baidu.com"),
            ),
        )
        captureScreenshot {
            PostCard(post.wrapImmutable(), post.contentRenders, threadAuthorId = threadAuthor.id)
        }
    }

    @Test
    fun replyWithSubPosts() {
        val subPosts = listOf(
            SubPostList(id = 101, author = threadAuthor, content = listOf(text("楼主的楼中楼回复"))),
            SubPostList(id = 102, author = replier, content = listOf(text("另一条楼中楼回复"))),
        )
        val post = post(
            floor = 4,
            author = replier,
            content = listOf(text("有楼中楼的回复")),
            subPosts = subPosts,
            subPostNumber = 5,
        )
        captureScreenshot {
            PostCard(
                post.wrapImmutable(),
                post.contentRenders,
                subPosts = post.subPosts,
                threadAuthorId = threadAuthor.id,
            )
        }
    }

    @Test
    fun blocked() {
        val post = post(floor = 5, author = replier, content = listOf(text("被屏蔽的回复")))
        captureScreenshot {
            PostCard(post.wrapImmutable(), post.contentRenders, blocked = true)
        }
    }
}
