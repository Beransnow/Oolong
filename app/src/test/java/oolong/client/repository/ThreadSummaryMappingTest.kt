package oolong.client.repository

import oolong.client.api.models.protos.Agree
import oolong.client.api.models.protos.Media
import oolong.client.api.models.protos.OriginThreadInfo
import oolong.client.api.models.protos.PbContent
import oolong.client.api.models.protos.SimpleForum
import oolong.client.api.models.protos.ThreadInfo
import oolong.client.api.models.protos.User
import oolong.client.api.models.protos.VideoInfo
import oolong.client.model.ForumSummary
import oolong.client.model.ThreadVideo
import oolong.client.model.plainText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ThreadSummaryMappingTest {
    private val emoticons = EmoticonRegistry { _, _ -> }

    @Test
    fun `a feed thread takes its forum from the forum info`() {
        val summary = ThreadInfo(
            threadId = 1,
            forumId = 0,
            forumInfo = SimpleForum(id = 5, name = "数码", avatar = "a.png"),
            author = User(id = 7, name = "name", nameShow = "show", portrait = "p"),
            title = "标题",
            lastTimeInt = 100,
            createTime = 50,
            replyNum = 3,
            shareNum = 4,
            agreeNum = 9,
            agree = Agree(hasAgree = 1),
        ).toThreadSummary(emoticons)

        assertEquals(1, summary.id)
        assertEquals(ForumSummary(5, "数码", "a.png"), summary.forum)
        assertEquals("show", summary.author.displayName)
        assertEquals(100, summary.listTime)
        assertEquals(50, summary.createTime)
        assertEquals(3, summary.replyCount)
        assertEquals(4, summary.shareCount)
        assertEquals(9, summary.agree.count)
        assertTrue(summary.agree.agreed)
    }

    @Test
    fun `a forum's thread keeps its forum by name and its author by id`() {
        val summary = ThreadInfo(id = 2, forumId = 6, forumName = "游戏", authorId = 8).toThreadSummary(emoticons)

        assertEquals(2, summary.id)
        assertEquals(ForumSummary(6, "游戏", ""), summary.forum)
        assertEquals(8, summary.author.id)
    }

    @Test
    fun `a thread without a title has none`() {
        val summary = ThreadInfo(title = "正文开头", isNoTitle = 1).toThreadSummary(emoticons)
        assertEquals("", summary.title)
    }

    @Test
    fun `the abstract collapses spaces and writes emoticons as codes`() {
        val summary = ThreadInfo(
            richAbstract = listOf(
                PbContent(type = 0, text = "a   b"),
                PbContent(type = 2, text = "image_emoticon25", c = "滑稽"),
                PbContent(type = 3, text = "ignored"),
            ),
        ).toThreadSummary(emoticons)

        assertEquals("a b#(滑稽)", summary.abstract)
    }

    @Test
    fun `the whole first floor, pictures and video come along`() {
        val summary = ThreadInfo(
            firstPostId = 11,
            firstPostContent = listOf(PbContent(type = 0, text = "一楼全文")),
            media = listOf(Media(srcPic = "s", bigPic = "b", originPic = "o", width = 10, height = 20, isLongPic = 1)),
            videoInfo = VideoInfo(videoUrl = "v", thumbnailUrl = "t", thumbnailWidth = 16, thumbnailHeight = 9),
        ).toThreadSummary(emoticons)

        assertEquals(11, summary.firstPostId)
        assertEquals("一楼全文", summary.firstPost.plainText)
        val image = summary.images.single()
        assertEquals(Triple("b", "s", "o"), Triple(image.small, image.large, image.original))
        assertTrue(image.isLong)
        assertEquals(ThreadVideo("v", "t", 16, 9), summary.video)
    }

    @Test
    fun `a picture missing some sizes takes the nearest one there is`() {
        val image = Media(bigPic = "b").toThreadImage()

        assertEquals(Triple("b", "b", "b"), Triple(image.small, image.large, image.original))
    }

    @Test
    fun `only a sharing thread has a shared thread`() {
        val origin = OriginThreadInfo(tid = "21", fid = 22, fname = "原吧", content = listOf(PbContent(type = 0, text = "原帖")))

        val sharing = ThreadInfo(is_share_thread = 1, origin_thread_info = origin).toThreadSummary(emoticons)
        val notSharing = ThreadInfo(is_share_thread = 0, origin_thread_info = origin).toThreadSummary(emoticons)

        val shared = sharing.shared!!
        assertEquals(21, shared.id)
        assertEquals("原吧", shared.forumName)
        assertEquals("原帖", shared.content.plainText)
        assertNull(notSharing.shared)
        assertFalse(notSharing.isGood)
    }

    @Test
    fun `a recommended reply shows its thread with the reply on it`() {
        val summary = ThreadInfo(
            id = 2100000009177635,
            threadId = 2100000009177635,
            thread_type = 81,
            title = "回复：贝果记录一下",
            forumInfo = SimpleForum(id = 5, name = "绝区零", avatar = "a.png"),
            author = User(id = 7, name = "name", nameShow = "show", portrait = "p"),
            createTime = 50,
            post_id = 154099046834,
            richAbstract = listOf(
                PbContent(type = 4, text = "@下个星期四"),
                PbContent(type = 0, text = " 回复：你应该去找"),
            ),
            firstPostContent = listOf(PbContent(type = 0, text = "你应该去找")),
            media = listOf(Media(type = 3, bigPic = "reply.jpg")),
            origin_thread_info = OriginThreadInfo(
                title = "贝果记录一下",
                tid = "11035424096",
                media = listOf(Media(type = 3, bigPic = "thread.jpg")),
            ),
        ).toThreadSummary(emoticons)

        // The entry keeps the feed's id, so it never stands in for the thread elsewhere
        assertEquals(2100000009177635, summary.id)
        assertEquals("贝果记录一下", summary.title)
        assertEquals("thread.jpg", summary.images.single().small)
        assertEquals("", summary.author.name)
        assertEquals("你应该去找", summary.firstPost.plainText)
        val reply = summary.reply!!
        assertEquals(11035424096, reply.threadId)
        assertEquals(154099046834, reply.postId)
        assertEquals("show", reply.author.displayName)
        assertEquals(50, reply.time)
        assertEquals("回复 @下个星期四：你应该去找", reply.text)
        assertEquals("reply.jpg", reply.images.single().small)
    }

    @Test
    fun `a thread of its own has no reply`() {
        assertNull(ThreadInfo(id = 1, title = "标题").toThreadSummary(emoticons).reply)
    }
}
