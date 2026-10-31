package com.huanchengfly.tieba.core.network.identity

/**
 * The logged-in account a request is sent as.
 */
data class Session(
    val uid: String,
    val bduss: String,
    val stoken: String?,
    val tbs: String?,
    /** Device token issued by Sofire, cached per account. */
    val zid: String?,
)

fun interface SessionProvider {
    /** The current session, or `null` when logged out. */
    fun currentSession(): Session?
}
