package com.huanchengfly.tieba.post.api.interfaces

import com.huanchengfly.tieba.post.api.models.*
import com.huanchengfly.tieba.post.api.models.protos.getUserInfo.GetUserInfoResponse
import com.huanchengfly.tieba.post.api.retrofit.ApiResult
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.flow.Flow

interface ITiebaApi {

    /**
     * 吧签到
     *
     * **需登录**
     *
     * @param forumId 吧 ID
     * @param forumName 吧名
     * @param tbs tbs
     */
    fun signFlow(
        forumId: String,
        forumName: String,
        tbs: String
    ): Flow<SignResultBean>

    /**
     * 获取举报贴子/回贴页面 URL
     *
     * @param postId PID
     */
    fun checkReportPostAsync(
        postId: String,
    ): Deferred<ApiResult<CheckReportBean>>

    /**
     * 获得当前用户昵称（需登录）
     */
    fun initNickNameFlow(): Flow<InitNickNameBean>

    /**
     * 获得用户昵称
     */
    fun initNickNameFlow(
        bduss: String,
        sToken: String,
    ): Flow<InitNickNameBean>

    /**
     * 更新登录信息（需登录）
     */
    fun loginFlow(): Flow<LoginBean>

    /**
     * 登录
     */
    fun loginFlow(
        bduss: String,
        sToken: String
    ): Flow<LoginBean>

    /**
     * 获取吧列表
     */
    fun getForumListFlow(): Flow<GetForumListBean>

    /**
     * 一键签到（官方）
     */
    fun mSign(
        forumIds: String,
        tbs: String
    ): Flow<MSignBean>

    /**
     * 全部关注的吧
     *
     * 分页拉取直到没有更多，不受 [forumRecommendFlow] / [forumRecommendNewFlow] 最多 200 个的限制
     *
     * **需登录**
     */
    fun allForumGuideFlow(): Flow<List<ForumGuideBean.LikeForum>>

    fun syncFlow(clientId: String? = null): Flow<Sync>

    /**
     * 获得当前用户信息（需登录）
     */
    fun getUserInfoFlow(): Flow<GetUserInfoResponse>

    /**
     * 获得用户信息
     */
    fun getUserInfoFlow(
        uid: Long,
        bduss: String?,
        sToken: String?,
    ): Flow<GetUserInfoResponse>

}