package oolong.core.network.form

import oolong.core.network.CommonParams
import oolong.core.network.TiebaProfile

/**
 * The signed query of a data request an in-app web page sends: the common parameters with the
 * account's `tbs`, then [params], marked as coming from such a page: [subapp] names which kind.
 */
internal fun CommonParams.signedWebQuery(
    profile: TiebaProfile,
    params: List<Pair<String, String>>,
    subapp: String = "client_fe",
): Map<String, String> {
    val all = form(profile, includeTbs = true) + params + ("subapp_type" to subapp)
    return signedParams(all, withSig = false).toMap()
}
