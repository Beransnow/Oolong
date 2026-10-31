package com.huanchengfly.tieba.post.api.retrofit.interfaces

import android.os.Build
import android.util.Base64
import com.huanchengfly.tieba.core.network.form.FormParamsInterceptor
import com.huanchengfly.tieba.post.App
import com.huanchengfly.tieba.post.App.ScreenInfo
import com.huanchengfly.tieba.post.api.ClientVersion
import com.huanchengfly.tieba.post.api.Header
import com.huanchengfly.tieba.post.api.Param
import com.huanchengfly.tieba.post.api.getScreenDensity
import com.huanchengfly.tieba.post.api.getScreenHeight
import com.huanchengfly.tieba.post.api.getScreenWidth
import com.huanchengfly.tieba.post.api.models.*
import com.huanchengfly.tieba.post.api.retrofit.ApiResult
import com.huanchengfly.tieba.post.api.retrofit.body.MyMultipartBody
import com.huanchengfly.tieba.post.utils.AccountUtil
import com.huanchengfly.tieba.post.utils.ClientUtils
import com.huanchengfly.tieba.post.utils.InstallState
import com.huanchengfly.tieba.post.utils.MobileInfoUtil
import com.huanchengfly.tieba.post.utils.UIDUtil
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.flow.Flow
import okhttp3.RequestBody
import retrofit2.Call
import retrofit2.http.*


interface OfficialTiebaApi {
    @POST("/c/f/pb/page")
    @FormUrlEncoded
    fun threadContent(
        @Field("kz") threadId: String,
        @Field("pn") page: Int,
        @Field("last") last: String?,
        @Field("r") r: String?,
        @Field("lz") lz: Int,
        @Field("st_type") st_type: String = "tb_frslist",
        @Field("back") back: String = "0",
        @Field("floor_rn") floor_rn: String = "3",
        @Field("mark") mark: String = "0",
        @Field("rn") rn: String = "30",
        @Field("with_floor") with_floor: String = "1",
        @Field("scr_dip") scr_dip: String = ScreenInfo.DENSITY.toString(),
        @Field("scr_h") scr_h: String = getScreenHeight().toString(),
        @Field("scr_w") scr_w: String = getScreenWidth().toString(),
        @retrofit2.http.Header("thread_id") threadIdHeader: String = threadId,
    ): Call<ThreadContentBean>

    @POST("/c/f/pb/page")
    @FormUrlEncoded
    fun threadContent(
        @Field("kz") threadId: String,
        @Field("pid") postId: String?,
        @Field("last") last: String?,
        @Field("r") r: String?,
        @Field("lz") lz: Int,
        @Field("st_type") st_type: String = "tb_frslist",
        @Field("back") back: String = "0",
        @Field("floor_rn") floor_rn: String = "3",
        @Field("mark") mark: String = "0",
        @Field("rn") rn: String = "30",
        @Field("with_floor") with_floor: String = "1",
        @Field("scr_dip") scr_dip: String = ScreenInfo.DENSITY.toString(),
        @Field("scr_h") scr_h: String = getScreenHeight().toString(),
        @Field("scr_w") scr_w: String = getScreenWidth().toString(),
        @retrofit2.http.Header("thread_id") threadIdHeader: String = threadId,
    ): Call<ThreadContentBean>

    @POST("/c/f/pb/page")
    @FormUrlEncoded
    fun threadContentAsync(
        @Field("kz") threadId: String,
        @Field("pn") page: Int,
        @Field("last") last: String?,
        @Field("r") r: String?,
        @Field("lz") lz: Int,
        @Field("st_type") st_type: String = "tb_frslist",
        @Field("back") back: String = "0",
        @Field("floor_rn") floor_rn: String = "3",
        @Field("mark") mark: String = "0",
        @Field("rn") rn: String = "30",
        @Field("with_floor") with_floor: String = "1",
        @Field("scr_dip") scr_dip: String = ScreenInfo.DENSITY.toString(),
        @Field("scr_h") scr_h: String = getScreenHeight().toString(),
        @Field("scr_w") scr_w: String = getScreenWidth().toString(),
        @retrofit2.http.Header("thread_id") threadIdHeader: String = threadId,
    ): Deferred<ApiResult<ThreadContentBean>>

    @POST("/c/f/pb/page")
    @FormUrlEncoded
    fun threadContentAsync(
        @Field("kz") threadId: String,
        @Field("pid") postId: String?,
        @Field("last") last: String?,
        @Field("r") r: String?,
        @Field("lz") lz: Int,
        @Field("st_type") st_type: String = "tb_frslist",
        @Field("back") back: String = "0",
        @Field("floor_rn") floor_rn: String = "3",
        @Field("mark") mark: String = "0",
        @Field("rn") rn: String = "30",
        @Field("with_floor") with_floor: String = "1",
        @Field("scr_dip") scr_dip: String = ScreenInfo.DENSITY.toString(),
        @Field("scr_h") scr_h: String = getScreenHeight().toString(),
        @Field("scr_w") scr_w: String = getScreenWidth().toString(),
        @retrofit2.http.Header("thread_id") threadIdHeader: String = threadId,
    ): Deferred<ApiResult<ThreadContentBean>>

    @Headers("${Header.FORCE_LOGIN}: ${Header.FORCE_LOGIN_TRUE}")
    @POST("/c/c/excellent/submitDislike")
    @FormUrlEncoded
    fun submitDislike(
        @Field("dislike") dislike: String,
        @Field("dislike_from") dislike_from: String = "homepage",
        @Field("stoken") stoken: String = AccountUtil.getSToken()!!
    ): Call<CommonResponse>

    @Headers("${Header.FORCE_LOGIN}: ${Header.FORCE_LOGIN_TRUE}")
    @POST("/c/c/excellent/submitDislike")
    @FormUrlEncoded
    fun submitDislikeFlow(
        @Field("dislike") dislike: String,
        @Field("dislike_from") dislike_from: String = "homepage",
        @Field("stoken") stoken: String? = AccountUtil.getSToken()
    ): Flow<CommonResponse>

    @Headers("${Header.FORCE_LOGIN}: ${Header.FORCE_LOGIN_TRUE}")
    @POST("/c/c/user/unfollow")
    @FormUrlEncoded
    fun unfollowFlow(
        @Field("portrait") portrait: String,
        @Field("tbs") tbs: String,
        @Field("authsid") authsid: String = "null",
        @Field("stoken") stoken: String = AccountUtil.getSToken()!!,
        @Field("from_type") fromType: Int = 2,
        @Field("in_live") inLive: Int = 0,
        @Field("timestamp") timestamp: Long = System.currentTimeMillis()
    ): Flow<CommonResponse>

    @Headers(
        "${Header.FORCE_LOGIN}: ${Header.FORCE_LOGIN_TRUE}",
        "${FormParamsInterceptor.WITH_SIG}: 1",
    )
    @POST("/c/c/user/follow")
    @FormUrlEncoded
    fun followFlow(
        @Field("portrait") portrait: String,
        @Field("tbs") tbs: String,
        @Field("authsid") authsid: String = "null",
        @Field("stoken") stoken: String = AccountUtil.getSToken()!!,
        @Field("from_type") fromType: Int = 2,
        @Field("in_live") inLive: Int = 0
    ): Flow<FollowBean>

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

    @Headers("${Header.FORCE_LOGIN}: ${Header.FORCE_LOGIN_TRUE}")
    @POST("/c/f/forum/forumGuide")
    @FormUrlEncoded
    fun forumGuideFlow(
        @Field("page_no") pageNo: Int,
        @Field("res_num") resNum: Int,
        @Field("sort_type") sortType: Int = 3,
        @Field("call_from") callFrom: Int = 3,
        @Field("top_forum_num") topForumNum: Int = 0,
        @Field("tbs") tbs: String? = AccountUtil.getAccountInfo { this.tbs },
        @Field("stoken") stoken: String = AccountUtil.getSToken()!!,
    ): Flow<ForumGuideBean>

    @Headers("${Header.FORCE_LOGIN}: ${Header.FORCE_LOGIN_TRUE}")
    @POST("/c/c/forum/msign")
    @FormUrlEncoded
    fun mSignFlow(
        @Field("forum_ids") forumIds: String,
        @Field("tbs") tbs: String,
        @Field("authsid") authsid: String = "null",
        @Field("stoken") stoken: String = AccountUtil.getSToken()!!,
        @Field("user_id") userId: String = AccountUtil.getUid()!!
    ): Flow<MSignBean>

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

    @POST("/c/u/user/profile")
    @FormUrlEncoded
    fun profileFlow(
        @Field("stoken") sToken: String = AccountUtil.getSToken()!!,
        @Field("tbs") tbs: String = AccountUtil.getLoginInfo()!!.tbs,
        @Field("uid") userId: String? = AccountUtil.getUid(),
        @Field("is_from_usercenter") isFromUserCenter: String = "1",
        @Field("need_post_count") needPostCount: String = "1",
        @Field("page") page: String = "1",
        @Field("pn") pn: String = "1",
    ): Flow<Profile>

    @POST("/c/c/profile/modify")
    @FormUrlEncoded
    fun profileModify(
        @Field("birthday_show_status") birthdayShowStatus: String,
        @Field("birthday_time") birthdayTime: String,
        @Field("intro") intro: String,
        @Field("sex") sex: String,
        @Field("nick_name") nickName: String,
        @Field("stoken") sToken: String = AccountUtil.getSToken()!!,
        @Field("cam") cam: String = "",
        @Field("need_cam_decrypt") needCamDecrypt: String = "1",
        @Field("need_keep_nickname_flag") needKeepNicknameFlag: String = "0",
    ): Flow<CommonResponse>

    @Headers(
        "${Header.FORCE_LOGIN}: ${Header.FORCE_LOGIN_TRUE}",
        "${Header.COOKIE}: ka=open",
        "${Header.DROP_HEADERS}: ${Header.CHARSET},${Header.CLIENT_TYPE}",
        "${Header.NO_COMMON_PARAMS}: ${Param.OAID}"
    )
    @POST("/c/c/img/portrait")
    fun imgPortrait(
        @Body body: MyMultipartBody,
        @retrofit2.http.Header(Header.USER_AGENT) user_agent: String = "bdtb for Android ${ClientVersion.TIEBA_V12.version}",
    ): Flow<CommonResponse>

    @POST("/c/f/excellent/personalized")
    @FormUrlEncoded
    fun personalizedFlow(
        @Field("load_type") load_type: Int,
        @Field("pn") page: Int = 1,
        @Field("cuid_gid") cuid_gid: String = "",
        @Field("need_tags") need_tags: Int = 0,
        @Field("page_thread_count") page_thread_count: Int = 15,
        @Field("pre_ad_thread_count") pre_ad_thread_count: Int = 0,
        @Field("sug_count") sug_count: Int = 0,
        @Field("tag_code") tag_code: Int = 0,
        @Field("q_type") q_type: Int = 1,
        @Field("need_forumlist") need_forumlist: Int = 0,
        @Field("new_net_type") new_net_type: Int = 1,
        @Field("new_install") new_install: Int = 0,
        @Field("request_time") request_time: Long = System.currentTimeMillis(),
        @Field("invoke_source") invoke_source: String = "",
        @Field("scr_dip") scr_dip: String = ScreenInfo.DENSITY.toString(),
        @Field("scr_h") scr_h: String = getScreenHeight().toString(),
        @Field("scr_w") scr_w: String = getScreenWidth().toString()
    ): Flow<PersonalizedBean>

    @Headers(
        "${Header.FORCE_LOGIN}: ${Header.FORCE_LOGIN_TRUE}",
    )
    @POST("/c/c/forum/sign")
    @FormUrlEncoded
    fun signFlow(
        @Field("fid") forumId: String,
        @Field("kw") forumName: String,
        @Field("tbs") tbs: String,
    ): Flow<SignResultBean>

    @POST("/c/c/forum/unfavolike")
    @FormUrlEncoded
    @Headers(
        "${Header.FORCE_LOGIN}: ${Header.FORCE_LOGIN_TRUE}",
    )
    fun unfavolike(
        @Field("fid") forumId: String,
        @Field("kw") forumName: String,
        @Field("tbs") tbs: String,
        @Field("stoken") stoken: String? = AccountUtil.getSToken(),
    ): Flow<CommonResponse>

    @POST("/c/f/post/threadstore")
    @FormUrlEncoded
    @Headers(
        "${Header.FORCE_LOGIN}: ${Header.FORCE_LOGIN_TRUE}",
    )
    fun threadStoreFlow(
        @Field("rn") pageSize: Int,
        @Field("offset") offset: Int,
        @Field("stoken") stoken: String? = AccountUtil.getSToken(),
        @Field("user_id") user_id: String? = AccountUtil.getUid(),
    ): Flow<ThreadStoreBean>

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

    @Headers(
        "${Header.FORCE_LOGIN}: ${Header.FORCE_LOGIN_TRUE}",
    )
    @POST("/c/c/post/add")
    @FormUrlEncoded
    fun addPostFlow(
        @Field("content") content: String,
        @Field("fid") forumId: String,
        @Field("kw") forumName: String,
        @Field("tbs") tbs: String,
        @Field("tid") threadId: String,
        @Field("quote_id") quoteId: String? = null,
        @Field("repostid") repostId: String? = null,
        @Field("reply_uid") replyUserId: String = "null",
        @Field("name_show") nameShow: String? = AccountUtil.getLoginInfo()?.nameShow,
        @Field("anonymous") anonymous: String = "1",
        @Field("authsid") authsid: String = "null",
        @Field("barrage_time") barrage_time: String = "0",
        @Field("can_no_forum") can_no_forum: String = "0",
        @Field("entrance_type") entrance_type: String = "0",
        @Field("from_fourm_id") from_fourm_id: String = "null",
        @Field("is_ad") is_ad: String = "0",
        @Field("is_addition") is_addition: String? = null,
        @Field("is_barrage") is_barrage: String? = "0",
        @Field("is_feedback") is_feedback: String = "0",
        @Field("is_giftpost") is_giftpost: String? = null,
        @Field("is_twzhibo_thread") is_twzhibo_thread: String? = null,
        @Field("new_vcode") new_vcode: String = "1",
        @Field("post_from") post_from: String = "3",
        @Field("takephoto_num") takephoto_num: String = "0",
        @Field("v_fid") v_fid: String = "",
        @Field("v_fname") v_fname: String = "",
        @Field("vcode_tag") vcode_tag: String = "12",
        @Field("stoken") sToken: String? = AccountUtil.getSToken(),
    ): Flow<AddPostBean>

    @POST("/c/c/post/rmstore")
    @FormUrlEncoded
    @Headers(
        "${Header.FORCE_LOGIN}: ${Header.FORCE_LOGIN_TRUE}",
    )
    fun removeStoreFlow(
        @Field("tid") threadId: String,
        @Field("fid") forumId: String = "null",
        @Field("tbs") tbs: String = AccountUtil.getLoginInfo()!!.tbs,
        @Field("stoken") stoken: String = AccountUtil.getSToken()!!,
        @Field("user_id") user_id: String? = AccountUtil.getUid(),
    ): Flow<CommonResponse>

    @POST("/c/c/post/addstore")
    @FormUrlEncoded
    @Headers(
        "${Header.FORCE_LOGIN}: ${Header.FORCE_LOGIN_TRUE}",
    )
    fun addStoreAsync(
        @Field("data") data: String,
        @Field("stoken") stoken: String? = AccountUtil.getSToken(),
    ): Deferred<ApiResult<CommonResponse>>

    @POST("/c/c/post/addstore")
    @FormUrlEncoded
    @Headers(
        "${Header.FORCE_LOGIN}: ${Header.FORCE_LOGIN_TRUE}",
    )
    fun addStoreFlow(
        @Field("data") data: String,
        @Field("stoken") stoken: String = AccountUtil.getSToken()!!,
    ): Flow<CommonResponse>

    @Headers(
        "${Header.FORCE_LOGIN}: ${Header.FORCE_LOGIN_TRUE}",
    )
    @POST("/c/c/agree/opAgree")
    @FormUrlEncoded
    fun agreeFlow(
        @Field("thread_id") threadId: String,
        @Field("post_id") postId: String? = null,
        @Field("op_type") opType: Int = 0,
        @Field("obj_type") objType: Int = 1,
        @Field("agree_type") agreeType: Int = 2,
        @Field("cuid_gid") cuid_gid: String = "",
        @Field("forum_id") forumId: String = "",
        @Field("personalized_rec_switch") personalizedRecSwitch: Int = 1,
        @Field("tbs") tbs: String = AccountUtil.getLoginInfo()!!.tbs,
        @Field("stoken") stoken: String = AccountUtil.getSToken()!!
    ): Flow<AgreeBean>

    @Headers(
        "${Header.FORCE_LOGIN}: ${Header.FORCE_LOGIN_TRUE}",
        "${Header.DROP_HEADERS}: ${Header.CHARSET},${Header.CLIENT_TYPE}",
        "${Header.NO_COMMON_PARAMS}: ${Param.SWAN_GAME_VER},${Param.SDK_VER}",
    )
    @POST("/c/s/uploadPicture")
    fun uploadPicture(
        @Body body: RequestBody,
        @retrofit2.http.Header(Header.COOKIE) cookie: String = "ka=open;BAIDUID=${ClientUtils.baiduId}".takeIf { ClientUtils.baiduId != null }
            ?: "ka=open",
    ): Flow<UploadPictureResultBean>

    @Headers("${Header.FORCE_LOGIN}: ${Header.FORCE_LOGIN_TRUE}")
    @POST("/c/f/ueg/checkjubao")
    @FormUrlEncoded
    fun checkReport(
        @Field("category") category: String,
        @FieldMap reportParam: Map<String, String>,
        @Field("stoken") stoken: String? = AccountUtil.getLoginInfo()
            ?.sToken,
    ): Call<CheckReportBean>

    @Headers("${Header.FORCE_LOGIN}: ${Header.FORCE_LOGIN_TRUE}")
    @POST("/c/f/ueg/checkjubao")
    @FormUrlEncoded
    fun checkReportAsync(
        @Field("category") category: String,
        @FieldMap reportParam: Map<String, String>,
        @Field("stoken") stoken: String? = AccountUtil.getLoginInfo()
            ?.sToken,
    ): Deferred<ApiResult<CheckReportBean>>

    @Headers("${Header.FORCE_LOGIN}: ${Header.FORCE_LOGIN_TRUE}")
    @POST("/c/c/bawu/delthread")
    @FormUrlEncoded
    fun delThreadFlow(
        @Field("fid") forumId: Long,
        @Field("word") forumName: String,
        @Field("z") threadId: Long,
        @Field("tbs") tbs: String? = AccountUtil.getLoginInfo()?.tbs,
        @Field("src") src: Int = 1,
        @Field("is_vipdel") isVipDel: Int = 0,
        @Field("delete_my_thread") deleteMyThread: Int = 1,
        @Field("is_frs_mask") isFrsMask: Int = 0,
    ): Flow<CommonResponse>

    @Headers("${Header.FORCE_LOGIN}: ${Header.FORCE_LOGIN_TRUE}")
    @POST("/c/c/bawu/delpost")
    @FormUrlEncoded
    fun delPostFlow(
        @Field("fid") forumId: Long,
        @Field("word") forumName: String,
        @Field("z") threadId: Long,
        @Field("pid") postId: Long,
        @Field("isfloor") isFloor: Int,
        @Field("src") src: Int,
        @Field("is_vipdel") isVipDel: Int,
        @Field("delete_my_post") deleteMyPost: Int,
        @Field("tbs") tbs: String? = AccountUtil.getLoginInfo()?.tbs,
    ): Flow<CommonResponse>

    @Headers("${Header.FORCE_LOGIN}: ${Header.FORCE_LOGIN_TRUE}")
    @POST("/c/f/forum/like")
    @FormUrlEncoded
    fun userLikeForumFlow(
        @Field("page_no") page: Int = 1,
        @Field("page_size") pageSize: Int = 50,
        @Field("uid") uid: String?,
        @Field("friend_uid") friendUid: String?,
        @Field("is_guest") is_guest: String?,
    ): Flow<UserLikeForumBean>

    @POST("/c/f/pb/floor")
    @FormUrlEncoded
    fun floor(
        @Field("kz") threadId: String,
        @Field("pn") page: Int = 1,
        @Field("pid") postId: String?,
        @Field("spid") subPostId: String?,
        @Field("rn") rn: Int = 20
    ): Call<SubFloorListBean>
}

internal fun base64NoWrap(text: String): String =
    Base64.encodeToString(text.toByteArray(), Base64.NO_WRAP)
