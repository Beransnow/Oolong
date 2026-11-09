package oolong.client.repository

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/** Where a feed was left: the thread at the top of the list, and how far past its top. */
@Immutable
data class FeedPosition(val threadId: Long, val offset: Int)

/** A feed as it was left: its pages as the server sent them, in the list's order, and where it was read to. */
class FeedSnapshot(
    val pages: List<ByteArray>,
    /** The key of the page after the last one kept, as the feed writes it. */
    val next: String?,
    val position: FeedPosition?,
    /** The threads the user said they don't want, which the feed hides. */
    val disliked: Set<Long>,
)

/**
 * Keeps the feeds of the current account across restarts of the app, so that one comes back as
 * it was left unless that was long ago. Nothing it fails to read or write fails the feed.
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

    /** What is kept of [feed] for the current account, or null when nothing is or it was left too long ago. */
    fun read(feed: String): FeedSnapshot?

    /** Keeps [page] of [feed] at [place], with the key of the page that follows the list. */
    fun write(feed: String, page: ByteArray, place: Place, next: String?)

    /** Notes where [feed] was left, and the threads hidden in it, as of now. */
    fun leave(feed: String, position: FeedPosition?, disliked: Set<Long>)
}

/**
 * [FeedSnapshots] as files under [dir], one folder per account and feed: each page as the
 * server sent it, and a description of the list. At most [maxPages] are kept, as a page can
 * weigh a few hundred kilobytes. What was last touched more than [maxAge] ago is not read.
 */
class FileFeedSnapshots(
    private val dir: File,
    private val uid: () -> String?,
    private val now: () -> Long = System::currentTimeMillis,
    private val maxPages: Int = 10,
    private val maxAge: Long = 12 * 60 * 60 * 1000L,
) : FeedSnapshots {
    @Serializable
    private data class Meta(
        /** The page files in the list's order. */
        val pages: List<Long> = emptyList(),
        val next: String? = null,
        val savedAt: Long = 0,
        val threadId: Long = 0,
        val offset: Int = 0,
        val disliked: List<Long> = emptyList(),
    )

    private val json = Json { ignoreUnknownKeys = true }

    @Synchronized
    override fun read(feed: String): FeedSnapshot? = runCatching {
        val folder = folder(feed) ?: return null
        val meta = meta(folder) ?: return null
        if (meta.pages.isEmpty() || now() - meta.savedAt !in 0..maxAge) return null
        FeedSnapshot(
            pages = meta.pages.map { File(folder, "$it.bin").readBytes() },
            next = meta.next,
            position = meta.threadId.takeIf { it != 0L }?.let { FeedPosition(it, meta.offset) },
            disliked = meta.disliked.toSet(),
        )
    }.getOrNull()

    @Synchronized
    override fun write(feed: String, page: ByteArray, place: FeedSnapshots.Place, next: String?) {
        runCatching {
            val folder = folder(feed) ?: return
            folder.mkdirs()
            val meta = meta(folder) ?: Meta()
            if (place == FeedSnapshots.Place.Back && meta.pages.size >= maxPages) {
                // The list read back would end before this page, so it must still lead on to it
                save(folder, meta.copy(savedAt = now()))
                return
            }
            val id = (meta.pages.maxOrNull() ?: 0L) + 1
            File(folder, "$id.bin").writeBytes(page)
            val pages = when (place) {
                FeedSnapshots.Place.Replace -> listOf(id)
                FeedSnapshots.Place.Front -> listOf(id) + meta.pages
                FeedSnapshots.Place.Back -> meta.pages + id
            }
            val kept = pages.take(maxPages)
            (meta.pages - kept.toSet()).forEach { File(folder, "$it.bin").delete() }
            val fresh = place == FeedSnapshots.Place.Replace
            save(
                folder,
                meta.copy(
                    pages = kept,
                    next = next,
                    savedAt = now(),
                    threadId = if (fresh) 0 else meta.threadId,
                    offset = if (fresh) 0 else meta.offset,
                    disliked = if (fresh) emptyList() else meta.disliked,
                ),
            )
        }
    }

    @Synchronized
    override fun leave(feed: String, position: FeedPosition?, disliked: Set<Long>) {
        runCatching {
            val folder = folder(feed) ?: return
            val meta = meta(folder) ?: return
            save(
                folder,
                meta.copy(
                    savedAt = now(),
                    threadId = position?.threadId ?: 0,
                    offset = position?.offset ?: 0,
                    disliked = disliked.toList(),
                ),
            )
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
