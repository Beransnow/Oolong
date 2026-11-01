package com.huanchengfly.tieba.post.utils

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.ext.SdkExtensions.getExtensionVersion
import android.provider.MediaStore
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContract
import androidx.appcompat.app.AppCompatActivity
import com.huanchengfly.tieba.post.App
import com.huanchengfly.tieba.post.ui.page.picker.ImagePickerActivity

fun AppCompatActivity.registerPickMediasLauncher(callback: (PickMediasResult) -> Unit): ActivityResultLauncher<PickMediasRequest> {
    return registerForActivityResult(
        PickMediasContract
    ) {
        callback(it)
    }
}

fun isPhotoPickerAvailable(): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        true
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        getExtensionVersion(Build.VERSION_CODES.R) >= 2
    } else {
        false
    }
}

fun shouldUsePhotoPicker(): Boolean {
    return !App.INSTANCE.appPreferences.doNotUsePhotoPicker && isPhotoPickerAvailable()
}

fun Intent.getClipDataUris(): List<Uri> {
    // Use a LinkedHashSet to maintain any ordering that may be
    // present in the ClipData
    val resultSet = LinkedHashSet<Uri>()
    data?.let { data ->
        resultSet.add(data)
    }
    val clipData = clipData
    if (clipData == null && resultSet.isEmpty()) {
        return emptyList()
    } else if (clipData != null) {
        for (i in 0 until clipData.itemCount) {
            val uri = clipData.getItemAt(i).uri
            if (uri != null) {
                resultSet.add(uri)
            }
        }
    }
    return resultSet.toList()
}

/** A request to pick up to [maxItems] pictures; [id] tells the requester's result apart. */
data class PickMediasRequest(
    val id: String = "",
    val maxItems: Int = 1,
)

data class PickMediasResult(
    val id: String,
    val uris: List<Uri>
)

object PickMediasContract : ActivityResultContract<PickMediasRequest, PickMediasResult>() {
    private var curRequestId: String? = null

    val hasCurrentRequest: Boolean
        get() = curRequestId != null

    override fun createIntent(context: Context, input: PickMediasRequest): Intent {
        curRequestId = input.id
        if (shouldUsePhotoPicker()) {
            return Intent(MediaStore.ACTION_PICK_IMAGES).apply {
                type = "image/*"
                if (input.maxItems > 1) {
                    putExtra(MediaStore.EXTRA_PICK_IMAGES_MAX, input.maxItems)
                }
            }
        }
        return ImagePickerActivity.intent(context, input.maxItems)
    }

    override fun parseResult(resultCode: Int, intent: Intent?): PickMediasResult {
        val id = curRequestId + ""
        curRequestId = null
        if (resultCode != Activity.RESULT_OK || intent == null) {
            return PickMediasResult(id, emptyList())
        }
        return PickMediasResult(id, intent.getClipDataUris())
    }
}