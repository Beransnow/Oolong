package com.huanchengfly.tieba.core.network.sign

import com.huanchengfly.tieba.core.network.CommonParams
import com.huanchengfly.tieba.core.network.TiebaNetwork
import com.huanchengfly.tieba.core.network.TiebaProfile
import com.huanchengfly.tieba.core.network.form.signedFormBody

class SignRemoteDataSource(
    network: TiebaNetwork,
    private val commonParams: CommonParams,
) {
    private val profile = TiebaProfile.FORM_12_52
    private val service = network.service<SignService>(profile)

    /**
     * Signs in to a forum.
     *
     * @param tbs a fresher `tbs` than the account's, such as the one the forum page returned
     */
    suspend fun sign(forumId: Long, forumName: String, tbs: String? = null): SignResponse {
        val common = commonParams.form(profile, includeTbs = tbs == null)
        val params = common +
                listOfNotNull("fid" to forumId.toString(), "kw" to forumName, tbs?.let { "tbs" to it })
        // sig is on by default and only turned off by the android_enc_sig_new switch from sync
        return service.sign(signedFormBody(params, withSig = true))
    }

    /**
     * Signs in to up to [MAX_MSIGN_FORUMS] forums at once.
     *
     * The official client always sends `authsid`, as the literal `null` until an account access
     * check has issued one.
     */
    suspend fun mSign(forumIds: List<Long>, tbs: String? = null): MSignResponse {
        require(forumIds.size <= MAX_MSIGN_FORUMS) { "At most $MAX_MSIGN_FORUMS forums per request" }
        val session = checkNotNull(commonParams.session()) { "Not logged in" }
        val params = commonParams.form(profile, includeTbs = tbs == null) + listOfNotNull(
            "user_id" to session.uid,
            "forum_ids" to forumIds.joinToString(","),
            "authsid" to "null",
            tbs?.let { "tbs" to it },
        )
        return service.mSign(signedFormBody(params, withSig = true))
    }

    companion object {
        const val MAX_MSIGN_FORUMS = 50
    }
}
