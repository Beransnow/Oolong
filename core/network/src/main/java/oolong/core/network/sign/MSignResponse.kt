package oolong.core.network.sign

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonTransformingSerializer

/**
 * When one-click sign-in is refused as a whole, [info] comes as a string and [error] says why;
 * [info] is then empty.
 */
@Serializable
data class MSignResponse(
    @SerialName("show_dialog") val showDialog: String? = null,
    @SerialName("sign_notice") val signNotice: String? = null,
    @SerialName("is_timeout") val isTimeout: String? = null,
    @SerialName("timeout_notice") val timeoutNotice: String? = null,
    @Serializable(with = ForumResultsSerializer::class) val info: List<ForumResult> = emptyList(),
    val error: Refusal? = null,
) {
    /** What to tell the user when no forum was signed in to, if the server said anything. */
    val refusal: String?
        get() = listOf(error?.usermsg, signNotice, error?.errmsg).firstOrNull { !it.isNullOrBlank() }

    @Serializable
    data class Refusal(
        val errno: Int? = null,
        val errmsg: String? = null,
        val usermsg: String? = null,
    )

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

private object ForumResultsSerializer : JsonTransformingSerializer<List<MSignResponse.ForumResult>>(
    ListSerializer(MSignResponse.ForumResult.serializer()),
) {
    override fun transformDeserialize(element: JsonElement): JsonElement =
        element as? JsonArray ?: JsonArray(emptyList())
}
