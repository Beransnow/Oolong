package com.huanchengfly.tieba.post.api.retrofit.interfaces

import android.os.Build
import android.util.Base64
import com.huanchengfly.tieba.post.App
import com.huanchengfly.tieba.post.api.Header
import com.huanchengfly.tieba.post.api.Param
import com.huanchengfly.tieba.post.api.getScreenDensity
import com.huanchengfly.tieba.post.api.getScreenHeight
import com.huanchengfly.tieba.post.api.getScreenWidth
import com.huanchengfly.tieba.post.api.models.*
import com.huanchengfly.tieba.post.api.models.PicPageBean
import com.huanchengfly.tieba.post.api.retrofit.ApiResult
import com.huanchengfly.tieba.post.utils.AccountUtil
import com.huanchengfly.tieba.post.utils.InstallState
import com.huanchengfly.tieba.post.utils.MobileInfoUtil
import com.huanchengfly.tieba.post.utils.UIDUtil
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.flow.Flow
import retrofit2.http.*

interface OfficialTiebaApi {
    @POST("/c/f/pb/picpage")
    @FormUrlEncoded
    fun picPageFlow(
        @Field("forum_id") forumId: String,
        @Field("kw") forumName: String,
        @Field("tid") threadId: String,
        @Field("pic_id") picId: String,
        @Field("pic_index") picIndex: String,
        @Field("obj_type") objType: String,
        @Field("prev") prev: Int,
        @Field("next") next: Int,
        @Field("not_see_lz") notSeeLz: Int,
        @Field("post_id") postId: String? = null,
        @Field("page_name") pageName: String = "PB",
        @Field("user_id") userId: String? = AccountUtil.getUid(),
        @Field("scr_h") scrH: String = getScreenHeight().toString(),
        @Field("scr_w") scrW: String = getScreenWidth().toString(),
        @Field("q_type") qType: Int = 2,
        @Field("_os_version") osVersion: String = Build.VERSION.RELEASE,
        @Field("applist") appList: String = "",
    ): Flow<PicPageBean>

    @Headers(
        "${Header.FORCE_LOGIN}: ${Header.FORCE_LOGIN_TRUE}",
        "${Header.NO_COMMON_PARAMS}: BDUSS"
    )
    @POST("/c/f/forum/getforumlist")
    @FormUrlEncoded
    fun getForumListFlow(
        @Field("BDUSS") bduss: String = AccountUtil.getBduss()!!,
        @Field("stoken") stoken: String = AccountUtil.getSToken()!!,
        @Field("user_id") userId: String = AccountUtil.getUid()!!,
    ): Flow<GetForumListBean>

    @Headers(
        "${Header.NO_COMMON_PARAMS}: BDUSS"
    )
    @POST("/c/s/initNickname")
    @FormUrlEncoded
    fun initNickNameFlow(
        @Field("BDUSS") bduss: String = AccountUtil.getBduss()!!,
        @Field("stoken") sToken: String = AccountUtil.getSToken()!!,
    ): Flow<InitNickNameBean>

    @Headers(
        "${Header.NO_COMMON_PARAMS}: BDUSS"
    )
    @POST("/c/s/login")
    @FormUrlEncoded
    fun loginFlow(
        @Field("bdusstoken") bdusstoken: String = "${AccountUtil.getBduss()!!}|",
        @Field("stoken") sToken: String = AccountUtil.getSToken()!!,
        @Field("first_login") firstLogin: String = InstallState.firstLogin,
        @Field("channel_id") channelId: String = "",
        @Field("channel_uid") channelUid: String = "",
        @Field("send_tb") sendTb: String = "0",
    ): Flow<LoginBean>

    /**
     * Sync sends the IMEI, Android ID and MAC under Base64-encoded aliases
     * (`iemi`, `di_diordna`, `cam`) instead of the plain common parameters.
     */
    @Headers(
        "${Header.NO_COMMON_PARAMS}: ${Param.MAC},${Param.PHONE_IMEI},${Param.ANDROID_ID}",
    )
    @POST("/c/s/sync")
    @FormUrlEncoded
    fun sync(
        @Field(Param.CLIENT_ID) clientId: String?,
        @Field("push_system_switch") pushSystemSwitch: String,
        @Field("last_push_system_switch") lastPushSystemSwitch: String,
        @Field("last_push_system_switch_time") lastPushSystemSwitchTime: Long,
        @Field("running_abi") runningAbi: String,
        @Field("support_abi") supportAbi: String,
        @Field("install_app_time") installAppTime: Long,
        @Field("_os_version") osVersion: String = Build.VERSION.RELEASE,
        @Field("brand") brand: String = Build.BRAND,
        @Field("model") model: String = Build.MODEL,
        @Field("board") board: String = Build.BOARD,
        @Field("incremental") incremental: String = Build.VERSION.INCREMENTAL,
        @Field("_phone_screen") phoneScreen: String = "${getScreenWidth()},${getScreenHeight()}",
        @Field("scr_w") scr_w: String = getScreenWidth().toString(),
        @Field("scr_h") scr_h: String = getScreenHeight().toString(),
        @Field("scr_dip") scr_dip: String = getScreenDensity().toString(),
        @Field("_msg_status") msgStatus: String = "1",
        @Field("_active") active: String = "0",
        @Field("_pic_quality") picQuality: String = "0",
        @Field("package") packageName: String = "com.baidu.tieba",
        @Field("versioncode") versionCode: String = "204734720",
        @Field("signmd5") signmd5: String = "225172691",
        @Field("md5") md5: String = "F86F4C238491AB3BEBFA33AC42C1582B",
        @Field("iemi") imeiAlias: String = base64NoWrap(MobileInfoUtil.getIMEI(App.INSTANCE)),
        @Field("di_diordna") androidIdAlias: String = base64NoWrap(UIDUtil.getAndroidId("")),
        @Field("cam") macAlias: String = base64NoWrap("02:00:00:00:00:00"),
    ): Flow<Sync>

    @Headers("${Header.FORCE_LOGIN}: ${Header.FORCE_LOGIN_TRUE}")
    @POST("/c/f/ueg/checkjubao")
    @FormUrlEncoded
    fun checkReportAsync(
        @Field("category") category: String,
        @FieldMap reportParam: Map<String, String>,
        @Field("stoken") stoken: String? = AccountUtil.getLoginInfo()
            ?.sToken,
    ): Deferred<ApiResult<CheckReportBean>>

}

internal fun base64NoWrap(text: String): String =
    Base64.encodeToString(text.toByteArray(), Base64.NO_WRAP)
