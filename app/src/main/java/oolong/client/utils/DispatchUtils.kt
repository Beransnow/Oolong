package oolong.client.utils

import oolong.client.App

val officialClientPackages = arrayOf("com.baidu.tieba", "com.baidu.tieba_mini")

fun isOfficialClientInstalled(): Boolean {
    return App.INSTANCE.isAnyPackageInstalled(officialClientPackages)
}
