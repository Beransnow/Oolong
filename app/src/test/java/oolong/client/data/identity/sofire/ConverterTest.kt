package oolong.client.data.identity.sofire

import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import oolong.client.data.identity.sofire.SofireResponse
import oolong.client.data.identity.sofire.asConverterFactory
import org.junit.Assert.assertEquals
import org.junit.Test
import retrofit2.Retrofit

class ConverterTest {
    private val retrofit = Retrofit.Builder()
        .baseUrl("https://example.com/")
        .addConverterFactory(Json { ignoreUnknownKeys = true }.asConverterFactory())
        .build()

    @Test
    fun readsSerializableResponsesAsTheLastConverter() {
        val converter = retrofit.responseBodyConverter<SofireResponse>(SofireResponse::class.java, emptyArray())
        val body = """{"data":"d","request_id":1,"skey":"k","extra":0}""".toResponseBody("application/json".toMediaType())
        assertEquals(SofireResponse("d", 1, "k"), converter.convert(body))
    }
}
