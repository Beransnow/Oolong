package oolong.client.ui.page.reader

import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import oolong.client.model.Agree
import oolong.client.model.Post
import oolong.client.model.PostContent
import oolong.client.model.TextSpan
import oolong.client.model.User
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderDocumentTest {
    private val author = User(1, "author", "", "")

    private fun post(floor: Int, text: String, time: Long = floor * 60L, images: Int = 0, subs: Int = 0) = Post(
        id = floor.toLong(),
        floor = floor,
        title = "",
        time = time,
        author = author,
        content = (
            listOf(PostContent.Text(persistentListOf(TextSpan.Plain(text)))) +
                List(images) { PostContent.Image("$floor-$it", persistentListOf(), "", 560, 320, false, 0) }
            ).toImmutableList(),
        agree = Agree(0, false),
        subPosts = persistentListOf(),
        subPostCount = subs,
    )

    private fun fixture(name: String): List<Post> {
        val json = javaClass.classLoader!!.getResource("reader/reader-$name.json")!!.readText()
        return Json.parseToJsonElement(json).jsonArray.map {
            val o = it.jsonObject
            post(
                floor = o["f"]!!.jsonPrimitive.int,
                text = o["text"]!!.jsonPrimitive.content,
                time = o["ts"]!!.jsonPrimitive.long,
                images = o["imgs"]!!.jsonPrimitive.int,
                subs = o["subs"]!!.jsonPrimitive.int,
            )
        }
    }

    private val long = "正文".repeat(60)

    @Test
    fun chineseNumerals() {
        assertEquals(12, parseNumber("十二"))
        assertEquals(20, parseNumber("二十"))
        assertEquals(105, parseNumber("一百零五"))
        assertEquals(7, parseNumber("7"))
        assertNull(parseNumber("十x"))
    }

    @Test
    fun numberedChaptersAndSectionsAreFound() {
        assertEquals(setOf(HeadingKind.Chapter, HeadingKind.Section), detectKinds(fixture("6171605316")))
        assertEquals(setOf(HeadingKind.Bracket), detectKinds(fixture("5025862278")))
    }

    @Test
    fun aShapeUsedTwiceIsNotAHeading() {
        val posts = listOf(post(1, "第一章 开始\n$long"), post(2, "第二章 继续\n$long"), post(3, long))
        assertEquals(emptySet<HeadingKind>(), detectKinds(posts))
    }

    @Test
    fun numbersOutOfOrderAreNotHeadings() {
        val posts = listOf(5, 3, 8, 2, 7).mapIndexed { i, n -> post(i + 1, "($n) 说明\n$long") }
        assertEquals(emptySet<HeadingKind>(), detectKinds(posts))
    }

    @Test
    fun chaptersRepeatedAcrossFloorsAreListedOnce() {
        val doc = buildReaderDocument(fixture("6171605316"))
        val titles = doc.chapters.map { it.title }
        assertEquals(listOf("序章", "第一章：诡异的委托", "第一章 乐能", "第一节千岛事务所"), titles.take(4))
        assertEquals(1, titles.count { it.startsWith("第二章") })
    }

    @Test
    fun bracketRunsIntoTheText() {
        val posts = listOf(post(1, "【1】他推开门\n$long"), post(2, "【2】\n$long"), post(3, "【3】〖终章〗\n$long"))
        val doc = buildReaderDocument(posts)
        assertEquals(listOf("【1】", "【2】", "【3】〖终章〗"), doc.chapters.map { it.title })
        val first = doc.blocks.filterIsInstance<ReaderBlock.Paragraph>().first()
        assertEquals("他推开门", first.text)
    }

    @Test
    fun shortFloorsBetweenChaptersFold() {
        val posts = listOf(post(1, long), post(2, "今天先更到这"), post(3, "明天继续"), post(4, long))
        val notes = buildReaderDocument(posts).blocks.filterIsInstance<ReaderBlock.Notes>().single()
        assertEquals(listOf(2, 3), notes.notes.map { it.floor })
    }

    @Test
    fun aShortFloorWithAPictureIsNotANote() {
        val doc = buildReaderDocument(listOf(post(1, "插图", images = 1)))
        assertTrue(doc.blocks.none { it is ReaderBlock.Notes })
        assertEquals(1, doc.blocks.count { it is ReaderBlock.Media })
    }

    @Test
    fun picturesStayWhereTheyStand() {
        val doc = buildReaderDocument(listOf(post(1, "第一段$long", images = 1)))
        assertEquals(listOf(ReaderBlock.Paragraph::class, ReaderBlock.Media::class, ReaderBlock.Foot::class), doc.blocks.drop(1).map { it::class })
    }

    @Test
    fun aLongPauseStartsAnUpdate() {
        val day = 86_400L
        val posts = listOf(post(1, long, time = 0), post(2, long, time = 3600), post(3, long, time = 30 * day))
        val doc = buildReaderDocument(posts)
        assertEquals(listOf(null, 30 * day - 3600), doc.updates.map { it.gap })
        assertEquals(listOf(2, 1), doc.updates.map { it.floors })
    }

    @Test
    fun anUpdateOfOnlyNotesIsLeftOutOfTheList() {
        val day = 86_400L
        val posts = listOf(post(1, long, time = 0), post(2, "还活着", time = 10 * day))
        assertEquals(1, buildReaderDocument(posts).updates.size)
    }

    @Test
    fun notesPostedAfterSeveralPausesFoldTogether() {
        val day = 86_400L
        val posts = listOf(
            post(1, long, time = 0),
            post(2, "明天更", time = 3 * day),
            post(3, "再等等", time = 4 * day),
            post(4, long, time = 10 * day),
        )
        val doc = buildReaderDocument(posts)
        assertEquals(listOf(2, 3), doc.blocks.filterIsInstance<ReaderBlock.Notes>().single().notes.map { it.floor })
        val marks = doc.blocks.filterIsInstance<ReaderBlock.UpdateMark>()
        assertEquals(listOf(null, 10 * day), marks.map { it.gap })
        assertEquals(listOf(1, 4), marks.map { it.floor })
    }

    @Test
    fun chineseIsSpacedFromLatinAndDigits() {
        assertEquals("用\u2005Kotlin\u2005写了\u20053\u2005天", spaced("用Kotlin写了3天"))
        assertEquals("用 Kotlin 写", spaced("用 Kotlin 写"))
    }

    @Test
    fun threeChatLinesMakeAChat() {
        val text = "【死党群】\n小明：在吗\n小红：在\n小明：出来玩\n然后他们出去了。$long"
        val chat = buildReaderDocument(listOf(post(1, text))).blocks.filterIsInstance<ReaderBlock.Chat>().single()
        assertEquals("死党群", chat.room)
        assertEquals(listOf("小明", "小红", "小明"), chat.lines.map { it.name })
    }

    @Test
    fun twoChatLinesStayParagraphs() {
        val doc = buildReaderDocument(listOf(post(1, "小明：在吗\n小红：在\n$long")))
        assertTrue(doc.blocks.none { it is ReaderBlock.Chat })
    }

    @Test
    fun aColumnOfFullStopsIsOneBreak() {
        val doc = buildReaderDocument(listOf(post(1, "开头$long\n。\n。\n\n。\n后来")))
        assertEquals(1, doc.blocks.count { it is ReaderBlock.Break })
        assertEquals("后来", doc.blocks.filterIsInstance<ReaderBlock.Paragraph>().last().text)
    }

    @Test
    fun knownKindsStayAsMoreFloorsLoad() {
        val posts = listOf(post(1, "第一章\n$long"), post(2, "第二章\n$long"), post(3, "第三章\n$long"))
        val known = buildReaderDocument(posts).kinds
        val next = buildReaderDocument(posts + post(4, "第九十九章\n$long") + post(5, "第三章\n$long"), known)
        assertTrue(HeadingKind.Chapter in next.kinds)
    }
}
