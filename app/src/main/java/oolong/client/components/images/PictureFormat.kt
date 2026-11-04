package oolong.client.components.images

import java.io.File
import java.io.InputStream

/** What kind of picture a file holds, told by its first bytes rather than its name. */
enum class PictureFormat(val mimeType: String, val extension: String) {
    Jpeg("image/jpeg", "jpg"),
    Png("image/png", "png"),
    Gif("image/gif", "gif"),
    WebP("image/webp", "webp"),
    Heif("image/heif", "heic"),
    Avif("image/avif", "avif"),
    ;

    companion object {
        /** The format of the picture whose first bytes are [head], or null if it is none of these. */
        fun of(head: ByteArray): PictureFormat? {
            fun at(offset: Int, vararg bytes: Int) =
                head.size >= offset + bytes.size && bytes.indices.all { head[offset + it] == bytes[it].toByte() }
            fun text(offset: Int, value: String) = at(offset, *value.map { it.code }.toIntArray())
            return when {
                at(0, 0xFF, 0xD8, 0xFF) -> Jpeg
                at(0, 0x89, 0x50, 0x4E, 0x47) -> Png
                text(0, "GIF8") -> Gif
                text(0, "RIFF") && text(8, "WEBP") -> WebP
                // An ISO media file: its brand says which
                text(4, "ftyp") -> when {
                    text(8, "avif") || text(8, "avis") -> Avif
                    else -> Heif
                }

                else -> null
            }
        }

        fun of(input: InputStream): PictureFormat? = of(input.readNBytesCompat(12))

        fun of(file: File): PictureFormat? = runCatching { file.inputStream().use(::of) }.getOrNull()

        private fun InputStream.readNBytesCompat(count: Int): ByteArray {
            val bytes = ByteArray(count)
            var read = 0
            while (read < count) {
                val n = read(bytes, read, count - read)
                if (n < 0) break
                read += n
            }
            return bytes.copyOf(read)
        }
    }
}
