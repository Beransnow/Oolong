package oolong.client.util

import android.content.IntentFilter

fun newIntentFilter(action: String) =
    IntentFilter().apply {
        addAction(action)
    }
