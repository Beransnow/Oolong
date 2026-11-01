package com.huanchengfly.tieba.post.components.images

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.Buffer
import org.junit.After
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ImageDownloadProgressTest {
    private val server = MockWebServer()
    private val client = OkHttpClient.Builder().addInterceptor(ImageDownloadProgress.interceptor).build()

    @Before
    fun setUp() {
        server.start()
    }

    @After
    fun tearDown() {
        server.close()
    }

    private fun serve(size: Int) {
        server.enqueue(MockResponse.Builder().body(Buffer().write(ByteArray(size))).build())
    }

    private fun download(url: String) {
        client.newCall(Request.Builder().url(url).build()).execute().use { it.body.bytes() }
    }

    @Test
    fun `reports the fraction downloaded of a watched image`() = runTest(UnconfinedTestDispatcher()) {
        serve(256 * 1024)
        val url = server.url("/pic.jpg").toString()
        val progress = ImageDownloadProgress.watch(url)
        val seen = mutableListOf<Float?>()
        val collecting = launch { progress.toList(seen) }

        download(url)

        collecting.cancel()
        ImageDownloadProgress.unwatch(url)
        val fractions = seen.filterNotNull()
        assertEquals(null, seen.first())
        assertTrue(fractions.size > 2, "only $fractions")
        assertEquals(fractions.sorted(), fractions)
        assertEquals(1f, fractions.last())
    }

    @Test
    fun `leaves images nobody watches alone`() {
        serve(1024)
        val url = server.url("/other.jpg").toString()
        val watched = server.url("/watched.jpg").toString()
        val progress = ImageDownloadProgress.watch(watched)

        download(url)

        assertNull(progress.value)
        ImageDownloadProgress.unwatch(watched)
    }

    @Test
    fun `stops tracking once the last watcher leaves`() {
        val url = server.url("/twice.jpg").toString()
        ImageDownloadProgress.watch(url)
        val second = ImageDownloadProgress.watch(url)
        ImageDownloadProgress.unwatch(url)
        serve(1024)
        download(url)
        assertEquals(1f, second.value)

        ImageDownloadProgress.unwatch(url)
        val again = ImageDownloadProgress.watch(url)
        assertNull(again.value)
        ImageDownloadProgress.unwatch(url)
    }
}
