package oolong.client.repository

import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FileFeedSnapshotsTest {
    @get:Rule
    val folder = TemporaryFolder()

    private var uid: String? = "1"
    private var now = 1_000L
    private val snapshots by lazy { FileFeedSnapshots(folder.root, { uid }, { now }, maxPages = 3, maxAge = 100) }

    private fun FeedSnapshot.texts() = pages.map { String(it) }

    @Test
    fun `pages are kept in the list's order with the next key`() {
        snapshots.write("feed", "a".toByteArray(), FeedSnapshots.Place.Replace, next = "2")
        snapshots.write("feed", "b".toByteArray(), FeedSnapshots.Place.Back, next = "3")
        snapshots.write("feed", "c".toByteArray(), FeedSnapshots.Place.Front, next = "2")

        val kept = snapshots.read("feed")!!
        assertEquals(listOf("c", "a", "b"), kept.texts())
        assertEquals("2", kept.next)
    }

    @Test
    fun `beyond the limit a refresh drops the end and more pages are not kept`() {
        snapshots.write("feed", "a".toByteArray(), FeedSnapshots.Place.Replace, next = "2")
        snapshots.write("feed", "b".toByteArray(), FeedSnapshots.Place.Back, next = "3")
        snapshots.write("feed", "c".toByteArray(), FeedSnapshots.Place.Back, next = "4")
        snapshots.write("feed", "d".toByteArray(), FeedSnapshots.Place.Back, next = "5")
        assertEquals(listOf("a", "b", "c"), snapshots.read("feed")!!.texts())
        assertEquals("4", snapshots.read("feed")!!.next)

        snapshots.write("feed", "e".toByteArray(), FeedSnapshots.Place.Front, next = "2")
        assertEquals(listOf("e", "a", "b"), snapshots.read("feed")!!.texts())
        assertEquals(3, File(folder.root, "1/feed").listFiles()!!.count { it.name.endsWith(".bin") })
    }

    @Test
    fun `where the list was left comes back, and leaving keeps it fresh`() {
        snapshots.write("feed", "a".toByteArray(), FeedSnapshots.Place.Replace, next = null)
        now += 90
        snapshots.leave("feed", FeedPosition(7, 40), setOf(3L))
        now += 90

        val kept = snapshots.read("feed")!!
        assertEquals(FeedPosition(7, 40), kept.position)
        assertEquals(setOf(3L), kept.disliked)

        now += 20
        assertNull(snapshots.read("feed"))
    }

    @Test
    fun `starting the list over forgets where it was left`() {
        snapshots.write("feed", "a".toByteArray(), FeedSnapshots.Place.Replace, next = null)
        snapshots.leave("feed", FeedPosition(7, 40), setOf(3L))
        snapshots.write("feed", "b".toByteArray(), FeedSnapshots.Place.Replace, next = null)

        assertNull(snapshots.read("feed")!!.position)
        assertEquals(emptySet(), snapshots.read("feed")!!.disliked)
    }

    @Test
    fun `each account has its own, and none is kept when logged out`() {
        snapshots.write("feed", "a".toByteArray(), FeedSnapshots.Place.Replace, next = null)
        uid = "2"
        assertNull(snapshots.read("feed"))
        uid = null
        snapshots.write("feed", "b".toByteArray(), FeedSnapshots.Place.Replace, next = null)
        assertNull(snapshots.read("feed"))
        uid = "1"
        assertEquals(listOf("a"), snapshots.read("feed")!!.texts())
    }

    @Test
    fun `a broken description reads as nothing kept`() {
        snapshots.write("feed", "a".toByteArray(), FeedSnapshots.Place.Replace, next = null)
        File(folder.root, "1/feed/meta.json").writeText("{")

        assertNull(snapshots.read("feed"))
    }
}
