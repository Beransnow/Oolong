package oolong.client.data.identity.sofire

import kotlinx.serialization.json.Json
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import oolong.client.BuildConfig
import oolong.client.data.identity.sofire.FlowCallAdapterFactory
import oolong.client.data.identity.sofire.SofireApi
import oolong.client.data.identity.sofire.asConverterFactory
import oolong.client.diagnostics.RequestRecorder
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit

/** The Retrofit client of Sofire, which issues the device token; Tieba requests go through TiebaNetwork. */
object RetrofitTiebaApi {
    private val connectionPool = ConnectionPool(32, 5, TimeUnit.MINUTES)

    private val json = Json {
        isLenient = true
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    val SOFIRE_API: SofireApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://sofire.baidu.com/")
            .addCallAdapterFactory(FlowCallAdapterFactory.create())
            .addConverterFactory(NullOnEmptyConverterFactory())
            .addConverterFactory(json.asConverterFactory())
            .client(OkHttpClient.Builder().apply {
                if (BuildConfig.DEBUG) addNetworkInterceptor(RequestRecorder)
                connectionPool(connectionPool)
            }.build())
            .build()
            .create(SofireApi::class.java)
    }
}
