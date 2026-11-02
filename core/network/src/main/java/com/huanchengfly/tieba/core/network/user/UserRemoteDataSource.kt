package com.huanchengfly.tieba.core.network.user

import com.huanchengfly.tieba.core.network.CommonParams
import com.huanchengfly.tieba.core.network.TiebaNetwork
import com.huanchengfly.tieba.core.network.TiebaProfile
import com.huanchengfly.tieba.core.network.form.signedFormBody
import com.huanchengfly.tieba.core.network.identity.DeviceIdentity
import com.huanchengfly.tieba.core.network.protobuf.ProtobufBody
import com.huanchengfly.tieba.post.api.models.protos.profile.ProfileRequest
import com.huanchengfly.tieba.post.api.models.protos.profile.ProfileRequestData
import com.huanchengfly.tieba.post.api.models.protos.profile.ProfileResponseData
import com.huanchengfly.tieba.post.api.models.protos.userPost.UserPostRequest
import com.huanchengfly.tieba.post.api.models.protos.userPost.UserPostRequestData
import com.huanchengfly.tieba.post.api.models.protos.userPost.UserPostResponseData

/** A user's profile and what they posted and follow, and following them. */
class UserRemoteDataSource(
    network: TiebaNetwork,
    private val commonParams: CommonParams,
    private val device: DeviceIdentity,
) {
    private val protoProfile = TiebaProfile.PROTO_12_52
    private val formProfile = TiebaProfile.FORM_12_52
    private val service = network.service<UserService>(protoProfile)
    private val formService = network.service<UserFormService>(formProfile)

    private val viewerUid: Long? get() = commonParams.session()?.uid?.toLongOrNull()

    /** The profile of [uid], as the user themselves or as a guest of theirs. */
    suspend fun profile(uid: Long): ProfileResponseData {
        val viewer = viewerUid
        val self = viewer == uid
        val request = ProfileRequest(
            ProfileRequestData(
                common = commonParams.protobuf(protoProfile),
                friend_uid = uid.takeIf { !self },
                friend_uid_portrait = "",
                has_plist = 1,
                is_from_usercenter = 1,
                is_guest = if (self) 0 else 1,
                need_post_count = 1,
                page = 1,
                pn = 1,
                q_type = 0,
                rn = 20,
                scr_dip = device.screenDensity.toDouble(),
                scr_h = device.screenHeight,
                scr_w = device.screenWidth,
                uid = viewer,
            )
        )
        return checkNotNull(service.profile(ProtobufBody(request, commonParams.stokenField())).data_)
    }

    /**
     * A page of the threads [uid] started, or with [threads] false of their replies.
     *
     * @param page page number, from 1
     */
    suspend fun posts(uid: Long, page: Int, threads: Boolean): UserPostResponseData {
        val request = UserPostRequest(
            UserPostRequestData(
                uid = uid,
                rn = 20,
                is_thread = if (threads) 1 else 0,
                need_content = 1,
                pn = page,
                common = commonParams.protobuf(protoProfile),
                scr_w = device.screenWidth,
                scr_h = device.screenHeight,
                scr_dip = device.screenDensity.toDouble(),
                q_type = 1,
                is_view_card = if (threads) 1 else 0,
                subtype = 0.takeUnless { threads },
            )
        )
        return checkNotNull(service.userPost(ProtobufBody(request, commonParams.stokenField())).data_)
    }

    /** A page (from 1) of the forums [uid] follows. */
    suspend fun likedForums(uid: Long, page: Int): LikedForumsResponse {
        val viewer = viewerUid
        val guest = viewer != uid
        val params = commonParams.form(formProfile) + listOfNotNull(
            "page_no" to page.toString(),
            "page_size" to "50",
            viewer?.let { "uid" to it.toString() },
            ("friend_uid" to uid.toString()).takeIf { guest },
            ("is_guest" to "1").takeIf { guest },
        )
        return formService.likedForums(signedFormBody(params, withSig = false))
    }

    /** Follows the user of [portrait]; unlike unfollowing, this is sent with `sig`. */
    suspend fun follow(portrait: String, tbs: String? = null) {
        val params = commonParams.form(formProfile, includeTbs = tbs == null) + listOfNotNull(
            "portrait" to portrait,
            "authsid" to "null",
            "from_type" to "2",
            "in_live" to "0",
            tbs?.let { "tbs" to it },
        )
        formService.follow(signedFormBody(params, withSig = true))
    }

    suspend fun unfollow(portrait: String, tbs: String? = null) {
        val params = commonParams.form(formProfile, includeTbs = tbs == null) + listOfNotNull(
            "portrait" to portrait,
            "authsid" to "null",
            "from_type" to "2",
            "in_live" to "0",
            tbs?.let { "tbs" to it },
        )
        formService.unfollow(signedFormBody(params, withSig = false))
    }

    /** What the user has barred [uid] from: following them, interacting with their posts, messaging them. */
    suspend fun blackInfo(uid: Long): BlackPermissions {
        val params = commonParams.form(formProfile) + listOf("black_uid" to uid.toString())
        return formService.blackInfo(signedFormBody(params, withSig = false)).permissions ?: BlackPermissions()
    }

    /** Bars [uid] from following the user, interacting with their posts and messaging them, as each is true. */
    suspend fun setBlack(uid: Long, follow: Boolean, interact: Boolean, chat: Boolean) {
        fun flag(barred: Boolean) = if (barred) 1 else 0
        val params = commonParams.form(formProfile, includeTbs = true) + listOf(
            "black_uid" to uid.toString(),
            "perm_list" to "{\"follow\":${flag(follow)},\"interact\":${flag(interact)},\"chat\":${flag(chat)}}",
        )
        formService.setBlack(signedFormBody(params, withSig = false))
    }

    /**
     * Changes the user's own profile.
     *
     * @param birthdayTime the birthday in Unix seconds, sent back as the profile had it
     * @param sex 1 male, 2 female
     */
    suspend fun modifyProfile(nickName: String, sex: Int, intro: String, birthdayShown: Boolean, birthdayTime: Long) {
        val params = commonParams.form(formProfile) + listOf(
            "birthday_show_status" to if (birthdayShown) "1" else "0",
            "birthday_time" to birthdayTime.toString(),
            "intro" to intro,
            "sex" to sex.toString(),
            "nick_name" to nickName,
            "cam" to "",
            "need_cam_decrypt" to "1",
            "need_keep_nickname_flag" to "0",
        )
        formService.modifyProfile(signedFormBody(params, withSig = false))
    }
}
