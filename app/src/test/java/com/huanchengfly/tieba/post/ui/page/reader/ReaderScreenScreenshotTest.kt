package com.huanchengfly.tieba.post.ui.page.reader

import androidx.compose.ui.test.junit4.createComposeRule
import com.huanchengfly.tieba.post.model.Agree
import com.huanchengfly.tieba.post.model.ForumSummary
import com.huanchengfly.tieba.post.model.Post
import com.huanchengfly.tieba.post.model.PostContent
import com.huanchengfly.tieba.post.model.TextSpan
import com.huanchengfly.tieba.post.model.ThreadDetail
import com.huanchengfly.tieba.post.model.ThreadSummary
import com.huanchengfly.tieba.post.model.User
import com.huanchengfly.tieba.post.testing.LegacyAppRule
import com.huanchengfly.tieba.post.testing.captureScreenshot
import kotlinx.collections.immutable.persistentListOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "+w393dp-h851dp-xxhdpi")
class ReaderScreenScreenshotTest {
    @get:Rule
    val legacyApp = LegacyAppRule()

    @get:Rule
    val composeRule = createComposeRule()

    private val author = User(id = 10, name = "author", nameShow = "连载作者", portrait = "")

    // 2024, so the dates keep their year whenever the test runs
    private val start = 1_717_200_000L

    private fun post(floor: Int, text: String, day: Int, subs: Int = 0) = Post(
        id = floor.toLong(),
        floor = floor,
        title = "",
        time = start + day * 86_400L,
        author = author,
        content = persistentListOf(PostContent.Text(persistentListOf(TextSpan.Plain(text)))),
        agree = Agree(0, false),
        subPosts = persistentListOf(),
        subPostCount = subs,
    )

    private val body = "雨从傍晚开始下，一直没有停。他站在事务所的窗前，看着楼下的街道一点点亮起灯来，" +
        "想起那封没有署名的委托信。信里只有一句话：请在月底之前，找到那只走失的猫。"

    private val state = run {
        val posts = listOf(
            post(1, "第一章 委托\n$body\n$body", day = 0),
            post(3, "第二章 雨夜\n$body\n。\n。\n。\n第二天早上，雨停了。", day = 0, subs = 4),
            post(5, "今天先更到这里", day = 0),
            post(8, "第三章 来客\n【事务所群】\n小林：人到了吗\n老周：刚进门\n小林：让他先坐\n$body", day = 40),
        )
        val summary = ThreadSummary(
            id = 9_000,
            forum = ForumSummary(id = 52, name = "原创小说", avatar = ""),
            title = "雨夜事务所",
            author = author,
            createTime = start,
            listTime = start,
            abstract = "",
            firstPostId = 1,
            firstPost = persistentListOf(),
            replyCount = 20,
            agree = Agree(0, false),
        )
        ReaderState(
            thread = ThreadDetail(summary, storeMarkPostId = null),
            document = buildReaderDocument(posts),
            hasMore = false,
            lastFloor = 21,
        )
    }

    private fun capture(chrome: Boolean, style: ReaderStyle = ReaderStyle()) = captureScreenshot {
        ReaderScreen(
            state = state,
            style = style,
            onStyleChange = {},
            onBack = {},
            onLoadMore = {},
            onRetry = {},
            onOpenSubPosts = {},
            onReadUpTo = {},
            photoViewData = { _, _ -> null },
            initialChrome = chrome,
        )
    }

    @Test
    fun reading() = capture(chrome = false)

    @Test
    fun chrome() = capture(chrome = true)

    @Test
    fun serifOnPaper() = capture(chrome = false, style = ReaderStyle(fontSize = 19, serif = true, page = ReaderPaper.Paper))

    @Test
    @Config(qualifiers = "+en")
    fun chromeEnglish() = capture(chrome = true)

    @Test
    fun mediumWeight() = capture(chrome = false, style = ReaderStyle(weight = 1))

    @Test
    @Config(qualifiers = "+w1280dp-h800dp-land-xhdpi")
    fun tablet() = capture(chrome = true)
}
