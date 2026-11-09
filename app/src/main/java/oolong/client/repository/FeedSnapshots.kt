package oolong.client.repository

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/** A feed as it was last loaded: its pages as the server sent them, in the list's order. */
class FeedSnapshot(
    val pages: List<ByteArray>,
    /** The key of the page after the last one kept, as the feed writes it. */
    val next: String?,
    /** The threads the user said they don't want, which the feed hides. */
    val hidden: Set<Long>,
)

/**
 * Keeps the feeds of the current account across restarts of the app, for showing them as they
 * were last loaded. Nothing it fails to read or write fails the feed.
 */
interface FeedSnapshots {
    /** Where a page goes in the list kept. */
    enum class Place {
        /** It starts the list over. */
        Replace,

        /** In front, as a refresh of a feed that keeps what was already loaded. */
        Front,

        /** At the end. */
        Back,
    }

    /** What is kept of [feed] for the current account, or null when nothing is. */
    fun read(feed: String): FeedSnapshot?

    /** Keeps [page] of [feed] at [place], with the key of the page that follows the list. */
    fun write(feed: String, page: ByteArray, place: Place, next: String?)

    /** Notes that thread [threadId] is hidden from [feed]. */
    fun hide(feed: String, threadId: Long)
}

/**
 * [FeedSnapshots] as files under [dir], one folder per account and feed: each page as the
 * server sent it, and a description of the list. At most [maxPages] are kept, as a page can
 * weigh a few hundred kilobytes.
 */
class FileFeedSnapshots(
    private val dir: File,
    private val uid: () -> String?,
    private val maxPages: Int = 10,
) : FeedSnapshots {
    @Serializable
    private data class Meta(
        /** The page files in the list's order. */
        val pages: List<Long> = emptyList(),
        val next: String? = null,
        val hidden: List<Long> = emptyList(),
    )

    private val json = Json { ignoreUnknownKeys = true }

    @Synchronized
    override fun read(feed: String): FeedSnapshot? = runCatching {
        val folder = folder(feed) ?: return null
        val meta = meta(folder) ?: return null
        if (meta.pages.isEmpty()) return null
        FeedSnapshot(
            pages = meta.pages.map { File(folder, "$it.bin").readBytes() },
            next = meta.next,
            hidden = meta.hidden.toSet(),
        )
    }.getOrNull()

    @Synchronized
    override fun write(feed: String, page: ByteArray, place: FeedSnapshots.Place, next: String?) {
        runCatching {
            val folder = folder(feed) ?: return
            folder.mkdirs()
            val meta = meta(folder) ?: Meta()
            // The list read back would end before this page, so it must still lead on to it
            if (place == FeedSnapshots.Place.Back && meta.pages.size >= maxPages) return
            val id = (meta.pages.maxOrNull() ?: 0L) + 1
            File(folder, "$id.bin").writeBytes(page)
            val pages = when (place) {
                FeedSnapshots.Place.Replace -> listOf(id)
                FeedSnapshots.Place.Front -> listOf(id) + meta.pages
                FeedSnapshots.Place.Back -> meta.pages + id
            }
            val kept = pages.take(maxPages)
            (meta.pages - kept.toSet()).forEach { File(folder, "$it.bin").delete() }
            val hidden = if (place == FeedSnapshots.Place.Replace) emptyList() else meta.hidden
            save(folder, meta.copy(pages = kept, next = next, hidden = hidden))
        }
    }

    @Synchronized
    override fun hide(feed: String, threadId: Long) {
        runCatching {
            val folder = folder(feed) ?: return
            val meta = meta(folder) ?: return
            if (threadId !in meta.hidden) save(folder, meta.copy(hidden = meta.hidden + threadId))
        }
    }

    private fun folder(feed: String): File? = uid()?.takeIf { it.isNotEmpty() }?.let { File(File(dir, it), feed) }

    private fun meta(folder: File): Meta? = runCatching {
        json.decodeFromString(Meta.serializer(), File(folder, MetaFile).readText())
    }.getOrNull()

    private fun save(folder: File, meta: Meta) {
        val temp = File(folder, "$MetaFile.tmp")
        temp.writeText(json.encodeToString(Meta.serializer(), meta))
        temp.renameTo(File(folder, MetaFile))
    }

    private companion object {
        const val MetaFile = "meta.json"
    }
}
