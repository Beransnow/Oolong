package oolong.migration

import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Build
import java.security.MessageDigest

/** Oolong, the only app the data goes to. */
const val OOLONG_PACKAGE = "oolong.client"

/**
 * SHA-256 of the certificates Oolong may be signed with: its release key, and in debug builds the
 * debug key every local build uses. A package name alone proves nothing: anyone can take one.
 */
private val OOLONG_CERTIFICATES: Set<String> = buildSet {
    add("9cbc612f0bbdfba80000d021cd73b1e6e619c186fdc6b32f1e11382bd9e4b725")
    if (BuildConfig.DEBUG) add("cbfebf02b64acc8ff13f92a716205bb706bdf18fda3bf543a4277c395d2b25a0")
}

/** Whether [packageName] is installed and signed with one of Oolong's keys. */
fun PackageManager.isOolong(packageName: String?): Boolean {
    if (packageName != OOLONG_PACKAGE) return false
    return signerDigests(packageName).any { it in OOLONG_CERTIFICATES }
}

@SuppressLint("PackageManagerGetSignatures")
@Suppress("DEPRECATION")
private fun PackageManager.signerDigests(packageName: String): List<String> = try {
    val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        val info = getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES).signingInfo ?: return emptyList()
        // With a rotated key the current signers are the ones that count
        if (info.hasMultipleSigners()) info.apkContentsSigners else info.signingCertificateHistory
    } else {
        getPackageInfo(packageName, PackageManager.GET_SIGNATURES).signatures
    }
    signatures.orEmpty().map { sha256(it.toByteArray()) }
} catch (e: PackageManager.NameNotFoundException) {
    emptyList()
}

private fun sha256(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
