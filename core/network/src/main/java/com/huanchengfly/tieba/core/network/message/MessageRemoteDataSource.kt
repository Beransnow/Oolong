package com.huanchengfly.tieba.core.network.message

import com.huanchengfly.tieba.core.network.CommonParams
import com.huanchengfly.tieba.core.network.TiebaNetwork
import com.huanchengfly.tieba.core.network.TiebaProfile
import com.huanchengfly.tieba.core.network.form.signedFormBody

class MessageRemoteDataSource(
    network: TiebaNetwork,
    private val commonParams: CommonParams,
) {
    private val profile = TiebaProfile.MESSAGE_FORM_12_52
    private val service = network.service<MessageService>(profile)

    /** Unread message counts. The request has no parameters of its own. */
    suspend fun reminder(): MessageReminderResponse =
        service.reminder(signedFormBody(commonParams.form(profile), withSig = false))
}
