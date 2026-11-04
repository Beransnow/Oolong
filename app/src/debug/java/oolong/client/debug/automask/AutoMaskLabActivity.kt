package oolong.client.debug.automask

import android.graphics.Bitmap
import android.graphics.RectF
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.size.Precision
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import oolong.client.components.images.loadBitmap

/**
 * A lab for tap-to-hide: pick a screenshot, see how long finding its text and making it ready
 * take on this phone, then tap it to see what would be hidden and how long each tap takes.
 * Blue is a line of text, red what MobileSAM made out.
 */
class AutoMaskLabActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { Lab() }
    }
}

private class Hit(val box: RectF, val text: Boolean)

@Composable
private fun Lab() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var threads by remember { mutableIntStateOf(4) }
    var engine by remember { mutableStateOf<AutoMask?>(null) }
    var picture by remember { mutableStateOf<Bitmap?>(null) }
    var prepared by remember { mutableStateOf<AutoMask.Prepared?>(null) }
    var status by remember { mutableStateOf("选一张截图开始。") }
    var showLines by remember { mutableStateOf(false) }
    val hits = remember { mutableStateListOf<Hit>() }
    val tapTimes = remember { mutableStateListOf<Long>() }

    fun run(bitmap: Bitmap) {
        prepared = null
        hits.clear()
        tapTimes.clear()
        scope.launch {
            status = "处理中……"
            val result = withContext(Dispatchers.Default) {
                runCatching {
                    val t0 = System.nanoTime()
                    val loaded = engine ?: AutoMask.load(context, threads)
                    val loadMillis = (System.nanoTime() - t0) / 1_000_000
                    engine = loaded
                    loaded.prepare(bitmap) to loadMillis
                }
            }
            result.onSuccess { (p, load) ->
                prepared = p
                status = "${p.width}×${p.height}，$threads 线程\n" +
                    (if (load > 0) "载入模型 $load ms\n" else "") +
                    "文字检测 ${p.detectMillis} ms（${p.lines.size} 行）\n图像编码 ${p.encodeMillis} ms"
            }.onFailure { status = "出错：${it.javaClass.simpleName} ${it.message}" }
        }
    }

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val bitmap = context.loadBitmap(uri) {
                size(4096)
                // Only ever smaller, never blown up past its own pixels
                precision(Precision.INEXACT)
            } ?: return@launch
            picture = bitmap.copy(Bitmap.Config.ARGB_8888, false)
            run(picture!!)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF16181C))
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) { Text("选择截图") }
            Button(onClick = {
                threads = if (threads == 4) 2 else if (threads == 2) 1 else 4
                engine = null
                picture?.let(::run)
            }) { Text("$threads 线程") }
            Button(onClick = { showLines = !showLines }) { Text(if (showLines) "隐藏文字框" else "显示文字框") }
        }
        Text(status, color = Color.White, fontSize = 14.sp)
        if (tapTimes.isNotEmpty()) {
            Text("点击 ${tapTimes.size} 次，平均 ${tapTimes.average().toInt()} ms，最近 ${tapTimes.last()} ms", color = Color.White, fontSize = 14.sp)
        }
        val bitmap = picture
        if (bitmap != null) {
            val image = remember(bitmap) { bitmap.asImageBitmap() }
            Box(modifier = Modifier.fillMaxWidth().aspectRatio(bitmap.width.toFloat() / bitmap.height)) {
                Image(bitmap = image, contentDescription = null, contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize())
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(prepared) {
                            val p = prepared ?: return@pointerInput
                            detectTapGestures { at ->
                                val k = bitmap.width / size.width.toFloat()
                                scope.launch {
                                    val found = withContext(Dispatchers.Default) { engine?.tap(p, at.x * k, at.y * k) } ?: return@launch
                                    tapTimes += found.millis
                                    found.box?.let { hits += Hit(it, found.text) }
                                }
                            }
                        },
                ) {
                    val k = size.width / bitmap.width
                    fun box(r: RectF, color: Color, width: Float) = drawRect(
                        color = color,
                        topLeft = Offset(r.left * k, r.top * k),
                        size = Size(r.width() * k, r.height() * k),
                        style = Stroke(width),
                    )
                    if (showLines) prepared?.lines?.forEach { box(it, Color(0x8800C8FF), 1.dp.toPx()) }
                    hits.forEach { box(it.box, if (it.text) Color(0xFF2F6BFF) else Color(0xFFF03030), 2.5.dp.toPx()) }
                }
            }
        }
    }
}
