package com.huanchengfly.tieba.post.api.interfaces.impls

import com.huanchengfly.tieba.core.network.agree.AgreeTarget
import android.text.TextUtils
import com.huanchengfly.tieba.core.network.forum.LikeForumResponse
import com.huanchengfly.tieba.core.network.sign.MSignResponse
import com.huanchengfly.tieba.core.network.sign.SignRemoteDataSource
import com.huanchengfly.tieba.core.network.sign.SignResponse
import com.huanchengfly.tieba.post.App
import com.huanchengfly.tieba.post.api.ClientVersion
import com.huanchengfly.tieba.post.api.booleanToString
import com.huanchengfly.tieba.post.api.buildAdParam
import com.huanchengfly.tieba.post.api.buildAppPosInfo
import com.huanchengfly.tieba.post.api.buildCommonRequest
import com.huanchengfly.tieba.post.api.buildProtobufRequestBody
import com.huanchengfly.tieba.post.api.getScreenHeight
import com.huanchengfly.tieba.post.api.getScreenWidth
import com.huanchengfly.tieba.post.api.interfaces.ITiebaApi
import com.huanchengfly.tieba.post.api.legacyAgreeRemoteDataSource
import com.huanchengfly.tieba.post.api.legacyLikeRemoteDataSource
import com.huanchengfly.tieba.post.api.legacyPostRemoteDataSource
import com.huanchengfly.tieba.post.api.legacySignRemoteDataSource
import com.huanchengfly.tieba.post.api.legacyUploadRemoteDataSource
import com.huanchengfly.tieba.post.api.models.AgreeBean
import com.huanchengfly.tieba.post.api.models.CheckReportBean
import com.huanchengfly.tieba.post.api.models.CommonResponse
import com.huanchengfly.tieba.post.api.models.ForumGuideBean
import com.huanchengfly.tieba.post.api.models.GetForumListBean
import com.huanchengfly.tieba.post.api.models.InitNickNameBean
import com.huanchengfly.tieba.post.api.models.LikeForumResultBean
import com.huanchengfly.tieba.post.api.models.LoginBean
import com.huanchengfly.tieba.post.api.models.MSignBean
import com.huanchengfly.tieba.post.api.models.NewCollectDataBean
import com.huanchengfly.tieba.post.api.models.PersonalizedBean
import com.huanchengfly.tieba.post.api.models.PicPageBean
import com.huanchengfly.tieba.post.api.models.SearchForumBean
import com.huanchengfly.tieba.post.api.models.SearchThreadBean
import com.huanchengfly.tieba.post.api.models.SearchUserBean
import com.huanchengfly.tieba.post.api.models.SignResultBean
import com.huanchengfly.tieba.post.api.models.Sync
import com.huanchengfly.tieba.post.api.models.ThreadStoreBean
import com.huanchengfly.tieba.post.api.models.protos.addPost.AddPostRequestData
import com.huanchengfly.tieba.post.api.models.protos.addPost.AddPostResponse
import com.huanchengfly.tieba.post.api.models.protos.frsPage.FrsPageRequest
import com.huanchengfly.tieba.post.api.models.protos.frsPage.FrsPageRequestData
import com.huanchengfly.tieba.post.api.models.protos.frsPage.FrsPageResponse
import com.huanchengfly.tieba.post.api.models.protos.getForumDetail.GetForumDetailRequest
import com.huanchengfly.tieba.post.api.models.protos.getForumDetail.GetForumDetailRequestData
import com.huanchengfly.tieba.post.api.models.protos.getForumDetail.GetForumDetailResponse
import com.huanchengfly.tieba.post.api.models.protos.getUserInfo.GetUserInfoRequest
import com.huanchengfly.tieba.post.api.models.protos.getUserInfo.GetUserInfoRequestData
import com.huanchengfly.tieba.post.api.models.protos.getUserInfo.GetUserInfoResponse
import com.huanchengfly.tieba.post.api.models.protos.hotThreadList.HotThreadListRequest
import com.huanchengfly.tieba.post.api.models.protos.hotThreadList.HotThreadListRequestData
import com.huanchengfly.tieba.post.api.models.protos.hotThreadList.HotThreadListResponse
import com.huanchengfly.tieba.post.api.models.protos.pbFloor.PbFloorRequest
import com.huanchengfly.tieba.post.api.models.protos.pbFloor.PbFloorRequestData
import com.huanchengfly.tieba.post.api.models.protos.pbFloor.PbFloorResponse
import com.huanchengfly.tieba.post.api.models.protos.pbPage.PbPageRequest
import com.huanchengfly.tieba.post.api.models.protos.pbPage.PbPageRequestData
import com.huanchengfly.tieba.post.api.models.protos.pbPage.PbPageResponse
import com.huanchengfly.tieba.post.api.models.protos.personalized.PersonalizedRequest
import com.huanchengfly.tieba.post.api.models.protos.personalized.PersonalizedRequestData
import com.huanchengfly.tieba.post.api.models.protos.personalized.PersonalizedResponse
import com.huanchengfly.tieba.post.api.models.protos.searchSug.SearchSugRequest
import com.huanchengfly.tieba.post.api.models.protos.searchSug.SearchSugRequestData
import com.huanchengfly.tieba.post.api.models.protos.searchSug.SearchSugResponse
import com.huanchengfly.tieba.post.api.models.protos.threadList.AdParam
import com.huanchengfly.tieba.post.api.models.protos.threadList.ThreadListRequest
import com.huanchengfly.tieba.post.api.models.protos.threadList.ThreadListRequestData
import com.huanchengfly.tieba.post.api.models.protos.threadList.ThreadListResponse
import com.huanchengfly.tieba.post.api.models.protos.topicList.TopicListRequest
import com.huanchengfly.tieba.post.api.models.protos.topicList.TopicListRequestData
import com.huanchengfly.tieba.post.api.models.protos.topicList.TopicListResponse
import com.huanchengfly.tieba.post.api.models.protos.userLike.UserLikeRequest
import com.huanchengfly.tieba.post.api.models.protos.userLike.UserLikeRequestData
import com.huanchengfly.tieba.post.api.models.protos.userLike.UserLikeResponse
import com.huanchengfly.tieba.post.api.retrofit.ApiResult
import com.huanchengfly.tieba.post.api.retrofit.RetrofitTiebaApi
import com.huanchengfly.tieba.post.api.urlEncode
import com.huanchengfly.tieba.post.models.DislikeBean
import com.huanchengfly.tieba.post.toJson
import com.huanchengfly.tieba.post.utils.AccountUtil
import com.huanchengfly.tieba.post.utils.CuidUtils
import com.huanchengfly.tieba.post.utils.InstallState
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onEach
import okio.ByteString.Companion.toByteString
import java.io.File

object MixedTiebaApiImpl : ITiebaApi {

    override fun personalizedFlow(loadType: Int, page: Int): Flow<PersonalizedBean> {
        return RetrofitTiebaApi.OFFICIAL_TIEBA_API.personalizedFlow(loadType, page)
    }

    override fun personalizedProtoFlow(loadType: Int, page: Int): Flow<PersonalizedResponse> {
        return RetrofitTiebaApi.OFFICIAL_PROTOBUF_TIEBA_V12_API.personalizedFlow(
            buildProtobufRequestBody(
                data = PersonalizedRequest(
                    PersonalizedRequestData(
                        app_pos = buildAppPosInfo(),
                        common = buildCommonRequest(clientVersion = ClientVersion.TIEBA_V12),
                        load_type = loadType,
                        pn = page,
                        need_tags = 0,
                        page_thread_count = 11,
                        pre_ad_thread_count = 0,
                        sug_count = 0,
                        tag_code = 0,
                        q_type = 1,
                        need_forumlist = 0,
                        new_net_type = 1,
                        new_install = 0,
                        request_times = 0,
                        invoke_source = "",
                        scr_dip = App.ScreenInfo.DENSITY.toDouble(),
                        scr_h = getScreenHeight(),
                        scr_w = getScreenWidth()
                    )
                ),
                clientVersion = ClientVersion.TIEBA_V12
            )
        )
    }

    override fun opAgreeFlow(
        target: AgreeTarget,
        forumId: Long?,
        undo: Boolean,
    ): Flow<AgreeBean> = flow {
        legacyAgreeRemoteDataSource.opAgree(target, forumId, undo)
        emit(AgreeBean())
    }

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

    override fun unlikeForumFlow(
        forumId: String,
        forumName: String,
        tbs: String
    ): Flow<CommonResponse> = flow {
        legacyLikeRemoteDataSource.unfavolike(forumId.toLong(), forumName, tbs)
        emit(CommonResponse())
    }

    override fun likeForumFlow(
        forumId: String,
        forumName: String,
        tbs: String,
        hasHead: Boolean,
    ): Flow<LikeForumResultBean> = flow {
        val response = legacyLikeRemoteDataSource.like(forumId.toLong(), forumName, hasHead, tbs)
        emit(response.toLikeForumResultBean())
    }

    override fun signFlow(forumId: String, forumName: String, tbs: String): Flow<SignResultBean> =
        flow {
            val response = legacySignRemoteDataSource.sign(forumId.toLong(), forumName, tbs)
            emit(response.toSignResultBean())
        }

    override fun delThreadFlow(
        forumId: Long,
        forumName: String,
        threadId: Long,
        tbs: String?,
        delMyThread: Boolean,
        isHide: Boolean,
    ): Flow<CommonResponse> =
        RetrofitTiebaApi.OFFICIAL_TIEBA_API
            .delThreadFlow(
                forumId,
                forumName,
                threadId,
                tbs,
                deleteMyThread = if (delMyThread) 1 else 0,
                isFrsMask = if (isHide) 1 else 0
            )

    override fun delPostFlow(
        forumId: Long,
        forumName: String,
        threadId: Long,
        postId: Long,
        tbs: String?,
        isFloor: Boolean,
        delMyPost: Boolean
    ): Flow<CommonResponse> =
        RetrofitTiebaApi.OFFICIAL_TIEBA_API
            .delPostFlow(
                forumId,
                forumName,
                threadId,
                postId,
                isFloor = if (isFloor) 1 else 0,
                src = if (isFloor) 3 else 1,
                isVipDel = if (delMyPost) 0 else 1,
                deleteMyPost = if (delMyPost) 1 else 0,
                tbs = tbs
            )

    override fun searchUserFlow(keyword: String): Flow<SearchUserBean> =
        RetrofitTiebaApi.HYBRID_TIEBA_API.searchUserFlow(keyword)

    override fun threadStoreFlow(page: Int, pageSize: Int): Flow<ThreadStoreBean> =
        RetrofitTiebaApi.OFFICIAL_TIEBA_API.threadStoreFlow(
            pageSize,
            pageSize * page
        )

    override fun removeStoreFlow(
        threadId: Long,
        forumId: Long,
        tbs: String?
    ): Flow<CommonResponse> =
        RetrofitTiebaApi.OFFICIAL_TIEBA_API.removeStoreFlow(
            threadId.toString(),
            forumId.toString(),
            tbs ?: AccountUtil.getLoginInfo()!!.tbs
        )

    override fun removeStoreFlow(threadId: String): Flow<CommonResponse> =
        RetrofitTiebaApi.OFFICIAL_TIEBA_API.removeStoreFlow(threadId)

    override fun addStoreFlow(threadId: Long, postId: Long): Flow<CommonResponse> =
        RetrofitTiebaApi.OFFICIAL_TIEBA_API.addStoreFlow(
            listOf(
                NewCollectDataBean(
                    threadId.toString(),
                    postId.toString(),
                    status = 1
                )
            ).toJson()
        )

    override fun submitDislikeFlow(dislikeBean: DislikeBean): Flow<CommonResponse> =
        RetrofitTiebaApi.OFFICIAL_TIEBA_API.submitDislikeFlow(listOf(dislikeBean).toJson())

    override fun searchForumFlow(keyword: String): Flow<SearchForumBean> =
        RetrofitTiebaApi.HYBRID_TIEBA_API.searchForumFlow(keyword)

    override fun searchThreadFlow(
        keyword: String, page: Int, sort: Int,
    ): Flow<SearchThreadBean> =
        RetrofitTiebaApi.HYBRID_TIEBA_API.searchThreadFlow(
            keyword,
            page,
            sort
        )

    override fun searchPostFlow(
        keyword: String,
        forumName: String,
        forumId: Long,
        sortType: Int,
        filterType: Int,
        page: Int,
        pageSize: Int,
    ): Flow<SearchThreadBean> =
        RetrofitTiebaApi.HYBRID_TIEBA_API.searchThreadFlow(
            keyword,
            page,
            sortType,
            filterType,
            pageSize,
            forumName,
            ct = 2,
            isUseZonghe = null,
            clientVersion = ClientVersion.TIEBA_V12.version,
            referer = "https://tieba.baidu.com/mo/q/hybrid-usergrow-search/searchGlobal?entryPage=frs&loadingSignal=1&forumName=${forumName.urlEncode()}&forumId=$forumId&customfullscreen=1&nonavigationbar=1&cuid=${CuidUtils.getNewCuid()}&cuid_galaxy2=${CuidUtils.getNewCuid()}&cuid_gid=&timestamp=${System.currentTimeMillis()}&_client_version=${ClientVersion.TIEBA_V12.version}&_client_type=2"
        )

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

    override fun userLikeFlow(
        pageTag: String,
        lastRequestUnix: Long,
        loadType: Int
    ): Flow<UserLikeResponse> {
        return RetrofitTiebaApi.OFFICIAL_PROTOBUF_TIEBA_V12_API.userLikeFlow(
            buildProtobufRequestBody(
                UserLikeRequest(
                    UserLikeRequestData(
                        common = buildCommonRequest(clientVersion = ClientVersion.TIEBA_V12),
                        pageTag = pageTag,
                        lastRequestUnix = lastRequestUnix,
                        followType = 1,
                        loadType = loadType
                    )
                ),
                clientVersion = ClientVersion.TIEBA_V12,
            )
        )
    }

    override fun hotThreadListFlow(tabCode: String): Flow<HotThreadListResponse> {
        return RetrofitTiebaApi.OFFICIAL_PROTOBUF_TIEBA_V12_API.hotThreadListFlow(
            buildProtobufRequestBody(
                HotThreadListRequest(
                    HotThreadListRequestData(
                        common = buildCommonRequest(clientVersion = ClientVersion.TIEBA_V12),
                        tabCode = tabCode,
                        tabId = "1"
                    )
                ),
                clientVersion = ClientVersion.TIEBA_V12,
            )
        )
    }

    override fun topicListFlow(): Flow<TopicListResponse> {
        return RetrofitTiebaApi.OFFICIAL_PROTOBUF_TIEBA_V12_API.topicListFlow(
            buildProtobufRequestBody(
                TopicListRequest(
                    TopicListRequestData(
                        common = buildCommonRequest(clientVersion = ClientVersion.TIEBA_V12),
                        call_from = "newbang",
                        list_type = "all",
                        need_tab_list = "0",
                        fid = 0L
                    )
                ),
                clientVersion = ClientVersion.TIEBA_V12,
            )
        )
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

    override fun pbFloorFlow(
        threadId: Long,
        postId: Long,
        forumId: Long,
        page: Int,
        subPostId: Long
    ): Flow<PbFloorResponse> {
        return RetrofitTiebaApi.OFFICIAL_PROTOBUF_TIEBA_V22_API.pbFloorFlow(
            buildProtobufRequestBody(
                PbFloorRequest(
                    PbFloorRequestData(
                        common = buildCommonRequest(clientVersion = ClientVersion.TIEBA_V22),
                        forum_id = forumId,
                        kz = threadId,
                        pid = postId,
                        pn = page,
                        spid = subPostId,
                        scr_dip = App.ScreenInfo.DENSITY.toDouble(),
                        scr_h = getScreenHeight(),
                        scr_w = getScreenWidth(),
                        is_comm_reverse = 0,
                        ori_ugc_type = 0
                    )
                ),
                clientVersion = ClientVersion.TIEBA_V22,
                needSToken = false
            )
        )
    }

    override fun searchSuggestionsFlow(keyword: String, isForum: Boolean): Flow<SearchSugResponse> {
        return RetrofitTiebaApi.OFFICIAL_PROTOBUF_TIEBA_V12_API.searchSugFlow(
            buildProtobufRequestBody(
                SearchSugRequest(
                    SearchSugRequestData(
                        common = buildCommonRequest(clientVersion = ClientVersion.TIEBA_V12),
                        word = keyword,
                        isforum = isForum.booleanToString()
                    )
                ),
                clientVersion = ClientVersion.TIEBA_V12,
                needSToken = true
            )
        )
    }

    override fun getForumDetailFlow(forumId: Long): Flow<GetForumDetailResponse> {
        return RetrofitTiebaApi.OFFICIAL_PROTOBUF_TIEBA_V12_API.getForumDetailFlow(
            buildProtobufRequestBody(
                GetForumDetailRequest(
                    GetForumDetailRequestData(
                        common = buildCommonRequest(clientVersion = ClientVersion.TIEBA_V12),
                        forum_id = forumId,
                    )
                ),
                clientVersion = ClientVersion.TIEBA_V12,
                needSToken = true
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
