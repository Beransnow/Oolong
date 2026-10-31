package com.huanchengfly.tieba.core.network.identity

import android.telephony.TelephonyManager

/**
 * The `net_type` codes the official client sends.
 */
object NetworkType {
    const val NONE = 0
    const val WIFI = 1
    const val MOBILE_2G = 2
    const val MOBILE_3G = 3
    const val MOBILE_4G = 4
    const val MOBILE_5G = 5

    /** Maps a `TelephonyManager.NETWORK_TYPE_*` value; unknown types count as 4G. */
    fun ofMobile(networkType: Int): Int = when (networkType) {
        TelephonyManager.NETWORK_TYPE_GPRS,
        TelephonyManager.NETWORK_TYPE_EDGE,
        TelephonyManager.NETWORK_TYPE_CDMA,
        TelephonyManager.NETWORK_TYPE_1xRTT,
        TelephonyManager.NETWORK_TYPE_IDEN,
        TelephonyManager.NETWORK_TYPE_GSM -> MOBILE_2G

        TelephonyManager.NETWORK_TYPE_UMTS,
        TelephonyManager.NETWORK_TYPE_EVDO_0,
        TelephonyManager.NETWORK_TYPE_EVDO_A,
        TelephonyManager.NETWORK_TYPE_HSDPA,
        TelephonyManager.NETWORK_TYPE_HSUPA,
        TelephonyManager.NETWORK_TYPE_HSPA,
        TelephonyManager.NETWORK_TYPE_EVDO_B,
        TelephonyManager.NETWORK_TYPE_EHRPD,
        TelephonyManager.NETWORK_TYPE_HSPAP,
        TelephonyManager.NETWORK_TYPE_TD_SCDMA -> MOBILE_3G

        TelephonyManager.NETWORK_TYPE_NR -> MOBILE_5G

        else -> MOBILE_4G
    }
}
