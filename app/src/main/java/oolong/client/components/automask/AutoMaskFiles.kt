package oolong.client.components.automask

import android.content.Context
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import oolong.client.arch.ApplicationScope
import oolong.client.arch.IoDispatcher
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Where [AutoMaskFiles] stand: downloaded or not, and how far along. */
sealed interface AutoMaskDownload {
    /** Not downloaded; [bytes] to fetch. */
    data class Missing(val bytes: Long) : AutoMaskDownload

    data class Running(val progress: Float) : AutoMaskDownload

    data object Ready : AutoMaskDownload

    data class Failed(val bytes: Long, val error: Exception) : AutoMaskDownload
}

/**
 * The models and the LiteRT runtime that [AutoMask] needs: too large for an option few use, so
 * fetched the first time it is, from the release host. Each file is checked against the hash it
 * is pinned to here before it is kept; the runtime is native code, so nothing else may stand in.
 */
@Singleton
class AutoMaskFiles @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val io: CoroutineDispatcher,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private class Remote(val path: String, val name: String, val sha256: String, val size: Long)

    private val folder = File(context.filesDir, "automask/$Version")

    private val files: List<Remote> = buildList {
        add(Remote("models/automask/$Version/$Detector", Detector, "ed1a220aa9c52642c84075cd0d9e9923a0bb1985a1ea9e23d21b0bd8ba1fea55", 5_115_728))
        add(Remote("models/automask/$Version/$Encoder", Encoder, "1089090d59f9e6d159c84415c23fab2b97ae73b99b9453f50092d5c2887c1007", 14_244_036))
        add(Remote("models/automask/$Version/$Decoder", Decoder, "e2f4f85c7243b6debd290717796eace1793305e3683fc962402d403014de870b", 8_277_936))
        add(Remote("models/automask/$Version/$Gaussian", Gaussian, "9bf5b8dcada0e312a19b3a4c98a913f44df480d6aa72aedc2ea4558f979beff4", 1_024))
        runtime()?.let(::add)
    }

    /** What reading text needs besides: only for hiding everything at once, so fetched only for that. */
    private val readFiles: List<Remote> = listOf(
        Remote("models/automask/$Version/$Recognizer", Recognizer, "2fdd5dd8b89e00e9267e1c0918077497026d1ea7f18274489400dbe571bc94e7", 8_319_400),
        Remote("models/automask/$Version/$Characters", Characters, "d1979e9f794c464c0d2e0b70a7fe14dd978e9dc644c0e71f14158cdf8342af1b", 74_012),
    )

    // Its own client, as the updater's: the app's carry Tieba's cookies
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val _download = MutableStateFlow(if (downloaded()) AutoMaskDownload.Ready else AutoMaskDownload.Missing(missingBytes()))
    val download: StateFlow<AutoMaskDownload> = _download.asStateFlow()
    private var job: Job? = null

    private val _readDownload = MutableStateFlow(if (downloaded(readFiles)) AutoMaskDownload.Ready else AutoMaskDownload.Missing(missingBytes(readFiles)))

    /** Where what reading text needs stands. */
    val readDownload: StateFlow<AutoMaskDownload> = _readDownload.asStateFlow()
    private var readJob: Job? = null
    private var reader: TextReader? = null

    private val loading = Mutex()
    private var engine: AutoMask? = null

    /** Whether this phone can run it at all: a runtime is built for its processor. */
    val supported: Boolean get() = runtime() != null

    fun startDownload() {
        if (job?.isActive == true || _download.value == AutoMaskDownload.Ready) return
        job = scope.launch {
            _download.value = AutoMaskDownload.Running(0f)
            _download.value = try {
                fetch(files, _download)
                AutoMaskDownload.Ready
            } catch (e: Exception) {
                AutoMaskDownload.Failed(missingBytes(), e)
            }
        }
    }

    fun startReadDownload() {
        if (readJob?.isActive == true || _readDownload.value == AutoMaskDownload.Ready) return
        readJob = scope.launch {
            _readDownload.value = AutoMaskDownload.Running(0f)
            _readDownload.value = try {
                fetch(readFiles, _readDownload)
                AutoMaskDownload.Ready
            } catch (e: Exception) {
                AutoMaskDownload.Failed(missingBytes(readFiles), e)
            }
        }
    }

    /** What reads text, loaded the first time; null until it and the rest are downloaded, or when it would not load. */
    suspend fun reader(): TextReader? {
        if (engine() == null) return null
        return loading.withLock {
            reader?.let { return@withLock it }
            if (!downloaded(readFiles)) return@withLock null
            withContext(io) {
                try {
                    TextReader.load(File(folder, Recognizer), File(folder, Characters), threads = 4).also { reader = it }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    readFiles.forEach { File(folder, it.name).delete() }
                    _readDownload.value = AutoMaskDownload.Failed(missingBytes(readFiles), e)
                    null
                }
            }
        }
    }

    /**
     * The engine, its runtime and models loaded the first time; null until they are downloaded,
     * or when they would not load, which has them fetched again.
     */
    suspend fun engine(): AutoMask? = loading.withLock {
        engine?.let { return it }
        if (!downloaded()) return null
        withContext(io) {
            try {
                // Native code is only loaded from a file nothing can write to, as Android 17 asks
                val runtime = File(folder, RuntimeName).apply { setReadOnly() }
                // LiteRT finds no runtime in the APK and lets this one stand in
                System.load(runtime.absolutePath)
                AutoMask.load(folder, threads = 4).also { engine = it }
            } catch (e: Throwable) {
                if (e is CancellationException) throw e
                // Not to be loaded as it is: fetched afresh the next time
                folder.deleteRecursively()
                _readDownload.value = AutoMaskDownload.Missing(missingBytes(readFiles))
                _download.value = AutoMaskDownload.Failed(missingBytes(), e as? Exception ?: IOException(e))
                null
            }
        }
    }

    private fun downloaded(of: List<Remote> = files) = of.all { File(folder, it.name).length() == it.size }

    private fun missingBytes(of: List<Remote> = files) = of.filter { File(folder, it.name).length() != it.size }.sumOf { it.size }

    private suspend fun fetch(files: List<Remote>, state: MutableStateFlow<AutoMaskDownload>) = withContext(io) {
        folder.mkdirs()
        val wanted = files.filter { File(folder, it.name).length() != it.size }
        val total = wanted.sumOf { it.size }.coerceAtLeast(1)
        var done = 0L
        for (remote in wanted) {
            val part = File(folder, "${remote.name}.part")
            val digest = MessageDigest.getInstance("SHA-256")
            val request = Request.Builder().url("$Host/${remote.path}").build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                response.body.byteStream().use { input ->
                    part.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var shown = 0f
                        while (true) {
                            val n = input.read(buffer)
                            if (n < 0) break
                            output.write(buffer, 0, n)
                            digest.update(buffer, 0, n)
                            done += n
                            val progress = (done.toFloat() / total).coerceIn(0f, 1f)
                            if (progress - shown >= 0.005f) {
                                shown = progress
                                state.value = AutoMaskDownload.Running(progress)
                            }
                        }
                    }
                }
            }
            val sha256 = digest.digest().joinToString("") { "%02x".format(it) }
            if (!sha256.equals(remote.sha256, ignoreCase = true) || part.length() != remote.size) {
                part.delete()
                throw IOException("${remote.name} is not the file it should be")
            }
            val kept = File(folder, remote.name)
            if (!part.renameTo(kept)) throw IOException("could not keep ${remote.name}")
            kept.setReadOnly()
        }
    }

    /** The runtime built for this phone's processor, the first it prefers that one is built for. */
    private fun runtime(): Remote? {
        val abi = Build.SUPPORTED_ABIS.firstOrNull { it in Runtimes } ?: return null
        val (sha256, size) = Runtimes.getValue(abi)
        return Remote("runtime/litert/$LiteRtVersion/$abi/$RuntimeName", RuntimeName, sha256, size)
    }

    companion object {
        private const val Host = "https://oolong.huanchengfly.top"

        /** Of the models, as published; a new one goes to a new folder. */
        private const val Version = 1
        private const val LiteRtVersion = "1.4.0"

        const val Detector = "text_detector.tflite"
        const val Encoder = "sam_encoder.tflite"
        const val Decoder = "sam_decoder.tflite"
        const val Gaussian = "sam_pe_gaussian.bin"
        const val Recognizer = "text_recognizer.tflite"
        const val Characters = "text_recognizer_dict.txt"
        private const val RuntimeName = "libtensorflowlite_jni.so"

        private val Runtimes = mapOf(
            "arm64-v8a" to ("f9a6224f22f60426ae1b9d35f06ea040f2b108335932604a04c061d612f7f08f" to 4_320_480L),
            "armeabi-v7a" to ("1194c6ea953eb4903f8cdcc3c2b81f44cef3a2e6f64b85500700f6941a4f6928" to 2_867_532L),
            "x86_64" to ("b1fdd3d23e58df54f8ce47c704ed4bc0882be37e479fe86a87108d5f8153c422" to 6_053_528L),
            "x86" to ("ba824cc92711237e30fabb61cb116f0fc360d86fc2844116a0903bd637929b04" to 6_798_820L),
        )
    }
}
