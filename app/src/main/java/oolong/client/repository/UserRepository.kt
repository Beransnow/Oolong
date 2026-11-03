package oolong.client.repository

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import okio.ByteString.Companion.toByteString
import oolong.client.api.models.protos.User
import oolong.client.arch.IoDispatcher
import oolong.client.arch.Page
import oolong.client.model.LikedForum
import oolong.client.model.ListedUser
import oolong.client.model.ThreadSummary
import oolong.client.model.UserPost
import oolong.client.model.UserProfile
import oolong.core.network.TiebaServerException
import oolong.core.network.upload.UploadRemoteDataSource
import oolong.core.network.user.UserRemoteDataSource
import java.io.File
import javax.inject.Inject

/** What the user bars another user from: following them, interacting with their posts, messaging them. */
data class BlackSettings(val follow: Boolean, val interact: Boolean, val chat: Boolean) {
    val any: Boolean get() = follow || interact || chat
}

/** A page of what a user posted, and whether they hide their posts from others. */
data class UserPostsPage(val posts: Page<Int, UserPost>, val hidden: Boolean = false)

/** A user's profile, what they posted and the forums they follow, and following them. */
class UserRepository @Inject constructor(
    private val remote: UserRemoteDataSource,
    private val uploadRemote: UploadRemoteDataSource,
    @IoDispatcher private val io: CoroutineDispatcher,
    private val emoticons: EmoticonRegistry,
) {
    suspend fun profile(uid: Long): UserProfile = withContext(io) {
        checkNotNull(remote.profile(uid).user).toUserProfile()
    }

    /** Page [page] (from 1) of the threads [uid] started, or with [threads] false of their replies. */
    suspend fun posts(uid: Long, threads: Boolean, page: Int): UserPostsPage = withContext(io) {
        val data = remote.posts(uid, page, threads)
        UserPostsPage(
            posts = Page(data.post_list.map { it.toUserPost(emoticons) }, next = (page + 1).takeIf { data.post_list.isNotEmpty() }),
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

    /** Page [page] (from 1) of the users [uid] follows. */
    suspend fun follows(uid: Long, page: Int): Page<Int, ListedUser> = withContext(io) {
        val response = remote.follows(uid, page)
        Page(response.users.mapNotNull { it.toListedUser() }, next = (page + 1).takeIf { response.hasMore == "1" })
    }

    /** Page [page] (from 1) of the users who follow [uid]. */
    suspend fun fans(uid: Long, page: Int): Page<Int, ListedUser> = withContext(io) {
        val response = remote.fans(uid, page)
        Page(response.users.mapNotNull { it.toListedUser() }, next = (page + 1).takeIf { response.page?.hasMore == "1" })
    }

    suspend fun follow(user: UserProfile) = withContext(io) { remote.follow(user.portrait) }

    suspend fun blackSettings(uid: Long): BlackSettings = withContext(io) {
        val permissions = remote.blackInfo(uid)
        BlackSettings(follow = permissions.follow == "1", interact = permissions.interact == "1", chat = permissions.chat == "1")
    }

    suspend fun setBlack(uid: Long, settings: BlackSettings) = withContext(io) {
        remote.setBlack(uid, follow = settings.follow, interact = settings.interact, chat = settings.chat)
    }

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

    suspend fun uploadPortrait(picture: File): String? = uploadPortrait(withContext(io) { picture.readBytes() })
}

/** The upload of a portrait that waits for review, which the server sends as an error. */
private const val PORTRAIT_IN_REVIEW = 300003

private fun oolong.core.network.user.ListedUser.toListedUser(): ListedUser? {
    val id = id?.toLongOrNull() ?: return null
    return ListedUser(
        user = oolong.client.model.User(
            id = id,
            name = name.orEmpty(),
            nameShow = nameShow.orEmpty().takeIf { it != name }.orEmpty(),
            portrait = portrait.orEmpty(),
        ),
        // Left unwritten, an intro comes as the server's own line, which says nothing of them
        intro = intro.orEmpty().takeUnless { it.startsWith(DefaultIntro) }.orEmpty(),
        followed = followed == "1",
    )
}

private const val DefaultIntro = "签名是一种态度，我想我可以更酷"

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
