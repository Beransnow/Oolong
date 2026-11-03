package oolong.client.model

import android.net.Uri
import androidx.compose.runtime.Immutable

/** A picture stored on the device. */
@Immutable
data class DeviceImage(
    val uri: Uri,
    /** The folder it is in, which galleries show as an album. */
    val albumId: Long,
    val albumName: String,
    val mimeType: String,
)

/** A folder of pictures, with its newest one as the cover. */
@Immutable
data class DeviceAlbum(
    /** The folder, null for an album of every picture. */
    val id: Long?,
    val name: String,
    val cover: Uri,
    val count: Int,
)
