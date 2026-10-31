package com.huanchengfly.tieba.core.network

import com.huanchengfly.tieba.core.network.identity.DeviceIdentity
import com.huanchengfly.tieba.core.network.identity.InstallIdentity
import com.huanchengfly.tieba.core.network.identity.SessionProvider
import com.huanchengfly.tieba.post.api.models.protos.CommonRequest
import java.util.Calendar
import kotlin.random.Random

/**
 * Builds the common parameters and headers every request carries, from the injected identities.
 */
class CommonParams(
    private val device: DeviceIdentity,
    private val install: InstallIdentity,
    private val sessionProvider: SessionProvider,
    private val clock: () -> Long = System::currentTimeMillis,
    random: Random = Random.Default,
) {
    private val processStartTime = clock()

    /** Used until `c/s/sync` has issued a client id. */
    private val fallbackClientId = "wappc_${processStartTime}_${random.nextInt(1000)}"

    fun userAgent(profile: TiebaProfile): String =
        "${device.webViewUserAgent} tieba/${profile.version}"

    fun headers(profile: TiebaProfile): List<Pair<String, String>> = buildList {
        add("Charset" to "UTF-8")
        add("client_type" to "2")
        sessionProvider.currentSession()?.let { add("client_user_token" to it.uid) }
        add("cookie" to "ka=open; CUID=${device.cuid}; TBBRAND=${device.model}")
        add("cuid" to device.cuid)
        add("cuid_galaxy2" to device.cuid)
        add("cuid_gid" to "")
        add("c3_aid" to device.c3Aid)
        add("User-Agent" to userAgent(profile))
        if (profile.protocol == TiebaProtocol.PROTOBUF) add("x_bd_data_type" to "protobuf")
        add("client_logid" to processStartTime.toString())
    }

    /**
     * The `common` field of a protobuf request.
     *
     * @param includeTbs whether to send the account's `tbs`, which write operations need
     */
    fun protobuf(profile: TiebaProfile, includeTbs: Boolean = false): CommonRequest {
        val session = sessionProvider.currentSession()
        val now = clock()
        return CommonRequest(
            _client_type = 2,
            _client_version = profile.version,
            _client_id = install.clientId ?: fallbackClientId,
            _phone_imei = device.imei,
            from = CHANNEL,
            cuid = device.cuid,
            _timestamp = now,
            model = device.model,
            BDUSS = session?.bduss,
            tbs = session?.tbs?.takeIf { includeTbs },
            net_type = 1,
            pversion = "1.0.3",
            _os_version = device.osVersion,
            brand = device.brand,
            lego_lib_version = "3.0.0",
            applist = "",
            stoken = session?.stoken,
            z_id = session?.zid,
            cuid_galaxy2 = device.cuid,
            cuid_gid = "",
            oaid = device.oaid,
            c3_aid = device.c3Aid,
            sample_id = install.sampleId,
            scr_w = device.screenWidth,
            scr_h = device.screenHeight,
            scr_dip = device.screenDensity.toDouble(),
            q_type = 0,
            is_teenager = 0,
            sdk_ver = profile.sdkVersion,
            framework_ver = profile.frameworkVersion,
            swan_game_ver = profile.swanGameVersion,
            active_timestamp = install.activeTimestamp,
            first_install_time = install.firstInstallTime,
            last_update_time = install.lastUpdateTime,
            event_day = eventDay(now),
            android_id = device.androidId,
            cmode = 1,
            start_scheme = "",
            start_type = 1,
            extra = "",
            user_agent = userAgent(profile),
            personalized_rec_switch = 1,
            device_score = device.deviceScore.toString(),
            mac = MAC,
        )
    }

    /** The `stoken` form field protobuf requests carry next to the message, when logged in. */
    fun stokenField(): List<Pair<String, String>> =
        sessionProvider.currentSession()?.stoken?.let { listOf("stoken" to it) }.orEmpty()

    /** Year, month and day without zero padding, such as `2026111` for 2026-11-01. */
    private fun eventDay(time: Long): String {
        val calendar = Calendar.getInstance().apply { timeInMillis = time }
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH) + 1
        val day = calendar.get(Calendar.DAY_OF_MONTH)
        return "$year$month$day"
    }

    private companion object {
        const val CHANNEL = "1020031h"

        /** What Android has returned for the MAC address since Android 6. */
        const val MAC = "02:00:00:00:00:00"
    }
}
