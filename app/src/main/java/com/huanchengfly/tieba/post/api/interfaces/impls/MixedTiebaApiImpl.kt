package com.huanchengfly.tieba.post.api.interfaces.impls

import com.huanchengfly.tieba.core.network.forum.LikeForumResponse
import com.huanchengfly.tieba.core.network.sign.MSignResponse
import com.huanchengfly.tieba.core.network.sign.SignRemoteDataSource
import com.huanchengfly.tieba.core.network.sign.SignResponse
import com.huanchengfly.tieba.post.App
import com.huanchengfly.tieba.post.api.ClientVersion
import com.huanchengfly.tieba.post.api.buildCommonRequest
import com.huanchengfly.tieba.post.api.buildProtobufRequestBody
import com.huanchengfly.tieba.post.api.getScreenWidth
import com.huanchengfly.tieba.post.api.interfaces.ITiebaApi
import com.huanchengfly.tieba.post.api.legacyLikeRemoteDataSource
import com.huanchengfly.tieba.post.api.legacySignRemoteDataSource
import com.huanchengfly.tieba.post.api.models.CheckReportBean
import com.huanchengfly.tieba.post.api.models.ForumGuideBean
import com.huanchengfly.tieba.post.api.models.GetForumListBean
import com.huanchengfly.tieba.post.api.models.InitNickNameBean
import com.huanchengfly.tieba.post.api.models.LikeForumResultBean
import com.huanchengfly.tieba.post.api.models.LoginBean
import com.huanchengfly.tieba.post.api.models.MSignBean
import com.huanchengfly.tieba.post.api.models.SignResultBean
import com.huanchengfly.tieba.post.api.models.Sync
import com.huanchengfly.tieba.post.api.models.protos.getUserInfo.GetUserInfoRequest
import com.huanchengfly.tieba.post.api.models.protos.getUserInfo.GetUserInfoRequestData
import com.huanchengfly.tieba.post.api.models.protos.getUserInfo.GetUserInfoResponse
import com.huanchengfly.tieba.post.api.retrofit.ApiResult
import com.huanchengfly.tieba.post.api.retrofit.RetrofitTiebaApi
import com.huanchengfly.tieba.post.utils.AccountUtil
import com.huanchengfly.tieba.post.utils.InstallState
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onEach

object MixedTiebaApiImpl : ITiebaApi {

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
