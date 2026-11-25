package oolong.client.model

data class SignDataBean(
    val forumName: String,
    val forumId: String,
    val userName: String,
    val tbs: String,
    val canUseMSign: Boolean = false
)
