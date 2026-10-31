package com.huanchengfly.tieba.core.network

import com.huanchengfly.tieba.core.network.identity.DeviceIdentity
import com.huanchengfly.tieba.core.network.identity.InstallIdentity
import com.huanchengfly.tieba.core.network.identity.NetworkType
import com.huanchengfly.tieba.core.network.identity.Session

object FakeDevice : DeviceIdentity {
    override val androidId = "0123456789abcdef"
    override val cuid = "0123456789ABCDEF0123456789ABCDEF|VABCDEFGH"
    override val c3Aid = "A00-ABCDEFGHIJKLMNOPQRSTUVWXYZ234567-ABCDEFGH"
    override val imei = "000000000000000"
    override val oaid = "oaid-value"
    override val model = "Pixel 9"
    override val brand = "google"
    override val osVersion = "16"
    override val screenWidth = 1080
    override val screenHeight = 2400
    override val screenDensity = 2.625f
    override val networkType = NetworkType.MOBILE_5G
    override val deviceScore = 0.3f
    override val webViewUserAgent = "Mozilla/5.0 (Linux; Android 16; Pixel 9; wv)"
}

class FakeInstall(override val clientId: String? = "wappc_1700000000000_123") : InstallIdentity {
    override val sampleId = "100_1-200_2"
    override val baiduId = "0123456789ABCDEF0123456789ABCDEF:FG=1"
    override val activeTimestamp = 1_700_000_100_000L
    override val firstInstallTime = 1_600_000_000_000L
    override val lastUpdateTime = 1_650_000_000_000L
}

val fakeSession = Session(
    uid = "12345",
    bduss = "bduss-value",
    stoken = "stoken-value",
    tbs = "tbs-value",
    zid = "zid-value",
)
