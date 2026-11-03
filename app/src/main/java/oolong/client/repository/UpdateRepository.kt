package oolong.client.repository

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.CacheControl
import okhttp3.OkHttpClient
import okhttp3.Request
import oolong.client.BuildConfig
import oolong.client.arch.ApplicationScope
import oolong.client.arch.IoDispatcher
import oolong.client.dataStore
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.days

/** Where releases are published: each channel's manifest under `update/`, the APKs under `apk/`. */
private const val UpdateHost = "https://dl.example.com"

/** A release as a channel's manifest describes it. */
@Serializable
data class Release(
    val versionCode: Int,
    val versionName: String,
    val publishedAt: String,
    val url: String,
    val size: Long,
    val sha256: String,
    /** What changed, by language and then by group (新增, 修复…) in the order shown. */
    val notes: Map<String, Map<String, List<String>>> = emptyMap(),
    /** Versions below this one no longer work, and may not skip this release. */
    val minSupported: Int = 0,
) {
    val isNewer: Boolean get() = versionCode > BuildConfig.VERSION_CODE
    val required: Boolean get() = BuildConfig.VERSION_CODE < minSupported

    /** The notes in the language the app is shown in, else Chinese. */
    fun notesFor(locale: Locale): Map<String, List<String>> =
        notes[locale.language] ?: notes["zh"] ?: notes.values.firstOrNull().orEmpty()
}

enum class UpdateChannel(val key: String) { Stable("stable"), Beta("beta") }

sealed interface UpdateCheck {
    data object Idle : UpdateCheck

    data object Checking : UpdateCheck

    data class Failed(val error: Throwable) : UpdateCheck

    /** The channel's [latest] release, which may be the one installed. */
    data class Done(val latest: Release) : UpdateCheck
}

sealed interface UpdateDownload {
    data object Idle : UpdateDownload

    data class Running(val release: Release, val progress: Float) : UpdateDownload

    data class Failed(val release: Release, val error: Throwable) : UpdateDownload

    /** Downloaded and checked: [file] is [release], signed as the app installed is. */
    data class Ready(val release: Release, val file: File) : UpdateDownload
}

/** Thrown when a downloaded package is not the release, or not signed as the app installed is. */
class UpdateVerificationException(message: String) : IOException(message)

/**
 * Checks the chosen channel for a newer release, downloads it and hands it to the system installer.
 * Downloads go on in the application's scope, so leaving the update page doesn't stop them.
 */
@Singleton
class UpdateRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val io: CoroutineDispatcher,
    @ApplicationScope private val scope: CoroutineScope,
) {
    // Its own client: the app's clients carry Tieba's cookies and parameters, which have no
    // business going to the release host
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    private val json = Json { ignoreUnknownKeys = true }

    private val _check = MutableStateFlow<UpdateCheck>(UpdateCheck.Idle)
    val check: StateFlow<UpdateCheck> = _check.asStateFlow()

    private val _download = MutableStateFlow<UpdateDownload>(UpdateDownload.Idle)
    val download: StateFlow<UpdateDownload> = _download.asStateFlow()

    private var checkJob: Job? = null
    private var downloadJob: Job? = null

    private val prefs = context.dataStore
    val autoCheck: Flow<Boolean> = prefs.data.map { it[AutoCheckKey] ?: true }
    val channel: Flow<UpdateChannel> = prefs.data.map { prefs ->
        UpdateChannel.entries.firstOrNull { it.key == prefs[ChannelKey] } ?: UpdateChannel.Stable
    }

    /** A newer release the user hasn't skipped, for the About page's row and the launch hint. */
    val available: Flow<Release?> = combine(check, prefs.data) { check, prefs ->
        (check as? UpdateCheck.Done)?.latest
            ?.takeIf { it.isNewer && (it.required || it.versionCode != prefs[SkippedKey]) }
    }

    /** Whether the launch hint for [release] has been shown, which happens once per release. */
    suspend fun hinted(release: Release): Boolean = prefs.data.first()[HintedKey] == release.versionCode

    suspend fun markHinted(release: Release) {
        prefs.edit { it[HintedKey] = release.versionCode }
    }

    suspend fun setAutoCheck(enabled: Boolean) {
        prefs.edit { it[AutoCheckKey] = enabled }
    }

    suspend fun setChannel(channel: UpdateChannel) {
        prefs.edit { it[ChannelKey] = channel.key }
        checkNow()
    }

    suspend fun skip(release: Release) {
        prefs.edit { it[SkippedKey] = release.versionCode }
    }

    /** Checks unless a check is running. */
    fun checkNow() {
        if (checkJob?.isActive == true) return
        checkJob = scope.launch {
            _check.value = UpdateCheck.Checking
            _check.value = try {
                UpdateCheck.Done(fetch(channel.first())).also {
                    prefs.edit { it[LastCheckKey] = System.currentTimeMillis() }
                }
            } catch (e: Exception) {
                UpdateCheck.Failed(e)
            }
        }
    }

    /** Checks if checking by itself is on and the last check was a day or more ago. */
    fun checkIfDue() {
        scope.launch {
            if (!autoCheck.first()) return@launch
            val last = prefs.data.first()[LastCheckKey] ?: 0L
            if (System.currentTimeMillis() - last >= 1.days.inWholeMilliseconds) checkNow()
        }
    }

    private suspend fun fetch(channel: UpdateChannel): Release = withContext(io) {
        val request = Request.Builder()
            .url("$UpdateHost/update/${channel.key}.json")
            // The manifest changes in place: never take it from a cache on the way
            .cacheControl(CacheControl.FORCE_NETWORK)
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            json.decodeFromString<Release>(response.body.string())
        }
    }

    /** Downloads [release], unless it is downloading or downloaded already. */
    fun download(release: Release) {
        val current = _download.value
        if (current is UpdateDownload.Running || (current is UpdateDownload.Ready && current.release == release)) return
        downloadJob?.cancel()
        downloadJob = scope.launch {
            _download.value = UpdateDownload.Running(release, 0f)
            _download.value = try {
                UpdateDownload.Ready(release, fetchApk(release))
            } catch (e: Exception) {
                UpdateDownload.Failed(release, e)
            }
        }
    }

    private suspend fun fetchApk(release: Release): File = withContext(io) {
        val dir = File(context.cacheDir, UpdateDir).apply { mkdirs() }
        // Only the release being fetched is kept
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, "oolong-${release.versionName}.apk")
        val digest = MessageDigest.getInstance("SHA-256")
        val request = Request.Builder().url(release.url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            val body = response.body
            val total = body.contentLength().takeIf { it > 0 } ?: release.size
            body.byteStream().use { input ->
                file.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var read = 0L
                    var shown = 0f
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        output.write(buffer, 0, n)
                        digest.update(buffer, 0, n)
                        read += n
                        val progress = (read.toFloat() / total).coerceIn(0f, 1f)
                        if (progress - shown >= 0.005f) {
                            shown = progress
                            _download.value = UpdateDownload.Running(release, progress)
                        }
                    }
                }
            }
        }
        try {
            verify(file, release, digest.digest().toHex())
        } catch (e: Exception) {
            file.delete()
            throw e
        }
        file
    }

    /** The package must be the release, the same app, and signed by the key the installed app is. */
    private fun verify(file: File, release: Release, sha256: String) {
        if (!sha256.equals(release.sha256, ignoreCase = true)) throw UpdateVerificationException("checksum")
        val pm = context.packageManager
        val archive = pm.getPackageArchiveInfo(file.path, SignatureFlags)
            ?: throw UpdateVerificationException("unreadable")
        if (archive.packageName != context.packageName) throw UpdateVerificationException("package")
        val installed = pm.getPackageInfo(context.packageName, SignatureFlags)
        val theirs = archive.signerDigests()
        if (theirs.isEmpty() || theirs.none { it in installed.signerDigests() }) {
            throw UpdateVerificationException("signature")
        }
    }

    /**
     * Installs [file] in a session. From Android 12 an app updating itself, once it is its own
     * installer, needs no confirmation; the first time, before Android 12, or without leave to
     * install apps, the system asks, which [UpdateInstallReceiver] passes on.
     */
    fun install(file: File) {
        scope.launch(io) {
            val installer = context.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                setAppPackageName(context.packageName)
                setSize(file.length())
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) setRequestUpdateOwnership(true)
            }
            val id = installer.createSession(params)
            installer.openSession(id).use { session ->
                session.openWrite("oolong.apk", 0, file.length()).use { output ->
                    file.inputStream().use { it.copyTo(output) }
                    session.fsync(output)
                }
                val intent = Intent(context, UpdateInstallReceiver::class.java)
                val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                    (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
                session.commit(PendingIntent.getBroadcast(context, id, intent, flags).intentSender)
            }
        }
    }

    private fun PackageInfo.signerDigests(): Set<String> {
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val info = signingInfo ?: return emptySet()
            if (info.hasMultipleSigners()) info.apkContentsSigners else info.signingCertificateHistory
        } else {
            @Suppress("DEPRECATION")
            this.signatures
        }
        return signatures.orEmpty().map { MessageDigest.getInstance("SHA-256").digest(it.toByteArray()).toHex() }.toSet()
    }

    private companion object {
        const val UpdateDir = "update"

        @Suppress("DEPRECATION")
        val SignatureFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            PackageManager.GET_SIGNATURES
        }

        val AutoCheckKey = booleanPreferencesKey("update_auto_check")
        val ChannelKey = stringPreferencesKey("update_channel")
        val SkippedKey = intPreferencesKey("update_skipped_version")
        val HintedKey = intPreferencesKey("update_hinted_version")
        val LastCheckKey = longPreferencesKey("update_last_check")
    }
}

private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
