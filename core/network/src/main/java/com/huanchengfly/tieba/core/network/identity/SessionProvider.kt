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

    /** Stores the `tbs` a write response handed out, which replaces the account's current one. */
    fun updateTbs(tbs: String) {}
}
