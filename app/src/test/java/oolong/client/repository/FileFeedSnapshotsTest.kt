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
    private val snapshots by lazy { FileFeedSnapshots(folder.root, { uid }, maxPages = 3) }

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
    fun `hidden threads are kept until the list starts over`() {
        snapshots.write("feed", "a".toByteArray(), FeedSnapshots.Place.Replace, next = null)
        snapshots.hide("feed", 3)
        snapshots.write("feed", "b".toByteArray(), FeedSnapshots.Place.Front, next = null)
        assertEquals(setOf(3L), snapshots.read("feed")!!.hidden)

        snapshots.write("feed", "c".toByteArray(), FeedSnapshots.Place.Replace, next = null)
        assertEquals(emptySet(), snapshots.read("feed")!!.hidden)
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
