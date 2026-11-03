package oolong.core.network

import android.telephony.TelephonyManager
import oolong.core.network.identity.NetworkType
import kotlin.test.Test
import kotlin.test.assertEquals

class NetworkTypeTest {
    @Test
    fun `maps mobile network types to generations`() {
        assertEquals(2, NetworkType.ofMobile(TelephonyManager.NETWORK_TYPE_EDGE))
        assertEquals(3, NetworkType.ofMobile(TelephonyManager.NETWORK_TYPE_HSPAP))
        assertEquals(4, NetworkType.ofMobile(TelephonyManager.NETWORK_TYPE_LTE))
        assertEquals(5, NetworkType.ofMobile(TelephonyManager.NETWORK_TYPE_NR))
    }

    @Test
    fun `treats unknown mobile network types as 4G`() {
        assertEquals(4, NetworkType.ofMobile(TelephonyManager.NETWORK_TYPE_UNKNOWN))
    }
}
