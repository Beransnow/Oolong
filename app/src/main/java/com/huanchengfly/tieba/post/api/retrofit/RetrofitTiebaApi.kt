package com.huanchengfly.tieba.post.api.retrofit

import android.os.Build
import com.huanchengfly.tieba.core.network.ProfileInterceptor
import com.huanchengfly.tieba.core.network.TiebaProfile
import com.huanchengfly.tieba.core.network.form.FormParamsInterceptor
import com.huanchengfly.tieba.post.App
import com.huanchengfly.tieba.post.BuildConfig
import com.huanchengfly.tieba.post.api.ClientVersion
import com.huanchengfly.tieba.post.api.Header
import com.huanchengfly.tieba.post.api.Param
import com.huanchengfly.tieba.post.api.legacyCommonParams
import com.huanchengfly.tieba.post.api.models.OAID
import com.huanchengfly.tieba.post.api.retrofit.adapter.DeferredCallAdapterFactory
import com.huanchengfly.tieba.post.api.retrofit.adapter.FlowCallAdapterFactory
import com.huanchengfly.tieba.post.api.retrofit.converter.gson.GsonConverterFactory
import com.huanchengfly.tieba.post.api.retrofit.converter.kotlinx.serialization.asConverterFactory
import com.huanchengfly.tieba.post.api.retrofit.interceptors.CommonHeaderInterceptor
import com.huanchengfly.tieba.post.api.retrofit.interceptors.CommonParamInterceptor
import com.huanchengfly.tieba.post.api.retrofit.interceptors.ConnectivityInterceptor
import com.huanchengfly.tieba.post.api.retrofit.interceptors.CookieInterceptor
import com.huanchengfly.tieba.post.api.retrofit.interceptors.DropInterceptor
import com.huanchengfly.tieba.post.api.retrofit.interceptors.FailureResponseInterceptor
import com.huanchengfly.tieba.post.api.retrofit.interceptors.ForceLoginInterceptor
import com.huanchengfly.tieba.post.api.retrofit.interceptors.ProtoFailureResponseInterceptor
import com.huanchengfly.tieba.post.api.retrofit.interceptors.RequestRecorder
import com.huanchengfly.tieba.post.api.retrofit.interceptors.SortAndSignInterceptor
import com.huanchengfly.tieba.post.api.retrofit.interceptors.StParamInterceptor
import com.huanchengfly.tieba.post.api.retrofit.interfaces.AppHybridTiebaApi
import com.huanchengfly.tieba.post.api.retrofit.interfaces.OfficialProtobufTiebaApi
import com.huanchengfly.tieba.post.api.retrofit.interfaces.OfficialTiebaApi
import com.huanchengfly.tieba.post.api.retrofit.interfaces.SofireApi
import com.huanchengfly.tieba.post.toJson
import com.huanchengfly.tieba.post.utils.AccountUtil
import com.huanchengfly.tieba.post.utils.ClientUtils
import com.huanchengfly.tieba.post.utils.CuidUtils
import com.huanchengfly.tieba.post.utils.MobileInfoUtil
import com.huanchengfly.tieba.post.utils.UIDUtil
import kotlinx.serialization.json.Json
import okhttp3.ConnectionPool
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.wire.WireConverterFactory
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt


object RetrofitTiebaApi {
    private const val READ_TIMEOUT = 60L
    private const val CONNECT_TIMEOUT = 60L
    private const val WRITE_TIMEOUT = 60L

    private val initTime = System.currentTimeMillis()
    internal val randomClientId = "wappc_${initTime}_${(Math.random() * 1000).roundToInt()}"
    private val stParamInterceptor = StParamInterceptor()
    private val connectionPool = ConnectionPool(32, 5, TimeUnit.MINUTES)

    private val defaultCommonParamInterceptor = CommonParamInterceptor(
        Param.BDUSS to { AccountUtil.getBduss() },
        Param.CLIENT_ID to { ClientUtils.clientId },
        Param.CLIENT_TYPE to { "2" },
        Param.OS_VERSION to { Build.VERSION.SDK_INT.toString() },
        Param.MODEL to { Build.MODEL },
        Param.NET_TYPE to { "1" },
        Param.PHONE_IMEI to { MobileInfoUtil.getIMEI(App.INSTANCE) },
        Param.TIMESTAMP to { System.currentTimeMillis().toString() }
    )

    private val defaultCommonHeaderInterceptor =
        CommonHeaderInterceptor(
            Header.COOKIE to { "ka=open" },
            Header.PRAGMA to { "no-cache" }
        )
    private val gsonConverterFactory = GsonConverterFactory.create()
    private val sortAndSignInterceptor = SortAndSignInterceptor("tiebaclient!!!")

    /** Data requests of the in-app search page, sent as the web view sends them. */
    val HYBRID_TIEBA_API: AppHybridTiebaApi by lazy {
        createJsonApi<AppHybridTiebaApi>(
            TiebaProfile.WEB_12_52.baseUrl,
            ProfileInterceptor(TiebaProfile.WEB_12_52, legacyCommonParams),
            CommonHeaderInterceptor(
                Header.PRAGMA to { "no-cache" },
                Header.CACHE_CONTROL to { "no-cache" },
                Header.ACCEPT to { "application/json, text/plain, */*" },
                Header.ACCEPT_LANGUAGE to { Header.ACCEPT_LANGUAGE_VALUE },
                "X-Requested-With" to { "com.baidu.tieba" },
                "Sec-Fetch-Site" to { "same-origin" },
                "Sec-Fetch-Mode" to { "cors" },
                "Sec-Fetch-Dest" to { "empty" },
            ),
        )
    }

    /** Official form endpoints, sent through the classic form stack. */
    val OFFICIAL_TIEBA_API: OfficialTiebaApi by lazy {
        createFormApi<OfficialTiebaApi>(TiebaProfile.FORM_12_52)
    }

    val OFFICIAL_PROTOBUF_TIEBA_API: OfficialProtobufTiebaApi by lazy {
        createProtobufApi<OfficialProtobufTiebaApi>(
            "https://tiebac.baidu.com/",
            CommonHeaderInterceptor(
                Header.CHARSET to { "UTF-8" },
                Header.CLIENT_TYPE to { "2" },
                Header.CLIENT_USER_TOKEN to { AccountUtil.getUid() },
                Header.COOKIE to { "CUID=${CuidUtils.getNewCuid()};ka=open;TBBRAND=${Build.MODEL};" },
                Header.CUID to { CuidUtils.getNewCuid() },
                Header.CUID_GALAXY2 to { CuidUtils.getNewCuid() },
                Header.CUID_GID to { "" },
                Header.CUID_GALAXY3 to { UIDUtil.getAid() },
                Header.USER_AGENT to { "bdtb for Android ${ClientVersion.TIEBA_V11.version}" },
                Header.X_BD_DATA_TYPE to { "protobuf" },
            ),
            defaultCommonParamInterceptor - Param.OS_VERSION + CommonParamInterceptor(
                Param.CUID to { CuidUtils.getNewCuid() },
                Param.CUID_GALAXY2 to { CuidUtils.getNewCuid() },
                Param.CUID_GID to { "" },
                Param.FROM to { "tieba" },
                Param.CLIENT_VERSION to { ClientVersion.TIEBA_V11.version },
                Param.CUID_GALAXY3 to { UIDUtil.getAid() },
                Param.OAID to { OAID().toJson() },
            ),
            stParamInterceptor,
        )
    }

    val OFFICIAL_PROTOBUF_TIEBA_V12_API: OfficialProtobufTiebaApi by lazy {
        createOfficialProtobufApi(TiebaProfile.PROTO_12_52)
    }

    val OFFICIAL_PROTOBUF_TIEBA_V22_API: OfficialProtobufTiebaApi by lazy {
        createOfficialProtobufApi(TiebaProfile.PROTO_22_FLOOR)
    }

    private val json = Json {
        isLenient = true
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    val SOFIRE_API: SofireApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://sofire.baidu.com/")
            .addCallAdapterFactory(DeferredCallAdapterFactory())
            .addCallAdapterFactory(FlowCallAdapterFactory.create())
            .addConverterFactory(NullOnEmptyConverterFactory())
            .addConverterFactory(json.asConverterFactory())
            .addConverterFactory(gsonConverterFactory)
            .client(OkHttpClient.Builder().apply {
//                addInterceptor()
                if (BuildConfig.DEBUG) addNetworkInterceptor(RequestRecorder)
                connectionPool(connectionPool)
            }.build())
            .build()
            .create(SofireApi::class.java)
    }

    private inline fun <reified T : Any> createJsonApi(
        baseUrl: String,
        vararg interceptors: Interceptor
    ) = Retrofit.Builder()
        .baseUrl(baseUrl)
        .addCallAdapterFactory(DeferredCallAdapterFactory())
        .addCallAdapterFactory(FlowCallAdapterFactory.create())
        .addConverterFactory(NullOnEmptyConverterFactory())
        .addConverterFactory(json.asConverterFactory())
        .addConverterFactory(gsonConverterFactory)
        .client(OkHttpClient.Builder().apply {
            readTimeout(READ_TIMEOUT, TimeUnit.SECONDS)
            connectTimeout(CONNECT_TIMEOUT, TimeUnit.SECONDS)
            writeTimeout(WRITE_TIMEOUT, TimeUnit.SECONDS)
            interceptors.forEach {
                addInterceptor(it)
            }
            addInterceptor(DropInterceptor)
            addInterceptor(FailureResponseInterceptor)
            addInterceptor(ForceLoginInterceptor)
            addInterceptor(sortAndSignInterceptor)
            addInterceptor(ConnectivityInterceptor)
            if (BuildConfig.DEBUG) addNetworkInterceptor(RequestRecorder)
            connectionPool(connectionPool)
        }.build())
        .build()
        .create(T::class.java)

    private inline fun <reified T : Any> createFormApi(profile: TiebaProfile) = Retrofit.Builder()
        .baseUrl(profile.baseUrl)
        .addCallAdapterFactory(DeferredCallAdapterFactory())
        .addCallAdapterFactory(FlowCallAdapterFactory.create())
        .addConverterFactory(NullOnEmptyConverterFactory())
        .addConverterFactory(json.asConverterFactory())
        .addConverterFactory(gsonConverterFactory)
        .client(OkHttpClient.Builder().apply {
            readTimeout(READ_TIMEOUT, TimeUnit.SECONDS)
            connectTimeout(CONNECT_TIMEOUT, TimeUnit.SECONDS)
            writeTimeout(WRITE_TIMEOUT, TimeUnit.SECONDS)
            addInterceptor(ProfileInterceptor(profile, legacyCommonParams))
            addInterceptor(DropInterceptor)
            addInterceptor(FormParamsInterceptor(profile, legacyCommonParams))
            addInterceptor(FailureResponseInterceptor)
            addInterceptor(ForceLoginInterceptor)
            addInterceptor(CookieInterceptor)
            addInterceptor(ConnectivityInterceptor)
            if (BuildConfig.DEBUG) addNetworkInterceptor(RequestRecorder)
            connectionPool(connectionPool)
        }.build())
        .build()
        .create(T::class.java)

    private fun createOfficialProtobufApi(profile: TiebaProfile): OfficialProtobufTiebaApi =
        createProtobufApi<OfficialProtobufTiebaApi>(
            "https://tiebac.baidu.com/",
            ProfileInterceptor(profile, legacyCommonParams),
            stParamInterceptor,
        )

    private inline fun <reified T : Any> createProtobufApi(
        baseUrl: String,
        vararg interceptors: Interceptor
    ) = Retrofit.Builder()
        .baseUrl(baseUrl)
        .addCallAdapterFactory(DeferredCallAdapterFactory())
        .addCallAdapterFactory(FlowCallAdapterFactory.create())
        .addConverterFactory(NullOnEmptyConverterFactory())
        .addConverterFactory(WireConverterFactory.create())
        .client(OkHttpClient.Builder().apply {
            readTimeout(READ_TIMEOUT, TimeUnit.SECONDS)
            connectTimeout(CONNECT_TIMEOUT, TimeUnit.SECONDS)
            writeTimeout(WRITE_TIMEOUT, TimeUnit.SECONDS)
            interceptors.forEach {
                addInterceptor(it)
            }
            addInterceptor(DropInterceptor)
            addInterceptor(ProtoFailureResponseInterceptor)
            addInterceptor(ForceLoginInterceptor)
            addInterceptor(CookieInterceptor)
            addInterceptor(sortAndSignInterceptor)
            addInterceptor(ConnectivityInterceptor)
            if (BuildConfig.DEBUG) addNetworkInterceptor(RequestRecorder)
            connectionPool(connectionPool)
        }.build())
        .build()
        .create(T::class.java)
}