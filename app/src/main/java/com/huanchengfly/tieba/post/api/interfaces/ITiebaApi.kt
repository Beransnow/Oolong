package com.huanchengfly.tieba.post.api.interfaces

import com.huanchengfly.tieba.post.api.models.*
import com.huanchengfly.tieba.post.api.models.protos.addPost.AddPostResponse
import com.huanchengfly.tieba.post.api.models.protos.frsPage.FrsPageResponse
import com.huanchengfly.tieba.post.api.models.protos.getForumDetail.GetForumDetailResponse
import com.huanchengfly.tieba.post.api.models.protos.getUserInfo.GetUserInfoResponse
import com.huanchengfly.tieba.post.api.models.protos.pbPage.PbPageResponse
import com.huanchengfly.tieba.post.api.models.protos.threadList.ThreadListResponse
import com.huanchengfly.tieba.post.api.retrofit.ApiResult
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.flow.Flow

interface ITiebaApi {




    /**
     * 查看图片
     *
     * @param forumId 吧 ID
     * @param forumName 吧名
     * @param threadId 贴 ID
     * @param seeLz 是否只看楼主
     * @param picId 图片 ID
     * @param picIndex 图片索引
     * @param objType 原页面类型（pb - 贴页面 frs - 吧页面 index - 首页推荐）
     * @param prev 是否向前加载
     */
    fun picPageFlow(
        forumId: String,
        forumName: String,
        threadId: String,
        seeLz: Boolean,
        picId: String,
        picIndex: String,
        objType: String,
        prev: Boolean,
        postId: Long? = null,
    ): Flow<PicPageBean>



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

    /**
     * 吧页面
     *
     * @param forumName 吧名
     * @param page 页码（从 1 开始）
     * @param loadType 加载类型（1 - 下拉刷新 2 - 加载更多）
     * @param sortType 排序
     * @param goodClassifyId 精品贴分类
     */
    fun frsPage(
        forumName: String,
        page: Int,
        loadType: Int,
        sortType: Int,
        goodClassifyId: Int? = null
    ): Flow<FrsPageResponse>

    /**
     * 吧页面 - 贴子列表
     *
     * @param forumId 吧 ID
     * @param forumName 吧名
     * @param page 页码（从 1 开始）
     * @param sortType 排序
     */
    fun threadList(
        forumId: Long,
        forumName: String,
        page: Int,
        sortType: Int,
        threadIds: String = "",
    ): Flow<ThreadListResponse>

    fun syncFlow(clientId: String? = null): Flow<Sync>

    /**
     * 回贴（App 接口）
     *
     * **需登录**
     *
     * @param forumId 吧 ID
     * @param forumName 吧名
     * @param threadId 贴子 ID
     * @param tbs tbs
     * @param content 回复内容
     * @param postId 回复楼 ID，为空则回复贴子
     * @param replyUserId 楼中楼回复用户 ID
     */
    fun addPostFlow(
        content: String,
        forumId: String,
        forumName: String,
        threadId: String,
        tbs: String? = null,
        nameShow: String? = null,
        postId: String? = null,
        subPostId: String? = null,
        replyUserId: String? = null
    ): Flow<AddPostResponse>

    /**
     * 贴子页（Flow）
     *
     * @param threadId 贴子 ID
     * @param page 页码（从 1 开始）
     * @param postId 回复 ID
     * @param seeLz 是否只看楼主
     * @param back 是否向前翻页
     * @param sortType 排序类型
     * @param forumId 吧 ID
     * @param stType 来源类型（？）
     * @param mark 是否收藏
     * @param lastPostId 最后一条回复 ID
     */
    fun pbPageFlow(
        threadId: Long,
        page: Int = 0,
        postId: Long = 0L,
        seeLz: Boolean = false,
        back: Boolean = false,
        sortType: Int = 0,
        forumId: Long? = null,
        stType: String = "",
        mark: Int = 0,
        lastPostId: Long? = null,
    ): Flow<PbPageResponse>



    /**
     * 获取吧信息
     *
     * @param forumId 吧 ID
     */
    fun getForumDetailFlow(
        forumId: Long,
    ): Flow<GetForumDetailResponse>

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