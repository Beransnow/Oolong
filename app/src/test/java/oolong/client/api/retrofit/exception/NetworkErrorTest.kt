package oolong.client.api.retrofit.exception

import kotlinx.serialization.SerializationException
import oolong.core.network.TiebaServerException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class NetworkErrorTest {
    @Test
    fun `a request without an answer is a network error`() {
        assertTrue(UnknownHostException("tiebac.baidu.com").isNetworkError())
        assertTrue(SocketTimeoutException().isNetworkError())
        assertTrue(RuntimeException(UnknownHostException()).isNetworkError())
        assertTrue(NoConnectivityException().isNetworkError())
    }

    @Test
    fun `an answer is not a network error`() {
        assertFalse(TiebaServerException(1, "参数错误", null).isNetworkError())
        assertFalse(TiebaNotLoggedInException().isNetworkError())
        assertFalse(SerializationException("bad json").isNetworkError())
        assertFalse(IllegalStateException().isNetworkError())
    }
}
