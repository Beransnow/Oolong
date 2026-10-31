package com.huanchengfly.tieba.core.network.sign

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MSignResponse(
    @SerialName("show_dialog") val showDialog: String? = null,
    @SerialName("sign_notice") val signNotice: String? = null,
    @SerialName("is_timeout") val isTimeout: String? = null,
    @SerialName("timeout_notice") val timeoutNotice: String? = null,
    val info: List<ForumResult> = emptyList(),
) {
    @Serializable
    data class ForumResult(
        @SerialName("forum_id") val forumId: String? = null,
        @SerialName("forum_name") val forumName: String? = null,
        val signed: String? = null,
        @SerialName("is_on") val isOn: String? = null,
        @SerialName("is_filter") val isFilter: String? = null,
        @SerialName("sign_day_count") val signDayCount: String? = null,
        @SerialName("cur_score") val curScore: String? = null,
        val error: Error? = null,
    )

    @Serializable
    data class Error(
        @SerialName("err_no") val errNo: String? = null,
        val errmsg: String? = null,
        val usermsg: String? = null,
    )
}
