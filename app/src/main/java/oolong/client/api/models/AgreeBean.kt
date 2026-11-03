package oolong.client.api.models

import com.google.gson.annotations.SerializedName
import oolong.client.models.BaseBean

class AgreeBean : BaseBean() {
    @SerializedName("error_code")
    val errorCode: String? = null

    @SerializedName("error_msg")
    val errorMsg: String? = null
    val data: AgreeDataBean? = null

    class AgreeDataBean {
        val agree: AgreeInfoBean? = null
    }

    class AgreeInfoBean {
        val score: String? = null
    }
}
