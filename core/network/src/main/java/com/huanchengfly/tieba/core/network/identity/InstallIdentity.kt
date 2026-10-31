package com.huanchengfly.tieba.core.network.identity

/**
 * Identifiers of this installation. `_client_id` and `sample_id` are issued by `c/s/sync`.
 */
interface InstallIdentity {
    val clientId: String?
    val sampleId: String?

    /** Last time the app became active, in milliseconds. */
    val activeTimestamp: Long

    /** `PackageInfo.firstInstallTime`, in milliseconds. */
    val firstInstallTime: Long

    /** `PackageInfo.lastUpdateTime`, in milliseconds. */
    val lastUpdateTime: Long
}
