package oolong.client.utils

import android.content.ClipboardManager
import android.content.Context

val Context.clipBoardManager
    get() = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
