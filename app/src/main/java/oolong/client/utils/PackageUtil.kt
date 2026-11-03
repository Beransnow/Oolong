package oolong.client.utils

import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle

val Context.applicationMetaData: Bundle
    get() {
        return packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA).metaData
    }
