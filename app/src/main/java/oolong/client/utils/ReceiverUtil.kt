package oolong.client.utils

import android.content.IntentFilter

fun newIntentFilter(action: String) =
    IntentFilter().apply {
        addAction(action)
    }
