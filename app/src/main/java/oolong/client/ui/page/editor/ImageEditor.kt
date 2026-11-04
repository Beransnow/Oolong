package oolong.client.ui.page.editor

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.util.lerp
import androidx.core.graphics.createBitmap
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import coil3.size.Precision
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import oolong.client.R
import oolong.client.components.images.loadBitmap
import oolong.client.toastShort
import oolong.client.ui.common.theme.compose.Motion
import oolong.client.ui.common.theme.compose.TiebaLiteTheme
import java.io.File
import kotlin.math.roundToInt

/** What the editor is asked to do with a picture: [source] edited and written to [output]. */
sealed interface EditorRequest {
    val source: Uri
    val output: File
}

/**
 * [source] cut to [aspect], width over height, and written to [output] as a JPEG of [quality], at
 * most [maxWidth] wide. [round] shows a round frame, for a picture shown round, such as an avatar;
 * the picture itself stays a rectangle.
 */
data class CropRequest(
    override val source: Uri,
    override val output: File,
    val aspect: Float,
    val round: Boolean = false,
    val maxWidth: Int = 1080,
    val quality: Int = 90,
) : EditorRequest

/** [source] edited with every tool, and written to [output] as a JPEG. */
data class EditRequest(override val source: Uri, override val output: File) : EditorRequest

/** Where the picture lies in the frame: turned [turns] quarters anticlockwise, then scaled and moved. */
data class Crop(val frame: Size, val turns: Int, val scale: Float, val offset: Offset)

/**
 * The window's picture editor, which opens over everything: [open] shows it for a request, and
 * hears whether the picture was written once it closes.
 */
@Stable
class ImageEditor {
    internal class Session(val request: EditorRequest, val onResult: (Boolean) -> Unit, val id: Long)

    internal var session by mutableStateOf<Session?>(null)
    private var opened = 0L

    fun open(request: EditorRequest, onResult: (written: Boolean) -> Unit) {
        session = Session(request, onResult, ++opened)
    }
}

/** The window's picture editor; null where there is none, as in previews and tests. */
val LocalImageEditor = staticCompositionLocalOf<ImageEditor?> { null }

/** Draws [content] with the picture editor over it, ready to open. */
@Composable
fun ImageEditorHost(content: @Composable () -> Unit) {
    val editor = remember { ImageEditor() }
    // Once the editor covers everything, what lies under it is not drawn
    var covered by remember { mutableStateOf(false) }
    CompositionLocalProvider(LocalImageEditor provides editor) {
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .drawWithContent { if (!covered) drawContent() },
            ) { content() }
            editor.session?.let { session ->
                key(session.id) {
                    EditorOverlay(
                        session = session,
                        onCovering = { covered = it },
                        onClosed = { written ->
                            covered = false
                            editor.session = null
                            session.onResult(written)
                        },
                    )
                }
            }
        }
    }
}

/** The editor for [session], coming in over the page and leaving back to it; always dark. */
@Composable
private fun EditorOverlay(session: ImageEditor.Session, onCovering: (Boolean) -> Unit, onClosed: (Boolean) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val work = remember(session) { EditorWork(context.applicationContext, session.request) }
    val shown = remember { Animatable(0f, Motion.ProgressThreshold) }
    var closing by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    val close: (Boolean) -> Unit = { written ->
        if (!closing) {
            closing = true
            onCovering(false)
            scope.launch {
                shown.animateTo(0f, Motion.dismiss())
                onClosed(written)
            }
        }
    }
    val finish: (suspend () -> Boolean) -> Unit = { write ->
        saving = true
        scope.launch {
            if (write()) {
                close(true)
            } else {
                saving = false
                context.toastShort(R.string.toast_image_unreadable)
            }
        }
    }
    LaunchedEffect(work) {
        if (!work.load()) {
            context.toastShort(R.string.toast_image_unreadable)
            close(false)
        }
    }
    LaunchedEffect(Unit) {
        shown.animateTo(1f, Motion.present())
        if (!closing) onCovering(true)
    }
    LightSystemBars()
    // Leaving mid-way closes it as cancel does; the editor's own back comes first while open
    BackHandler(enabled = closing) {}

    // Pictures are judged on dark, whatever the app's theme
    TiebaLiteTheme(dark = true) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val p = shown.value
                    alpha = p.coerceIn(0f, 1f)
                    val scale = lerp(0.94f, 1f, p)
                    scaleX = scale
                    scaleY = scale
                }
                // The page under it takes no touches while it shows
                .pointerInput(Unit) { awaitEachGesture { awaitFirstDown(requireUnconsumed = false) } },
        ) {
            val picture = work.picture
            when (val request = session.request) {
                is EditRequest -> {
                    val renderer = remember(picture) { picture?.let(::EditRenderer) }
                    if (renderer != null) {
                        EditScreen(
                            renderer = renderer,
                            initial = work.initial,
                            saving = saving,
                            onCancel = { close(false) },
                            onDone = { result -> finish { work.save(renderer, result) } },
                        )
                    }
                }

                is CropRequest -> {
                    BackHandler(enabled = !closing) { close(false) }
                    ImageEditorScreen(
                        picture = picture,
                        aspect = request.aspect,
                        round = request.round,
                        saving = saving,
                        onCancel = { close(false) },
                        onDone = { crop -> finish { work.save(crop) } },
                    )
                }
            }
        }
    }
}

/** Loading the picture a request names, and writing the result where it asks. */
@Stable
private class EditorWork(private val context: Context, private val request: EditorRequest) {
    /** What was edited before, when the picture given is one this editor made. */
    private val record: EditRecord? = (request as? EditRequest)?.source?.takeIf { it.scheme == "file" }?.path?.let { EditRecord.read(File(it)) }

    /** The picture to start from: the one an earlier edit started from, or the one given. */
    private val source: Uri = record?.source?.toUri() ?: request.source

    var picture by mutableStateOf<Bitmap?>(null)
        private set

    /** The steps taken before, to carry on from; empty for a picture not edited here. */
    var initial: List<Edit> = emptyList()
        private set

    /** Loads the picture; false if it can't be read. */
    suspend fun load(): Boolean {
        // Turned upright by its EXIF, and no larger than an edit can use
        val bitmap = context.loadBitmap(source) {
            size(MaxSide)
            // Only ever smaller, never blown up past its own pixels
            precision(Precision.INEXACT)
        } ?: return false
        initial = record?.edits(bitmap.width, bitmap.height).orEmpty()
        picture = bitmap
        return true
    }

    /** Writes the picture as [crop] shows it in its frame; false if it could not be written. */
    suspend fun save(crop: Crop): Boolean = withContext(Dispatchers.Default) {
        val request = request as CropRequest
        val bitmap = picture ?: return@withContext false
        runCatching {
            // As many of the picture's own pixels as cross the frame, up to the widest asked for
            val width = (crop.frame.width / crop.scale).coerceAtMost(request.maxWidth.toFloat()).roundToInt().coerceAtLeast(1)
            val height = (width / request.aspect).roundToInt().coerceAtLeast(1)
            val result = createBitmap(width, height)
            Canvas(result).drawBitmap(bitmap, crop.matrix(bitmap.width, bitmap.height, width / crop.frame.width), Paint(Paint.FILTER_BITMAP_FLAG))
            request.output.parentFile?.mkdirs()
            request.output.outputStream().use { result.compress(Bitmap.CompressFormat.JPEG, request.quality, it) }
            result.recycle()
        }.isSuccess
    }

    /** Writes the picture as [result] leaves it; false if it could not be written. */
    suspend fun save(renderer: EditRenderer, result: EditResult): Boolean = withContext(Dispatchers.Default) {
        runCatching {
            val bitmap = renderer.render(result.framing, result.strokes, result.captions, MaxSide)
            request.output.parentFile?.mkdirs()
            request.output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, EditQuality, it) }
            bitmap.recycle()
            EditRecord.write(request.output, source.toString(), renderer.picture.width, renderer.picture.height, result.steps)
        }.isSuccess
    }

    companion object {
        /** The longest side a picture is decoded at: enough for any crop, small enough to hold. */
        private const val MaxSide = 4096

        /** The JPEG quality an edited picture is written at. */
        private const val EditQuality = 95
    }
}

/** Light status and navigation bar icons over the black, while the editor shows. */
@Composable
private fun LightSystemBars() {
    val view = LocalView.current
    val activity = LocalContext.current.findActivity() ?: return
    DisposableEffect(activity) {
        val controller = WindowCompat.getInsetsController(activity.window, view)
        val status = controller.isAppearanceLightStatusBars
        val navigation = controller.isAppearanceLightNavigationBars
        controller.isAppearanceLightStatusBars = false
        controller.isAppearanceLightNavigationBars = false
        onDispose {
            controller.isAppearanceLightStatusBars = status
            controller.isAppearanceLightNavigationBars = navigation
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * The picture of [width] by [height] as [Crop] lays it in its frame, the frame then scaled by
 * [outScale]: the same steps the screen draws it with.
 */
internal fun Crop.matrix(width: Int, height: Int, outScale: Float = 1f): Matrix = Matrix().apply {
    postTranslate(-width / 2f, -height / 2f)
    postRotate(-90f * turns)
    postScale(scale, scale)
    postTranslate(frame.width / 2 + offset.x, frame.height / 2 + offset.y)
    postScale(outScale, outScale)
}
