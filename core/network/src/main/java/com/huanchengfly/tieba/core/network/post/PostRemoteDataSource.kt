package com.huanchengfly.tieba.core.network.post

import com.huanchengfly.tieba.core.network.CaptchaFields
import com.huanchengfly.tieba.core.network.CommonParams
import com.huanchengfly.tieba.core.network.TiebaNetwork
import com.huanchengfly.tieba.core.network.TiebaProfile
import com.huanchengfly.tieba.core.network.TiebaServerException
import com.huanchengfly.tieba.core.network.form.signedParams
import com.huanchengfly.tieba.core.network.identity.SessionProvider
import com.huanchengfly.tieba.core.network.protobuf.ProtobufBody
import com.huanchengfly.tieba.core.network.writeResponseError
import com.huanchengfly.tieba.post.api.models.protos.addPollPost.AddPollPostRequest
import com.huanchengfly.tieba.post.api.models.protos.addPollPost.AddPollPostRequestData
import com.huanchengfly.tieba.post.api.models.protos.addPost.AddPostRequest
import com.huanchengfly.tieba.post.api.models.protos.addPost.AddPostRequestData
import com.huanchengfly.tieba.post.api.models.protos.addPost.AddPostResponse
import com.huanchengfly.tieba.post.api.models.protos.addPost.AddPostResponseData

class PostRemoteDataSource(
    network: TiebaNetwork,
    private val commonParams: CommonParams,
    private val sessionProvider: SessionProvider,
) {
    private val profile = TiebaProfile.PROTO_12_52
    private val service = network.service<PostService>(profile)

    /**
     * Posts a reply. [data] carries the business fields; the common request, with `tbs`, is
     * filled in here, and the top-level `tbs` and `sig` stay empty.
     *
     * The multipart body also carries the message task form parameters, signed with `sign` only.
     * A refreshed `anti_stat.tbs` is stored whether or not the post went through.
     *
     * @param tbs a fresher `tbs` than the account's, such as the one the thread page returned
     */
    suspend fun addPost(data: AddPostRequestData, tbs: String? = null): AddPostResponseData {
        val common = commonParams.protobuf(profile, includeTbs = true)
            .let { if (tbs != null) it.copy(tbs = tbs) else it }
        val request = AddPostRequest(data.copy(common = common, tbs = "", sig = "", bot_conf = data.bot_conf ?: ""))
        val fields = signedParams(commonParams.form(profile), withSig = false)
        val response = try {
            service.addPost(ProtobufBody(request, fields))
        } catch (e: TiebaServerException) {
            (e.response as? AddPostResponse)?.let { checkResponse(it, e.code, e.message.orEmpty()) }
            throw e
        }
        checkResponse(response, code = 0, message = "")
        return checkNotNull(response.data_)
    }

    /**
     * Votes for [optionIds] in a thread's poll. The request carries no common request; the form
     * fields beside it identify the user. An error in the leading data field is thrown as any
     * response's error is.
     */
    suspend fun addPollPost(threadId: Long, forumId: Long, optionIds: List<Int>) {
        val request = AddPollPostRequest(
            AddPollPostRequestData(thread_id = threadId, options = optionIds.joinToString(","), forum_id = forumId),
        )
        val fields = signedParams(commonParams.form(profile, includeTbs = true), withSig = false)
        val response = service.addPollPost(ProtobufBody(request, fields))
        response.error?.takeIf { it.error_code != 0 }?.let { throw TiebaServerException(it.error_code, it.error_msg, response) }
    }

    private fun checkResponse(response: AddPostResponse, code: Int, message: String) {
        val data = response.data_
        data?.anti_stat?.tbs?.takeIf { it.isNotEmpty() }?.let(sessionProvider::updateTbs)
        val info = data?.info
        val error = writeResponseError(
            code = code,
            message = message,
            needsCaptcha = info?.need_vcode == "1",
            captcha = {
                when {
                    !info?.vcode_md5.isNullOrEmpty() ->
                        CaptchaFields(info!!.vcode_md5, info.vcode_type, info.vcode_pic_url, info.vcode_extra)

                    !data?.anti?.vcode_md5.isNullOrEmpty() -> data!!.anti!!.let {
                        CaptchaFields(it.vcode_md5, it.vcode_type, it.vcode_pic_url, it.vcode_extra)
                    }

                    else -> null
                }
            },
            accessState = { info?.access_state },
            response = response,
        )
        if (error != null) throw error
    }
}
