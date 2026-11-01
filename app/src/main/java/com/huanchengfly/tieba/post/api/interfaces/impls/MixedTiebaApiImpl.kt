package com.huanchengfly.tieba.post.api.interfaces.impls

import com.huanchengfly.tieba.core.network.forum.LikeForumResponse
import com.huanchengfly.tieba.core.network.sign.MSignResponse
import com.huanchengfly.tieba.core.network.sign.SignRemoteDataSource
import com.huanchengfly.tieba.core.network.sign.SignResponse
import com.huanchengfly.tieba.post.App
import com.huanchengfly.tieba.post.api.ClientVersion
import com.huanchengfly.tieba.post.api.buildAdParam
import com.huanchengfly.tieba.post.api.buildAppPosInfo
import com.huanchengfly.tieba.post.api.buildCommonRequest
import com.huanchengfly.tieba.post.api.buildProtobufRequestBody
import com.huanchengfly.tieba.post.api.getScreenHeight
import com.huanchengfly.tieba.post.api.getScreenWidth
import com.huanchengfly.tieba.post.api.interfaces.ITiebaApi
import com.huanchengfly.tieba.post.api.legacyLikeRemoteDataSource
import com.huanchengfly.tieba.post.api.legacyPostRemoteDataSource
import com.huanchengfly.tieba.post.api.legacySignRemoteDataSource
import com.huanchengfly.tieba.post.api.models.CheckReportBean
import com.huanchengfly.tieba.post.api.models.ForumGuideBean
import com.huanchengfly.tieba.post.api.models.GetForumListBean
import com.huanchengfly.tieba.post.api.models.InitNickNameBean
import com.huanchengfly.tieba.post.api.models.LikeForumResultBean
import com.huanchengfly.tieba.post.api.models.LoginBean
import com.huanchengfly.tieba.post.api.models.MSignBean
import com.huanchengfly.tieba.post.api.models.PicPageBean
import com.huanchengfly.tieba.post.api.models.SignResultBean
import com.huanchengfly.tieba.post.api.models.Sync
import com.huanchengfly.tieba.post.api.models.protos.addPost.AddPostRequestData
import com.huanchengfly.tieba.post.api.models.protos.addPost.AddPostResponse
import com.huanchengfly.tieba.post.api.models.protos.frsPage.FrsPageRequest
import com.huanchengfly.tieba.post.api.models.protos.frsPage.FrsPageRequestData
import com.huanchengfly.tieba.post.api.models.protos.frsPage.FrsPageResponse
import com.huanchengfly.tieba.post.api.models.protos.getUserInfo.GetUserInfoRequest
import com.huanchengfly.tieba.post.api.models.protos.getUserInfo.GetUserInfoRequestData
import com.huanchengfly.tieba.post.api.models.protos.getUserInfo.GetUserInfoResponse
import com.huanchengfly.tieba.post.api.models.protos.pbPage.PbPageRequest
import com.huanchengfly.tieba.post.api.models.protos.pbPage.PbPageRequestData
import com.huanchengfly.tieba.post.api.models.protos.pbPage.PbPageResponse
import com.huanchengfly.tieba.post.api.models.protos.threadList.AdParam
import com.huanchengfly.tieba.post.api.models.protos.threadList.ThreadListRequest
import com.huanchengfly.tieba.post.api.models.protos.threadList.ThreadListRequestData
import com.huanchengfly.tieba.post.api.models.protos.threadList.ThreadListResponse
import com.huanchengfly.tieba.post.api.retrofit.ApiResult
import com.huanchengfly.tieba.post.api.retrofit.RetrofitTiebaApi
import com.huanchengfly.tieba.post.api.urlEncode
import com.huanchengfly.tieba.post.utils.AccountUtil
import com.huanchengfly.tieba.post.utils.InstallState
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onEach

object MixedTiebaApiImpl : ITiebaApi {




    override fun picPageFlow(
        forumId: String,
        forumName: String,
        threadId: String,
        seeLz: Boolean,
        picId: String,
        picIndex: String,
        objType: String,
        prev: Boolean,
        postId: Long?,
    ): Flow<PicPageBean> = RetrofitTiebaApi.OFFICIAL_TIEBA_API.picPageFlow(
        forumId,
        forumName,
        threadId,
        picId,
        picIndex,
        objType.ifEmpty { "other" },
        prev = if (prev) 10 else 0,
        next = if (prev) 0 else 10,
        notSeeLz = if (seeLz) 0 else 1,
        postId = postId?.takeIf { it != 0L }?.toString(),
    )



    override fun signFlow(forumId: String, forumName: String, tbs: String): Flow<SignResultBean> =
        flow {
            val response = legacySignRemoteDataSource.sign(forumId.toLong(), forumName, tbs)
            emit(response.toSignResultBean())
        }












    override fun checkReportPostAsync(postId: String): Deferred<ApiResult<CheckReportBean>> =
        RetrofitTiebaApi.OFFICIAL_TIEBA_API.checkReportAsync(
            category = "1",
            reportParam = mapOf(
                "pid" to postId
            )
        )

    override fun initNickNameFlow(): Flow<InitNickNameBean> =
        RetrofitTiebaApi.OFFICIAL_TIEBA_API.initNickNameFlow()

    override fun initNickNameFlow(bduss: String, sToken: String): Flow<InitNickNameBean> =
        RetrofitTiebaApi.OFFICIAL_TIEBA_API.initNickNameFlow(bduss, sToken)

    override fun loginFlow(): Flow<LoginBean> =
        RetrofitTiebaApi.OFFICIAL_TIEBA_API.loginFlow()

    override fun loginFlow(bduss: String, sToken: String): Flow<LoginBean> =
        RetrofitTiebaApi.OFFICIAL_TIEBA_API.loginFlow("$bduss|", sToken)
            .onEach { InstallState.onLoginSucceeded() }

    override fun getForumListFlow(): Flow<GetForumListBean> =
        RetrofitTiebaApi.OFFICIAL_TIEBA_API.getForumListFlow()

    override fun mSign(
        forumIds: String,
        tbs: String
    ): Flow<MSignBean> = flow {
        // At most 50 forums per request
        val results = forumIds.split(",").map { it.toLong() }
            .chunked(SignRemoteDataSource.MAX_MSIGN_FORUMS)
            .map { legacySignRemoteDataSource.mSign(it, tbs) }
        emit(results.toMSignBean())
    }




    override fun allForumGuideFlow(): Flow<List<ForumGuideBean.LikeForum>> = flow {
        val forums = mutableListOf<ForumGuideBean.LikeForum>()
        var pageNo = 1
        do {
            val page = legacyLikeRemoteDataSource.forumGuide(pageNo)
            val pageForums = page.likeForum.orEmpty().map {
                ForumGuideBean.LikeForum(
                    forumId = it.forumId.toLong(),
                    forumName = it.forumName,
                    levelId = it.levelId?.toIntOrNull() ?: 0,
                    isSign = it.isSign?.toIntOrNull() ?: 0,
                    avatar = it.avatar,
                )
            }
            forums += pageForums
            pageNo++
        } while (page.hasMore && pageForums.isNotEmpty())
        emit(forums)
    }

    override fun frsPage(
        forumName: String,
        page: Int,
        loadType: Int,
        sortType: Int,
        goodClassifyId: Int?
    ): Flow<FrsPageResponse> {
        return RetrofitTiebaApi.OFFICIAL_PROTOBUF_TIEBA_V12_API.frsPageFlow(
            buildProtobufRequestBody(
                FrsPageRequest(
                    FrsPageRequestData(
                        ad_param = buildAdParam(),
                        app_pos = buildAppPosInfo(),
                        call_from = 0,
                        category_id = 0,
                        cid = goodClassifyId ?: 0,
                        common = buildCommonRequest(clientVersion = ClientVersion.TIEBA_V12),
                        ctime = 0,
                        data_size = 0,
                        hot_thread_id = 0,
                        is_default_navtab = 0,
                        is_good = if (goodClassifyId != null) 1 else 0,
                        is_selection = 0,
                        kw = forumName.urlEncode(),
                        last_click_tid = 0,
                        load_type = loadType,
                        net_error = 0,
                        pn = page,
                        q_type = 2,
                        rn = 90,
                        rn_need = 30,
                        scr_dip = App.ScreenInfo.DENSITY.toDouble(),
                        scr_h = getScreenHeight(),
                        scr_w = getScreenWidth(),
                        sort_type = sortType,
                        st_param = 0,
                        st_type = "recom_flist",
                        up_schema = "",
                        with_group = 1,
                        yuelaou_locate = ""
                    )
                ),
                clientVersion = ClientVersion.TIEBA_V12
            ),
            forumName = forumName.urlEncode()
        )
    }

    override fun threadList(
        forumId: Long,
        forumName: String,
        page: Int,
        sortType: Int,
        threadIds: String
    ): Flow<ThreadListResponse> {
        return RetrofitTiebaApi.OFFICIAL_PROTOBUF_TIEBA_V12_API.threadListFlow(
            buildProtobufRequestBody(
                ThreadListRequest(
                    ThreadListRequestData(
                        ad_param = AdParam(3, 0, null),
                        app_pos = buildAppPosInfo(),
                        common = buildCommonRequest(clientVersion = ClientVersion.TIEBA_V12),
                        scr_dip = App.ScreenInfo.DENSITY.toDouble(),
                        scr_h = getScreenHeight(),
                        scr_w = getScreenWidth(),
                        forum_id = forumId,
                        forum_name = forumName,
                        pn = page,
                        q_type = 2,
                        user_id = AccountUtil.getUid()?.toLongOrNull(),
                        thread_ids = threadIds,
                        sort_type = sortType,
                        need_abstract = 0,
                        st_type = 0,
                        last_click_tid = 0
                    )
                ),
                clientVersion = ClientVersion.TIEBA_V12
            )
        )
    }

    override fun syncFlow(clientId: String?): Flow<Sync> {
        val pushSwitch = InstallState.pushSwitch()
        return RetrofitTiebaApi.OFFICIAL_TIEBA_API.sync(
            clientId = clientId,
            pushSystemSwitch = pushSwitch.current,
            lastPushSystemSwitch = pushSwitch.last,
            lastPushSystemSwitchTime = pushSwitch.lastChangeTime,
            runningAbi = InstallState.runningAbi,
            supportAbi = InstallState.supportAbi,
            installAppTime = App.Config.appFirstInstallTime,
        )
    }

    override fun addPostFlow(
        content: String,
        forumId: String,
        forumName: String,
        threadId: String,
        tbs: String?,
        nameShow: String?,
        postId: String?,
        subPostId: String?,
        replyUserId: String?
    ): Flow<AddPostResponse> {
        // Business fields as TiebaLite has always sent them; the data source adds the common
        // request and the message task form fields
        val data = AddPostRequestData(
            anonymous = "1",
            barrage_time = "0".takeIf { postId.isNullOrEmpty() },
            can_no_forum = "0",
            content = content,
            entrance_type = "0",
            fid = forumId,
            floor_num = "0",
            kw = forumName,
            is_ad = "0",
            is_addition = "0",
            is_barrage = "0",
            is_feedback = "0",
            is_giftpost = "0",
            is_pictxt = "0",
            is_show_bless = 0,
            is_twzhibo_thread = "0",
            name_show = nameShow ?: AccountUtil.getAccountInfo { this.nameShow }
                .orEmpty(),
            new_vcode = "1",
            post_from = if (postId.isNullOrEmpty() && subPostId.isNullOrEmpty()) "13" else if (subPostId.isNullOrEmpty()) "0" else null,
            quote_id = postId,
            reply_uid = replyUserId.takeIf { !postId.isNullOrEmpty() },
            repostid = postId,
            sub_post_id = subPostId,
            show_custom_figure = 0,
            takephoto_num = "0",
            tid = threadId,
            v_fid = "".takeIf { postId.isNullOrEmpty() },
            v_fname = "".takeIf { postId.isNullOrEmpty() },
            vcode_tag = "12",
        )
        return flow { emit(AddPostResponse(data_ = legacyPostRemoteDataSource.addPost(data, tbs))) }
    }

    override fun pbPageFlow(
        threadId: Long,
        page: Int,
        postId: Long,
        seeLz: Boolean,
        back: Boolean,
        sortType: Int,
        forumId: Long?,
        stType: String,
        mark: Int,
        lastPostId: Long?,
    ): Flow<PbPageResponse> {
        return RetrofitTiebaApi.OFFICIAL_PROTOBUF_TIEBA_V12_API.pbPageFlow(
            buildProtobufRequestBody(
                PbPageRequest(
                    PbPageRequestData(
                        common = buildCommonRequest(clientVersion = ClientVersion.TIEBA_V12),
                        kz = threadId,
                        pid = postId,
                        pn = page,
                        r = sortType,
                        lz = if (seeLz) 1 else 0,
                        forum_id = forumId ?: 0,
                        ad_param = com.huanchengfly.tieba.post.api.models.protos.pbPage.AdParam(
                            load_count = 0,
                            refresh_count = 1,
                            is_req_ad = 1
                        ),
                        mark = mark,
                        last_pid = lastPostId ?: 0,
                        app_pos = buildAppPosInfo(),
                        back = if (back) 1 else 0,
                        banner = 0,
                        broadcast_id = 0,
                        floor_rn = 4,
                        floor_sort_type = 1,
                        from_push = 0,
                        from_smart_frs = 0,
                        immersion_video_comment_source = 0,
                        is_comm_reverse = 0,
                        is_fold_comment_req = 0,
                        is_jumpfloor = 0,
                        jumpfloor_num = 0,
                        need_repost_recommend_forum = 0,
                        obj_locate = "",
                        obj_param1 = "10",
                        obj_source = "",
                        ori_ugc_type = 0,
                        pb_rn = 0,
                        q_type = 2,
                        request_times = 0,
                        rn = 15,
                        s_model = 0,
                        scr_dip = App.ScreenInfo.DENSITY.toDouble(),
                        scr_h = getScreenHeight(),
                        scr_w = getScreenWidth(),
                        similar_from = 0,
                        source_type = 2,
                        st_type = stType,
                        thread_type = 0,
                        weipost = 0,
                        with_floor = 1
                    )
                ),
                clientVersion = ClientVersion.TIEBA_V12
            )
        )
    }




    override fun getUserInfoFlow(): Flow<GetUserInfoResponse> {
        return getUserInfoFlow(AccountUtil.getUid()!!.toLong(), null, null)
    }

    override fun getUserInfoFlow(
        uid: Long,
        bduss: String?,
        sToken: String?,
    ): Flow<GetUserInfoResponse> {
        return RetrofitTiebaApi.OFFICIAL_PROTOBUF_TIEBA_V12_API.getUserInfoFlow(
            buildProtobufRequestBody(
                GetUserInfoRequest(
                    GetUserInfoRequestData(
                        common = buildCommonRequest(
                            clientVersion = ClientVersion.TIEBA_V12,
                            bduss = bduss,
                            stoken = sToken
                        ),
                        uid = uid,
                        scr_w = getScreenWidth()
                    )
                ),
                clientVersion = ClientVersion.TIEBA_V12,
                needSToken = true
            )
        )
    }
}

private fun SignResponse.toSignResultBean() = SignResultBean(
    userInfo = userInfo?.let { info ->
        SignResultBean.UserInfo(
            userId = info.userId,
            isSignIn = info.isSignIn,
            contSignNum = info.contSignNum,
            userSignRank = info.userSignRank,
            signTime = info.signTime,
            signBonusPoint = info.signBonusPoint,
            levelName = info.levelName,
            levelUpScore = info.levelUpScore,
            allLevelInfo = info.allLevelInfo.map {
                SignResultBean.UserInfo.AllLevelInfo(it.id, it.name, it.score)
            },
        )
    },
    errorCode = "0",
    time = time,
)

private fun List<MSignResponse>.toMSignBean(): MSignBean {
    val last = lastOrNull()
    return MSignBean(
        ctime = 0,
        error = MSignBean.Error(errmsg = "", errno = "0", usermsg = ""),
        errorCode = "0",
        info = flatMap { it.info }.map { info ->
            MSignBean.Info(
                curScore = info.curScore.orEmpty(),
                error = MSignBean.Info.Error(
                    errNo = info.error?.errNo.orEmpty(),
                    errmsg = info.error?.errmsg.orEmpty(),
                    usermsg = info.error?.usermsg.orEmpty(),
                ),
                forumId = info.forumId.orEmpty(),
                forumName = info.forumName.orEmpty(),
                isFilter = info.isFilter.orEmpty(),
                isOn = info.isOn.orEmpty(),
                signDayCount = info.signDayCount.orEmpty(),
                signed = info.signed.orEmpty(),
            )
        },
        isTimeout = last?.isTimeout.orEmpty(),
        logid = 0,
        serverTime = "",
        showDialog = last?.showDialog.orEmpty(),
        signNotice = last?.signNotice.orEmpty(),
        time = 0,
        timeoutNotice = last?.timeoutNotice.orEmpty(),
    )
}

private fun LikeForumResponse.toLikeForumResultBean() = LikeForumResultBean(
    errorCode = "0",
    error = LikeForumResultBean.ErrorInfo(errno = "0", errmsg = "", usermsg = ""),
    info = LikeForumResultBean.Info(
        curScore = info?.curScore ?: "0",
        levelUpScore = info?.levelUpScore ?: "0",
        levelId = info?.levelId ?: "0",
        levelName = info?.levelName.orEmpty(),
        memberSum = info?.memberSum ?: "0",
    ),
    userPerm = LikeForumResultBean.UserPermInfo(
        levelId = userPerm?.levelId ?: "0",
        levelName = userPerm?.levelName.orEmpty(),
    ),
)
