package com.huanchengfly.tieba.post.api

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import com.huanchengfly.tieba.core.network.CommonParams
import com.huanchengfly.tieba.core.network.TiebaNetwork
import com.huanchengfly.tieba.core.network.agree.AgreeRemoteDataSource
import com.huanchengfly.tieba.core.network.explore.ExploreRemoteDataSource
import com.huanchengfly.tieba.core.network.forum.ForumRemoteDataSource
import com.huanchengfly.tieba.core.network.forum.LikeRemoteDataSource
import com.huanchengfly.tieba.core.network.message.MessageRemoteDataSource
import com.huanchengfly.tieba.core.network.post.PostRemoteDataSource
import com.huanchengfly.tieba.core.network.search.SearchRemoteDataSource
import com.huanchengfly.tieba.core.network.sign.SignRemoteDataSource
import com.huanchengfly.tieba.core.network.thread.ThreadRemoteDataSource
import com.huanchengfly.tieba.core.network.user.UserRemoteDataSource
import com.huanchengfly.tieba.core.network.upload.UploadRemoteDataSource
import com.huanchengfly.tieba.core.network.identity.DeviceIdentity
import com.huanchengfly.tieba.core.network.identity.InstallIdentity
import com.huanchengfly.tieba.core.network.identity.NetworkType
import com.huanchengfly.tieba.core.network.identity.Session
import com.huanchengfly.tieba.core.network.identity.SessionProvider
import com.huanchengfly.tieba.post.App
import com.huanchengfly.tieba.post.BuildConfig
import com.huanchengfly.tieba.post.api.retrofit.interceptors.RequestRecorder
import com.huanchengfly.tieba.post.utils.AccountUtil
import com.huanchengfly.tieba.post.utils.ClientUtils
import com.huanchengfly.tieba.post.utils.CuidUtils
import com.huanchengfly.tieba.post.utils.DeviceUtils
import com.huanchengfly.tieba.post.utils.MobileInfoUtil
import com.huanchengfly.tieba.post.utils.UIDUtil
import dagger.Module
import dagger.Provides
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Reads the identities from the same storage the app has always used, so existing installs keep
 * their `cuid`, `c3_aid` and client id.
 */
private class AppDeviceIdentity(private val context: Context) : DeviceIdentity {
    override val androidId: String get() = UIDUtil.getAndroidId("")
    override val cuid: String get() = CuidUtils.getNewCuid()
    override val c3Aid: String get() = UIDUtil.getAid()
    override val imei: String get() = MobileInfoUtil.getIMEI(context)
    override val oaid: String get() = App.Config.oaid
    override val model: String get() = Build.MODEL
    override val brand: String get() = Build.BRAND
    override val osVersion: String get() = Build.VERSION.RELEASE
    override val screenWidth: Int get() = getScreenWidth()
    override val screenHeight: Int get() = getScreenHeight()
    override val screenDensity: Float get() = getScreenDensity()
    override val networkType: Int get() = currentNetworkType(context)
    override val deviceScore: Float by lazy { DeviceUtils.getNormalizedDeviceScore() }
    override val webViewUserAgent: String get() = getUserAgent()
}

@Suppress("DEPRECATION") // NetworkInfo.subtype is the only way to get the generation without READ_PHONE_STATE
private fun currentNetworkType(context: Context): Int {
    val connectivityManager = context.getSystemService(ConnectivityManager::class.java)
    val network = connectivityManager.activeNetwork ?: return NetworkType.NONE
    val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return NetworkType.NONE
    return when {
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkType.WIFI
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ->
            NetworkType.ofMobile(connectivityManager.getNetworkInfo(network)?.subtype ?: -1)

        else -> NetworkType.MOBILE_4G
    }
}

private object AppInstallIdentity : InstallIdentity {
    override val clientId: String? get() = ClientUtils.clientId
    override val sampleId: String? get() = ClientUtils.sampleId
    override val baiduId: String? get() = ClientUtils.baiduId
    override val activeTimestamp: Long get() = ClientUtils.activeTimestamp
    override val firstInstallTime: Long get() = App.Config.appFirstInstallTime
    override val lastUpdateTime: Long get() = App.Config.appLastUpdateTime
}

private object AccountSessionProvider : SessionProvider {
    override fun currentSession(): Session? = AccountUtil.getLoginInfo()?.let {
        Session(
            uid = it.uid,
            name = it.name,
            bduss = it.bduss,
            stoken = it.sToken.takeIf { token -> token.isNotEmpty() },
            tbs = it.tbs.takeIf { tbs -> tbs.isNotEmpty() },
            zid = it.zid?.takeIf { zid -> zid.isNotEmpty() },
        )
    }

    override fun updateTbs(tbs: String) {
        val account = AccountUtil.getLoginInfo() ?: return
        if (account.tbs == tbs) return
        account.tbs = tbs
        AccountUtil.saveAsync(account)
    }
}

@Module
@InstallIn(SingletonComponent::class)
object NetworkIdentityModule {
    @Provides
    @Singleton
    fun provideDeviceIdentity(@ApplicationContext context: Context): DeviceIdentity =
        AppDeviceIdentity(context)

    @Provides
    fun provideInstallIdentity(): InstallIdentity = AppInstallIdentity

    @Provides
    fun provideSessionProvider(): SessionProvider = AccountSessionProvider

    @Provides
    @Singleton
    fun provideCommonParams(
        device: DeviceIdentity,
        install: InstallIdentity,
        sessionProvider: SessionProvider,
    ): CommonParams = CommonParams(device, install, sessionProvider)

    @Provides
    @Singleton
    fun provideTiebaNetwork(
        commonParams: CommonParams,
        sessionProvider: SessionProvider,
    ): TiebaNetwork = TiebaNetwork(
        commonParams,
        sessionProvider,
        networkInterceptors = if (BuildConfig.DEBUG) listOf(RequestRecorder) else emptyList(),
    )

    @Provides
    @Singleton
    fun provideForumRemoteDataSource(
        network: TiebaNetwork,
        commonParams: CommonParams,
        device: DeviceIdentity,
    ): ForumRemoteDataSource = ForumRemoteDataSource(network, commonParams, device)

    @Provides
    @Singleton
    fun provideSignRemoteDataSource(
        network: TiebaNetwork,
        commonParams: CommonParams,
    ): SignRemoteDataSource = SignRemoteDataSource(network, commonParams)

    @Provides
    @Singleton
    fun provideAgreeRemoteDataSource(
        network: TiebaNetwork,
        commonParams: CommonParams,
    ): AgreeRemoteDataSource = AgreeRemoteDataSource(network, commonParams)

    @Provides
    @Singleton
    fun provideLikeRemoteDataSource(
        network: TiebaNetwork,
        commonParams: CommonParams,
    ): LikeRemoteDataSource = LikeRemoteDataSource(network, commonParams)

    @Provides
    @Singleton
    fun provideThreadRemoteDataSource(
        network: TiebaNetwork,
        commonParams: CommonParams,
        device: DeviceIdentity,
    ): ThreadRemoteDataSource = ThreadRemoteDataSource(network, commonParams, device)

    @Provides
    @Singleton
    fun provideUserRemoteDataSource(
        network: TiebaNetwork,
        commonParams: CommonParams,
        device: DeviceIdentity,
    ): UserRemoteDataSource = UserRemoteDataSource(network, commonParams, device)

    @Provides
    @Singleton
    fun provideExploreRemoteDataSource(
        network: TiebaNetwork,
        commonParams: CommonParams,
        device: DeviceIdentity,
    ): ExploreRemoteDataSource = ExploreRemoteDataSource(network, commonParams, device)

    @Provides
    @Singleton
    fun provideSearchRemoteDataSource(
        network: TiebaNetwork,
        commonParams: CommonParams,
        device: DeviceIdentity,
    ): SearchRemoteDataSource = SearchRemoteDataSource(network, commonParams, device)

    @Provides
    @Singleton
    fun provideMessageRemoteDataSource(
        network: TiebaNetwork,
        commonParams: CommonParams,
        device: DeviceIdentity,
    ): MessageRemoteDataSource = MessageRemoteDataSource(network, commonParams, device)

    @Provides
    @Singleton
    fun providePostRemoteDataSource(
        network: TiebaNetwork,
        commonParams: CommonParams,
        sessionProvider: SessionProvider,
    ): PostRemoteDataSource = PostRemoteDataSource(network, commonParams, sessionProvider)

    @Provides
    @Singleton
    fun provideUploadRemoteDataSource(
        network: TiebaNetwork,
        commonParams: CommonParams,
    ): UploadRemoteDataSource = UploadRemoteDataSource(network, commonParams)
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface LegacyNetworkEntryPoint {
    fun commonParams(): CommonParams
    fun signRemoteDataSource(): SignRemoteDataSource
    fun agreeRemoteDataSource(): AgreeRemoteDataSource
    fun likeRemoteDataSource(): LikeRemoteDataSource
    fun messageRemoteDataSource(): MessageRemoteDataSource
    fun postRemoteDataSource(): PostRemoteDataSource
    fun uploadRemoteDataSource(): UploadRemoteDataSource
}

private val legacyEntryPoint: LegacyNetworkEntryPoint by lazy {
    EntryPointAccessors.fromApplication(App.INSTANCE, LegacyNetworkEntryPoint::class.java)
}

/** For the legacy API singletons, which can't be injected. */
internal val legacyCommonParams: CommonParams get() = legacyEntryPoint.commonParams()

internal val legacySignRemoteDataSource: SignRemoteDataSource
    get() = legacyEntryPoint.signRemoteDataSource()

internal val legacyAgreeRemoteDataSource: AgreeRemoteDataSource
    get() = legacyEntryPoint.agreeRemoteDataSource()

internal val legacyLikeRemoteDataSource: LikeRemoteDataSource
    get() = legacyEntryPoint.likeRemoteDataSource()

internal val legacyMessageRemoteDataSource: MessageRemoteDataSource
    get() = legacyEntryPoint.messageRemoteDataSource()

internal val legacyPostRemoteDataSource: PostRemoteDataSource
    get() = legacyEntryPoint.postRemoteDataSource()

internal val legacyUploadRemoteDataSource: UploadRemoteDataSource
    get() = legacyEntryPoint.uploadRemoteDataSource()
