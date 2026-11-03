package oolong.core.network.message

import oolong.core.network.CommonParams
import oolong.core.network.TiebaNetwork
import oolong.core.network.TiebaProfile
import oolong.core.network.form.signedFormBody
import oolong.core.network.identity.DeviceIdentity

class MessageRemoteDataSource(
    network: TiebaNetwork,
    private val commonParams: CommonParams,
    private val device: DeviceIdentity,
) {
    private val profile = TiebaProfile.MESSAGE_FORM_12_52
    private val service = network.service<MessageService>(profile)

    /** Unread message counts. The request has no parameters of its own. */
    suspend fun reminder(): MessageReminderResponse =
        service.reminder(signedFormBody(commonParams.form(profile), withSig = false))

    /** A page of the replies to the user's posts, newest first. [page] counts from 1. */
    suspend fun replyMe(page: Int): FeedResponse = service.replyMe(feedBody(page))

    /** A page of the posts that mention the user, newest first. [page] counts from 1. */
    suspend fun atMe(page: Int): FeedResponse = service.atMe(feedBody(page))

    private fun feedBody(page: Int) = signedFormBody(
        commonParams.form(profile) + listOf(
            "pn" to page.toString(),
            "uid" to commonParams.session()?.uid.orEmpty(),
            "time" to "0",
            "tid" to "0",
            "is_first" to "0",
            "call_from" to "",
            "scr_dip" to device.screenDensity.toString(),
            "scr_h" to device.screenHeight.toString(),
            "scr_w" to device.screenWidth.toString(),
        ),
        withSig = false,
    )
}
