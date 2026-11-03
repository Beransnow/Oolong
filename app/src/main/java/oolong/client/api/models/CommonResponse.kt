package oolong.client.api.models

import com.google.gson.annotations.JsonAdapter
import com.google.gson.annotations.SerializedName
import oolong.client.api.adapters.ErrorMsgAdapter
import oolong.client.models.BaseBean

data class CommonResponse(
    @SerializedName("error_code", alternate = ["errno", "no"])
    val errorCode: Int = 0,
    @JsonAdapter(ErrorMsgAdapter::class)
    @SerializedName("error_msg", alternate = ["errmsg", "error"])
    val errorMsg: String = ""
) : BaseBean()
