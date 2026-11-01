package com.huanchengfly.tieba.post.api.interfaces

import com.huanchengfly.tieba.core.network.agree.AgreeTarget
import com.huanchengfly.tieba.post.api.models.*
import com.huanchengfly.tieba.post.api.models.protos.addPost.AddPostResponse
import com.huanchengfly.tieba.post.api.models.protos.frsPage.FrsPageResponse
import com.huanchengfly.tieba.post.api.models.protos.getForumDetail.GetForumDetailResponse
import com.huanchengfly.tieba.post.api.models.protos.getUserInfo.GetUserInfoResponse
import com.huanchengfly.tieba.post.api.models.protos.pbFloor.PbFloorResponse
import com.huanchengfly.tieba.post.api.models.protos.pbPage.PbPageResponse
import com.huanchengfly.tieba.post.api.models.protos.threadList.ThreadListResponse
import com.huanchengfly.tieba.post.api.models.protos.topicList.TopicListResponse
import com.huanchengfly.tieba.post.api.retrofit.ApiResult
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.flow.Flow

interface ITiebaApi {



    /**
     * 给贴子/回复点赞/取消点赞
     *
     * **需登录**
     *
     * @param target 主题贴、楼层或楼中楼
     * @param forumId 所在吧 ID
     * @param undo 是否取消点赞
     */
    fun opAgreeFlow(
        target: AgreeTarget,
        forumId: Long?,
        undo: Boolean,
    ): Flow<AgreeBean>

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
     * 取关一个吧
     *
     * **需登录**
     *
     * @param forumId 吧 ID
     * @param forumName 吧名
     * @param tbs tbs（长）
     */
    fun unlikeForumFlow(
        forumId: String,
        forumName: String,
        tbs: String
    ): Flow<CommonResponse>

    /**
     * 关注一个吧
     *
     * **需登录**
     *
     * @param forumId 吧 ID
     * @param forumName 吧名
     * @param tbs tbs（长）
     * @param hasHead 吧页是否有明星或活动头图
     */
    fun likeForumFlow(
        forumId: String,
        forumName: String,
        tbs: String,
        hasHead: Boolean,
    ): Flow<LikeForumResultBean>

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
     * 删除/隐藏贴子
     *
     * **需登录**
     *
     * @param forumId 吧 ID
     * @param forumName 吧名
     * @param threadId 贴子 ID
     * @param tbs tbs
     * @param delMyThread 是否为自己的贴子
     * @param isHide 是否为隐藏贴子
     */
    fun delThreadFlow(
        forumId: Long,
        forumName: String,
        threadId: Long,
        tbs: String?,
        delMyThread: Boolean,
        isHide: Boolean
    ): Flow<CommonResponse>

    /**
     * 删除贴子中的回复
     *
     * **需登录**
     *
     * @param forumId 吧 ID
     * @param forumName 吧名
     * @param threadId 贴子 ID
     * @param postId 回复 ID
     * @param isFloor 是否为楼中楼回复
     * @param delMyPost 是否为当前登录用户回复
     */
    fun delPostFlow(
        forumId: Long,
        forumName: String,
        threadId: Long,
        postId: Long,
        tbs: String?,
        isFloor: Boolean = false,
        delMyPost: Boolean = true
    ): Flow<CommonResponse>


    /**
     * 查看收藏贴列表
     *
     * **需登录**
     *
     * @param page 分页页码（从 0 开始）
     * @param pageSize 每页贴数（默认 20）
     */
    fun threadStoreFlow(
        page: Int = 0,
        pageSize: Int = 20
    ): Flow<ThreadStoreBean>

    /**
     * 移除收藏
     *
     * **需登录**
     *
     * @param threadId 贴子 ID
     * @param tbs tbs
     */
    fun removeStoreFlow(
        threadId: Long,
        forumId: Long,
        tbs: String?
    ): Flow<CommonResponse>

    /**
     * 移除收藏
     *
     * **需登录**
     *
     * @param threadId 贴子 ID
     */
    fun removeStoreFlow(
        threadId: String
    ): Flow<CommonResponse>

    /**
     * 添加/更新收藏
     *
     * **需登录**
     *
     * @param threadId 贴子 ID
     * @param postId 收藏到的回复 ID
     */
    fun addStoreFlow(
        threadId: Long,
        postId: Long
    ): Flow<CommonResponse>





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
     * 话题榜
     */
    fun topicListFlow(): Flow<TopicListResponse>

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
     * 楼中楼页面
     *
     * @param forumId 吧 ID
     * @param threadId 贴 ID
     * @param page 分页页码
     * @param postId 回复 ID
     * @param subPostId 楼中楼回复 ID
     */
    fun pbFloorFlow(
        threadId: Long,
        postId: Long,
        forumId: Long = 0L,
        page: Int = 1,
        subPostId: Long = 0L,
    ): Flow<PbFloorResponse>


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