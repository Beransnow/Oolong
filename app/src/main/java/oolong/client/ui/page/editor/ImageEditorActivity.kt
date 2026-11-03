package oolong.client.ui.page.editor

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.core.graphics.createBitmap
import androidx.core.view.WindowCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import oolong.client.R
import oolong.client.arch.AppContent
import oolong.client.components.images.loadBitmap
import oolong.client.repository.AccountRepository
import oolong.client.toastShort
import java.io.File
import javax.inject.Inject
import kotlin.math.roundToInt

/**
 * What [CropPicture] asks for: [source] cut to [aspect], width over height, and written to
 * [output] as a JPEG of [quality], at most [maxWidth] wide. [round] shows a round frame, for a
 * picture shown round, such as an avatar; the picture itself stays a rectangle.
 */
data class CropRequest(
    val source: Uri,
    val output: File,
    val aspect: Float,
    val round: Boolean = false,
    val maxWidth: Int = 1080,
    val quality: Int = 90,
)

/** Opens the editor to crop a picture; the result is whether it was written. */
object CropPicture : ActivityResultContract<CropRequest, Boolean>() {
    override fun createIntent(context: Context, input: CropRequest): Intent =
        Intent(context, ImageEditorActivity::class.java)
            .putExtra(ImageEditorViewModel.EXTRA_SOURCE, input.source)
            .putExtra(ImageEditorViewModel.EXTRA_OUTPUT, input.output.path)
            .putExtra(ImageEditorViewModel.EXTRA_ASPECT, input.aspect)
            .putExtra(ImageEditorViewModel.EXTRA_ROUND, input.round)
            .putExtra(ImageEditorViewModel.EXTRA_MAX_WIDTH, input.maxWidth)
            .putExtra(ImageEditorViewModel.EXTRA_QUALITY, input.quality)

    override fun parseResult(resultCode: Int, intent: Intent?): Boolean = resultCode == Activity.RESULT_OK
}

/** The picture editor. For now it crops; its tools sit along the bottom, where more can go. */
@AndroidEntryPoint
class ImageEditorActivity : AppCompatActivity() {
    @Inject
    lateinit var accountRepository: AccountRepository

    private val viewModel: ImageEditorViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            // Pictures are judged on dark, whatever the app's theme
            AppContent(this, accountRepository, dark = true) {
                val picture by viewModel.picture.collectAsStateWithLifecycle()
                var saving by remember { mutableStateOf(false) }
                val scope = rememberCoroutineScope()
                LaunchedEffect(viewModel) {
                    viewModel.unreadable.receiveAsFlow().collect {
                        toastShort(R.string.toast_image_unreadable)
                        finish()
                    }
                }
                ImageEditorScreen(
                    picture = picture,
                    aspect = viewModel.aspect,
                    round = viewModel.round,
                    saving = saving,
                    onCancel = ::finish,
                    onDone = { crop ->
                        saving = true
                        scope.launch {
                            if (viewModel.save(crop)) {
                                setResult(RESULT_OK)
                                finish()
                            } else {
                                saving = false
                                toastShort(R.string.toast_image_unreadable)
                            }
                        }
                    },
                )
            }
        }
    }
}

/** Where the picture lies in the frame: turned [turns] quarters anticlockwise, then scaled and moved. */
data class Crop(val frame: Size, val turns: Int, val scale: Float, val offset: Offset)

class ImageEditorViewModel(application: Application, savedState: SavedStateHandle) : AndroidViewModel(application) {
    private val source: Uri? = savedState[EXTRA_SOURCE]
    private val output = File(checkNotNull(savedState.get<String>(EXTRA_OUTPUT)))
    val aspect: Float = savedState[EXTRA_ASPECT] ?: 1f
    val round: Boolean = savedState[EXTRA_ROUND] ?: false
    private val maxWidth: Int = savedState[EXTRA_MAX_WIDTH] ?: 1080
    private val quality: Int = savedState[EXTRA_QUALITY] ?: 90

    private val _picture = MutableStateFlow<Bitmap?>(null)
    val picture: StateFlow<Bitmap?> = _picture.asStateFlow()

    val unreadable = Channel<Unit>(Channel.CONFLATED)

    init {
        viewModelScope.launch {
            // Turned upright by its EXIF, and no larger than a crop can use
            val bitmap = application.loadBitmap(source) { size(MaxSide) }
            if (bitmap == null) unreadable.trySend(Unit) else _picture.value = bitmap
        }
    }

    /** Writes the picture as [crop] shows it in its frame; false if it could not be written. */
    suspend fun save(crop: Crop): Boolean = withContext(Dispatchers.Default) {
        val bitmap = _picture.value ?: return@withContext false
        runCatching {
            // As many of the picture's own pixels as cross the frame, up to the widest asked for
            val width = (crop.frame.width / crop.scale).coerceAtMost(maxWidth.toFloat()).roundToInt().coerceAtLeast(1)
            val height = (width / aspect).roundToInt().coerceAtLeast(1)
            val result = createBitmap(width, height)
            Canvas(result).drawBitmap(bitmap, crop.matrix(bitmap.width, bitmap.height, width / crop.frame.width), Paint(Paint.FILTER_BITMAP_FLAG))
            output.parentFile?.mkdirs()
            output.outputStream().use { result.compress(Bitmap.CompressFormat.JPEG, quality, it) }
            result.recycle()
        }.isSuccess
    }

    companion object {
        const val EXTRA_SOURCE = "source"
        const val EXTRA_OUTPUT = "output"
        const val EXTRA_ASPECT = "aspect"
        const val EXTRA_ROUND = "round"
        const val EXTRA_MAX_WIDTH = "max_width"
        const val EXTRA_QUALITY = "quality"

        /** The longest side a picture is decoded at: enough for any crop, small enough to hold. */
        private const val MaxSide = 4096
    }
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
