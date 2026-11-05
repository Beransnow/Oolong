package oolong.client.components.automask

import android.content.Context
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
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

/** What the downloaded models can do, each downloaded and deleted on its own; what uses it needs all it lacks. */
enum class Ability {
    /** Finding the lines of text in a picture and reading them. */
    Text,

    /** Finding the whole of the thing at a point. */
    Region,
}

/**
 * The models and the LiteRT runtime the picture tools run on the phone: too large for options
 * few use, so fetched the first time one is, from the release host, an [Ability] at a time, the
 * runtime with whichever comes first and gone with the last. Each file is checked against the
 * hash it is pinned to here before it is kept; the runtime is native code, so nothing else may
 * stand in.
 */
@Singleton
class AutoMaskFiles @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val io: CoroutineDispatcher,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private class Remote(val path: String, val name: String, val sha256: String, val size: Long)

    private val folder = File(context.filesDir, "automask/$Version")

    private val runtime: Remote? = runtime()

    private val abilityFiles: Map<Ability, List<Remote>> = mapOf(
        Ability.Text to listOf(
            Remote("models/automask/$Version/$Detector", Detector, "ed1a220aa9c52642c84075cd0d9e9923a0bb1985a1ea9e23d21b0bd8ba1fea55", 5_115_728),
            Remote("models/automask/$Version/$Recognizer", Recognizer, "04cdb1ee330967b475b7a7ecc9c1c3528b4bd2ef3eb87e7660520224cd91af24", 10_681_428),
            Remote("models/automask/$Version/$Characters", Characters, "b5f2bfe2bdd9448429e3e82b51c789775d9b42f2403d082b00662eb77e401c5d", 74_947),
        ),
        Ability.Region to listOf(
            Remote("models/automask/$Version/$Encoder", Encoder, "1089090d59f9e6d159c84415c23fab2b97ae73b99b9453f50092d5c2887c1007", 14_244_036),
            Remote("models/automask/$Version/$Decoder", Decoder, "e2f4f85c7243b6debd290717796eace1793305e3683fc962402d403014de870b", 8_277_936),
            Remote("models/automask/$Version/$Gaussian", Gaussian, "9bf5b8dcada0e312a19b3a4c98a913f44df480d6aa72aedc2ea4558f979beff4", 1_024),
        ),
    )

    /** What [abilities] need on the phone: their own files and the runtime. */
    private fun needs(abilities: Collection<Ability>): List<Remote> = abilities.flatMap { abilityFiles.getValue(it) } + listOfNotNull(runtime)

    // Its own client, as the updater's: the app's carry Tieba's cookies
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    init {
        // Files of models since replaced, left from before: nothing reads them any more
        scope.launch(io) {
            val known = (abilityFiles.values.flatten() + listOfNotNull(runtime)).map { it.name }.toSet()
            folder.listFiles()?.filter { it.isFile && it.name !in known && !it.name.endsWith(".part") }?.forEach { it.delete() }
        }
    }

    private val states: Map<Ability, MutableStateFlow<AutoMaskDownload>> = Ability.entries.associateWith { MutableStateFlow(settled(setOf(it))) }

    /** Where [ability] stands: what fetching it would take, counting the runtime if that is missing too. */
    fun state(ability: Ability): StateFlow<AutoMaskDownload> = states.getValue(ability).asStateFlow()

    /** Where all of [abilities] stand together. */
    fun state(abilities: Set<Ability>): StateFlow<AutoMaskDownload> {
        val flows = abilities.map { states.getValue(it) }
        return combine(flows) { together(abilities) }.stateIn(scope, SharingStarted.Eagerly, together(abilities))
    }

    private fun together(abilities: Set<Ability>): AutoMaskDownload {
        val each = abilities.map { states.getValue(it).value }
        return when {
            each.all { it == AutoMaskDownload.Ready } -> AutoMaskDownload.Ready
            each.any { it is AutoMaskDownload.Running } -> AutoMaskDownload.Running(each.filterIsInstance<AutoMaskDownload.Running>().minOf { it.progress })
            else -> {
                val failed = each.filterIsInstance<AutoMaskDownload.Failed>().firstOrNull()
                if (failed != null) AutoMaskDownload.Failed(missingBytes(needs(abilities)), failed.error) else AutoMaskDownload.Missing(missingBytes(needs(abilities)))
            }
        }
    }

    /** What tap-to-hide needs: the lines of text, and the things around them. */
    val download: StateFlow<AutoMaskDownload> = state(setOf(Ability.Text, Ability.Region))

    /** What reading text needs. */
    val readDownload: StateFlow<AutoMaskDownload> = state(Ability.Text)

    private val fetching = Mutex()
    private val jobs = mutableMapOf<Ability, Job>()

    private val loading = Mutex()
    private var runtimeLoaded = false
    private var engine: AutoMask? = null
    private var finder: TextFinder? = null
    private var reader: TextReader? = null

    /** Whether this phone can run them at all: a runtime is built for its processor. */
    val supported: Boolean get() = runtime != null

    /** Fetches what tap-to-hide needs. */
    fun startDownload() = start(Ability.Text, Ability.Region)

    /** Fetches what reading text needs. */
    fun startReadDownload() = start(Ability.Text)

    /** Fetches whichever of [abilities] are not here yet, together, one download at a time. */
    @Synchronized
    fun start(vararg abilities: Ability) {
        val wanted = abilities.filter { jobs[it]?.isActive != true && states.getValue(it).value != AutoMaskDownload.Ready }
        if (wanted.isEmpty()) return
        wanted.forEach { states.getValue(it).value = AutoMaskDownload.Running(0f) }
        val job = scope.launch {
            try {
                fetching.withLock { fetch(needs(wanted)) { progress -> wanted.forEach { states.getValue(it).value = AutoMaskDownload.Running(progress) } } }
                wanted.forEach { states.getValue(it).value = settled(setOf(it)) }
            } catch (e: Exception) {
                wanted.forEach { states.getValue(it).value = AutoMaskDownload.Failed(missingBytes(needs(setOf(it))), e) }
            }
        }
        wanted.forEach { jobs[it] = job }
    }

    /**
     * Deletes [ability]'s files, and the runtime with the last of them. What was loaded is let go
     * rather than closed, as something may still be using it; it is gone with the process.
     */
    suspend fun delete(ability: Ability) {
        synchronized(this) { jobs[ability] }?.let { if (it.isActive) return }
        loading.withLock {
            withContext(io) {
                fetching.withLock {
                    engine = null
                    if (ability == Ability.Text) {
                        finder = null
                        reader = null
                    }
                    abilityFiles.getValue(ability).forEach { File(folder, it.name).delete() }
                    val anyLeft = abilityFiles.values.flatten().any { File(folder, it.name).exists() }
                    if (!anyLeft) runtime?.let { File(folder, it.name).delete() }
                    Ability.entries.forEach { states.getValue(it).value = settled(setOf(it)) }
                }
            }
        }
    }

    /** How much of the phone [ability]'s own files take now. */
    fun bytesOf(ability: Ability): Long = abilityFiles.getValue(ability).sumOf { File(folder, it.name).length() }

    /** How much [ability]'s own files take once downloaded. */
    fun sizeOf(ability: Ability): Long = abilityFiles.getValue(ability).sumOf { it.size }

    /** How much of the phone the runtime takes now. */
    val runtimeBytes: Long get() = runtime?.let { File(folder, it.name).length() } ?: 0

    /** What finds the lines of text, loaded the first time; null until it is downloaded, or when it would not load. */
    suspend fun finder(): TextFinder? = loading.withLock { loadFinder() }

    private suspend fun loadFinder(): TextFinder? {
        finder?.let { return it }
        if (!downloaded(needs(setOf(Ability.Text))) || !loadRuntime()) return null
        return withContext(io) {
            try {
                TextFinder.load(File(folder, Detector), threads = 4).also { finder = it }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                unusable(Ability.Text, e)
                null
            }
        }
    }

    /** What reads text, loaded the first time; null until it is downloaded, or when it would not load. */
    suspend fun reader(): TextReader? = loading.withLock {
        reader?.let { return it }
        if (!downloaded(needs(setOf(Ability.Text))) || !loadRuntime()) return null
        withContext(io) {
            try {
                TextReader.load(File(folder, Recognizer), File(folder, Characters), threads = 4).also { reader = it }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                unusable(Ability.Text, e)
                null
            }
        }
    }

    /**
     * The engine, its models loaded the first time; null until they are downloaded, or when they
     * would not load, which has them fetched again.
     */
    suspend fun engine(): AutoMask? = loading.withLock {
        engine?.let { return it }
        if (!downloaded(needs(Ability.entries)) || !loadRuntime()) return null
        val finder = loadFinder() ?: return null
        withContext(io) {
            try {
                AutoMask.load(folder, finder, threads = 4).also { engine = it }
            } catch (e: Throwable) {
                if (e is CancellationException) throw e
                unusable(Ability.Region, e)
                null
            }
        }
    }

    /** Loads the runtime, once; false when it would not load, which has it fetched again. */
    private suspend fun loadRuntime(): Boolean {
        if (runtimeLoaded) return true
        val remote = runtime ?: return false
        return withContext(io) {
            try {
                // Native code is only loaded from a file nothing can write to, as Android 17 asks
                val file = File(folder, remote.name).apply { setReadOnly() }
                // LiteRT finds no runtime in the APK and lets this one stand in
                System.load(file.absolutePath)
                runtimeLoaded = true
                true
            } catch (e: Throwable) {
                if (e is CancellationException) throw e
                File(folder, remote.name).delete()
                Ability.entries.forEach { states.getValue(it).value = AutoMaskDownload.Failed(missingBytes(needs(setOf(it))), e as? Exception ?: IOException(e)) }
                false
            }
        }
    }

    /** [ability]'s files would not load as they are: fetched afresh the next time. */
    private fun unusable(ability: Ability, e: Throwable) {
        abilityFiles.getValue(ability).forEach { File(folder, it.name).delete() }
        states.getValue(ability).value = AutoMaskDownload.Failed(missingBytes(needs(setOf(ability))), e as? Exception ?: IOException(e))
    }

    private fun settled(abilities: Set<Ability>): AutoMaskDownload {
        val files = needs(abilities)
        return if (downloaded(files)) AutoMaskDownload.Ready else AutoMaskDownload.Missing(missingBytes(files))
    }

    private fun downloaded(files: List<Remote>) = files.all { File(folder, it.name).length() == it.size }

    private fun missingBytes(files: List<Remote>) = files.filter { File(folder, it.name).length() != it.size }.sumOf { it.size }

    private suspend fun fetch(files: List<Remote>, progress: (Float) -> Unit) = withContext(io) {
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
                            val fraction = (done.toFloat() / total).coerceIn(0f, 1f)
                            if (fraction - shown >= 0.005f) {
                                shown = fraction
                                progress(fraction)
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

        // PP-OCRv6's: reads about twice as well as the v5 one before it, whose files go
        const val Recognizer = "text_recognizer_v6.tflite"
        const val Characters = "text_recognizer_v6_dict.txt"
        private const val RuntimeName = "libtensorflowlite_jni.so"

        private val Runtimes = mapOf(
            "arm64-v8a" to ("f9a6224f22f60426ae1b9d35f06ea040f2b108335932604a04c061d612f7f08f" to 4_320_480L),
            "armeabi-v7a" to ("1194c6ea953eb4903f8cdcc3c2b81f44cef3a2e6f64b85500700f6941a4f6928" to 2_867_532L),
            "x86_64" to ("b1fdd3d23e58df54f8ce47c704ed4bc0882be37e479fe86a87108d5f8153c422" to 6_053_528L),
            "x86" to ("ba824cc92711237e30fabb61cb116f0fc360d86fc2844116a0903bd637929b04" to 6_798_820L),
        )
    }
}
