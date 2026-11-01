package com.huanchengfly.tieba.core.network.explore

import com.huanchengfly.tieba.core.network.CommonParams
import com.huanchengfly.tieba.core.network.TiebaNetwork
import com.huanchengfly.tieba.core.network.TiebaProfile
import com.huanchengfly.tieba.core.network.form.signedFormBody
import com.huanchengfly.tieba.core.network.identity.DeviceIdentity
import com.huanchengfly.tieba.core.network.protobuf.ProtobufBody
import com.huanchengfly.tieba.post.api.models.protos.hotThreadList.HotThreadListRequest
import com.huanchengfly.tieba.post.api.models.protos.hotThreadList.HotThreadListRequestData
import com.huanchengfly.tieba.post.api.models.protos.hotThreadList.HotThreadListResponseData
import com.huanchengfly.tieba.post.api.models.protos.personalized.PersonalizedRequest
import com.huanchengfly.tieba.post.api.models.protos.personalized.PersonalizedRequestData
import com.huanchengfly.tieba.post.api.models.protos.personalized.PersonalizedResponseData
import com.huanchengfly.tieba.post.api.models.protos.topicList.TopicListRequest
import com.huanchengfly.tieba.post.api.models.protos.topicList.TopicListRequestData
import com.huanchengfly.tieba.post.api.models.protos.topicList.TopicListResponseData
import com.huanchengfly.tieba.post.api.models.protos.userLike.UserLikeRequest
import com.huanchengfly.tieba.post.api.models.protos.userLike.UserLikeRequestData
import com.huanchengfly.tieba.post.api.models.protos.userLike.UserLikeResponseData
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.put

/** The feeds of the explore tab: recommended threads, the followed users' threads and the hot lists. */
class ExploreRemoteDataSource(
    network: TiebaNetwork,
    private val commonParams: CommonParams,
    private val device: DeviceIdentity,
) {
    private val protoProfile = TiebaProfile.PROTO_12_52
    private val formProfile = TiebaProfile.FORM_12_52
    private val service = network.service<ExploreService>(protoProfile)
    private val formService = network.service<ExploreFormService>(formProfile)

    /**
     * A page of recommended threads.
     *
     * @param refresh whether the user pulled to refresh, rather than scrolled to the end
     * @param page page number, from 1
     */
    suspend fun personalized(refresh: Boolean, page: Int): PersonalizedResponseData {
        val request = PersonalizedRequest(
            PersonalizedRequestData(
                app_pos = commonParams.appPos(),
                common = commonParams.protobuf(protoProfile),
                load_type = if (refresh) 1 else 2,
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
                scr_dip = device.screenDensity.toDouble(),
                scr_h = device.screenHeight,
                scr_w = device.screenWidth,
            )
        )
        return checkNotNull(service.personalized(ProtobufBody(request, commonParams.stokenField())).data_)
    }

    /**
     * A page of what the users the user follows posted.
     *
     * @param pageTag the tag the previous page returned, empty for the first page
     * @param lastRequestUnix the `requestUnix` of the last first page loaded, 0 if none
     */
    suspend fun userLike(pageTag: String, lastRequestUnix: Long): UserLikeResponseData {
        val request = UserLikeRequest(
            UserLikeRequestData(
                common = commonParams.protobuf(protoProfile),
                pageTag = pageTag,
                lastRequestUnix = lastRequestUnix,
                followType = 1,
                loadType = if (pageTag.isEmpty()) 1 else 2,
            )
        )
        return checkNotNull(service.userLike(ProtobufBody(request, commonParams.stokenField())).data_)
    }

    /** The hot threads in the category [tabCode] ("all" for every category), with the categories and hot topics. */
    suspend fun hotThreads(tabCode: String): HotThreadListResponseData {
        val request = HotThreadListRequest(
            HotThreadListRequestData(
                common = commonParams.protobuf(protoProfile),
                tabCode = tabCode,
                tabId = "1",
            )
        )
        return checkNotNull(service.hotThreadList(ProtobufBody(request, commonParams.stokenField())).data_)
    }

    /** The hot topics ranking. */
    suspend fun topics(): TopicListResponseData {
        val request = TopicListRequest(
            TopicListRequestData(
                common = commonParams.protobuf(protoProfile),
                call_from = "newbang",
                list_type = "all",
                need_tab_list = "0",
                fid = 0L,
            )
        )
        return checkNotNull(service.topicList(ProtobufBody(request, commonParams.stokenField())).data_)
    }

    /**
     * Tells the server the user doesn't want to see threads like [threadId].
     *
     * @param reasonIds the ids of the reasons the user picked
     * @param extras the `extra` of each reason picked
     * @param clickTime when the user opened the menu, in Unix milliseconds
     */
    suspend fun dislike(threadId: Long, forumId: Long?, reasonIds: List<Int>, extras: List<String>, clickTime: Long) {
        val dislike = buildJsonArray {
            addJsonObject {
                put("tid", threadId.toString())
                put("dislike_ids", reasonIds.joinToString(","))
                forumId?.let { put("fid", it.toString()) }
                put("click_time", clickTime)
                put("extra", extras.joinToString(","))
            }
        }
        val params = commonParams.form(formProfile) + listOf(
            "dislike" to dislike.toString(),
            "dislike_from" to "homepage",
        )
        formService.submitDislike(signedFormBody(params, withSig = false))
    }
}
