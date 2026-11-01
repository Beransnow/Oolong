package com.huanchengfly.tieba.post.repository

import com.huanchengfly.tieba.core.network.agree.AgreeRemoteDataSource
import com.huanchengfly.tieba.core.network.TiebaServerException
import com.huanchengfly.tieba.core.network.agree.AgreeTarget
import com.huanchengfly.tieba.core.network.upload.UploadRemoteDataSource
import com.huanchengfly.tieba.core.network.user.UserRemoteDataSource
import com.huanchengfly.tieba.post.api.models.protos.PostInfoList
import com.huanchengfly.tieba.post.api.models.protos.User
import com.huanchengfly.tieba.post.arch.IoDispatcher
import com.huanchengfly.tieba.post.arch.Page
import com.huanchengfly.tieba.post.model.LikedForum
import com.huanchengfly.tieba.post.model.UserProfile
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import okio.ByteString.Companion.toByteString
import javax.inject.Inject

/** A page of what a user posted, and whether they hide their posts from others. */
data class UserPostsPage(val posts: Page<Int, PostInfoList>, val hidden: Boolean = false)

/** A user's profile, what they posted and the forums they follow, and following them. */
class UserRepository @Inject constructor(
    private val remote: UserRemoteDataSource,
    private val agreeRemote: AgreeRemoteDataSource,
    private val uploadRemote: UploadRemoteDataSource,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    suspend fun profile(uid: Long): UserProfile = withContext(io) {
        checkNotNull(remote.profile(uid).user).toUserProfile()
    }

    /** Page [page] (from 1) of the threads [uid] started, or with [threads] false of their replies. */
    suspend fun posts(uid: Long, threads: Boolean, page: Int): UserPostsPage = withContext(io) {
        val data = remote.posts(uid, page, threads)
        UserPostsPage(
            posts = Page(data.post_list, next = (page + 1).takeIf { data.post_list.isNotEmpty() }),
            hidden = data.hide_post == 1,
        )
    }

    suspend fun likedForums(uid: Long, page: Int): Page<Int, LikedForum> = withContext(io) {
        val response = remote.likedForums(uid, page)
        val forums = response.forumList?.forums.orEmpty().mapNotNull { forum ->
            LikedForum(
                id = forum.id.toLongOrNull() ?: return@mapNotNull null,
                name = forum.name.orEmpty(),
                avatar = forum.avatar.orEmpty(),
                level = forum.levelId?.toIntOrNull() ?: 0,
                slogan = forum.slogan.orEmpty(),
            )
        }
        Page(forums, next = (page + 1).takeIf { response.hasMore == "1" })
    }

    suspend fun follow(user: UserProfile) = withContext(io) { remote.follow(user.portrait) }

    suspend fun unfollow(user: UserProfile) = withContext(io) { remote.unfollow(user.portrait) }

    /** Saves the user's own nickname, sex and intro; the birthday goes back as [profile] has it. */
    suspend fun modifyProfile(profile: UserProfile, nickName: String, sex: Int, intro: String) = withContext(io) {
        remote.modifyProfile(nickName, sex, intro, profile.birthdayShown, profile.birthdayTime)
    }

    /**
     * Uploads a new portrait for the user. Returns what the server says when it holds the portrait
     * for review, which it reports as an error, or null when it took it at once.
     */
    suspend fun uploadPortrait(picture: ByteArray): String? = withContext(io) {
        try {
            uploadRemote.uploadPortrait(picture.toByteString())
            null
        } catch (e: TiebaServerException) {
            if (e.code == PORTRAIT_IN_REVIEW) e.message else throw e
        }
    }

    suspend fun agreeThread(post: PostInfoList, agree: Boolean) = withContext(io) {
        agreeRemote.opAgree(AgreeTarget.Thread(post.thread_id, post.post_id), post.forum_id, undo = !agree)
    }
}

/** The upload of a portrait that waits for review, which the server sends as an error. */
private const val PORTRAIT_IN_REVIEW = 300003

private fun User.toUserProfile() = UserProfile(
    id = id,
    name = name,
    nameShow = nameShow.takeIf { it != name }.orEmpty(),
    portrait = portrait,
    intro = intro,
    sex = sex,
    tiebaUid = tieba_uid,
    ipLocation = ip_address,
    tbAge = tb_age,
    followCount = concern_num,
    fanCount = fans_num,
    agreeCount = total_agree_num.toLong(),
    threadCount = thread_num,
    postCount = post_num,
    forumCount = my_like_num,
    followed = has_concerned != 0,
    bazhuTitle = bazhu_grade?.desc?.takeIf { it.isNotEmpty() },
    godField = new_god_data?.takeIf { it.status != 0 }?.field_name?.takeIf { it.isNotEmpty() },
    birthdayTime = birthday_info?.birthday_time ?: 0,
    birthdayShown = birthday_info?.birthday_show_status == 1,
)
