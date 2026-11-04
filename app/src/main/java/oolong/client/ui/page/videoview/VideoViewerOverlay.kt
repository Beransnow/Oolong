package oolong.client.ui.page.videoview

import android.app.Activity
import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.drawable.Icon
import android.os.Build
import android.util.Rational
import android.view.TextureView
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.PictureInPictureModeChangedInfo
import androidx.core.content.ContextCompat
import androidx.core.util.Consumer
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource
import coil3.compose.AsyncImage
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import oolong.client.R
import oolong.client.findActivity
import oolong.client.ui.common.LocalAccount
import oolong.client.ui.common.theme.compose.Motion
import oolong.client.ui.common.theme.compose.SmoothCornerShape
import oolong.client.ui.common.theme.compose.Symbols
import oolong.client.ui.page.photoview.LightSystemBars
import oolong.client.ui.page.photoview.offsetTo
import oolong.client.ui.utils.FullColorWindow
import oolong.client.ui.widgets.compose.ContextMenuItem
import oolong.client.ui.widgets.compose.IconButton
import oolong.client.ui.widgets.compose.LocalContextMenus
import oolong.client.ui.widgets.compose.MediaGlassActions
import oolong.client.ui.widgets.compose.MediaGlassIconButton
import oolong.client.ui.widgets.compose.MediaGlassPill
import oolong.client.ui.widgets.compose.ProvideMediaGlass
import oolong.client.ui.widgets.compose.WavyLoader
import oolong.client.utils.TiebaUtil
import oolong.client.utils.appPreferences
import kotlin.math.abs
import kotlin.math.roundToInt

private enum class Phase { Opening, Open, Closing }

/** What a finger on the video turned out to be doing. */
private enum class Gesture { Tap, Hold, Seek, Dismiss, None }

/** The player's state, as composition reads it. */
@Stable
private class Playback(player: Player) : Player.Listener {
    var playWhenReady by mutableStateOf(player.playWhenReady)
    var state by mutableIntStateOf(player.playbackState)
    var error by mutableStateOf(player.playerError)
    var firstFrame by mutableStateOf(false)

    /** Height over width of the picture, once it is known. */
    var aspect by mutableFloatStateOf(0f)

    val ended get() = state == Player.STATE_ENDED

    override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
        this.playWhenReady = playWhenReady
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        state = playbackState
    }

    override fun onPlayerErrorChanged(error: PlaybackException?) {
        this.error = error
    }

    override fun onRenderedFirstFrame() {
        firstFrame = true
    }

    override fun onVideoSizeChanged(videoSize: VideoSize) {
        if (videoSize.width > 0 && videoSize.height > 0) {
            aspect = videoSize.height / (videoSize.width * videoSize.pixelWidthHeightRatio)
        }
    }
}

/**
 * The video of [opening], over the whole window. It grows out of its cover, plays with sound, and
 * closes on the close button, the back button or a drag down, shrinking back into its cover. A tap
 * shows or hides the controls, a double tap pauses, holding plays at double speed and a sideways
 * drag seeks.
 */
@OptIn(UnstableApi::class)
@Composable
internal fun VideoViewerOverlay(viewer: VideoViewer, opening: VideoOpening, origin: Offset) {
    val player = viewer.player ?: return
    val video = opening.video
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val playback = remember(player) { Playback(player) }
    DisposableEffect(player) {
        player.addListener(playback)
        onDispose { player.removeListener(playback) }
    }

    LightSystemBars()
    FullColorWindow()

    // Turning sideways is the viewer's own, and ends with it
    val activity = remember(context) { context.findActivity() }
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val requested = remember { activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
    DisposableEffect(activity) {
        onDispose { activity?.requestedOrientation = requested }
    }
    val view = LocalView.current
    DisposableEffect(landscape, activity) {
        val window = activity?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        if (landscape && controller != null) {
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        }
        onDispose { controller?.show(WindowInsetsCompat.Type.systemBars()) }
    }

    val autoPip = remember { context.appPreferences.videoAutoPip }
    // Shrunk into a window of its own, the viewer shows only the video
    var inPip by remember { mutableStateOf(activity?.isInPictureInPictureMode == true) }
    DisposableEffect(activity) {
        val host = activity as? ComponentActivity
        val listener = Consumer<PictureInPictureModeChangedInfo> { inPip = it.isInPictureInPictureMode }
        host?.addOnPictureInPictureModeChangedListener(listener)
        onDispose { host?.removeOnPictureInPictureModeChangedListener(listener) }
    }

    var position by remember { mutableLongStateOf(player.currentPosition) }
    var buffered by remember { mutableLongStateOf(player.bufferedPosition) }
    var duration by remember { mutableLongStateOf(video.duration * 1000L) }
    LaunchedEffect(player) {
        while (true) {
            position = player.currentPosition
            buffered = player.bufferedPosition
            player.duration.takeIf { it != C.TIME_UNSET && it > 0 }?.let { duration = it }
            delay(200)
        }
    }

    // Each video starts at normal speed; holding plays it at double for as long as the finger stays
    var speed by remember { mutableFloatStateOf(1f) }
    var fast by remember { mutableStateOf(false) }
    LaunchedEffect(speed, fast) { player.setPlaybackSpeed(if (fast) 2f else speed) }

    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        player.pause()
        viewer.leave(video.key, player.currentPosition, duration)
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val container = Size(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat())
        val aspect = playback.aspect.takeIf { it > 0f } ?: video.aspect.takeIf { it > 0f } ?: (9f / 16)
        val fitted = fitRect(container, aspect)

        val progress = remember { Animatable(0f) }
        var phase by remember { mutableStateOf(Phase.Opening) }
        var near by remember { mutableStateOf(opening.from?.translate(-origin)) }
        var far by remember { mutableStateOf<Rect?>(null) }
        var drag by remember { mutableStateOf(Offset.Zero) }
        var dragging by remember { mutableStateOf(false) }
        var closingFade by remember { mutableFloatStateOf(1f) }
        val dragScale = 1f - (drag.y.coerceAtLeast(0f) / container.height).coerceIn(0f, 1f) * 0.45f
        val dragFade = 1f - (abs(drag.y) / (container.height * 0.5f)).coerceIn(0f, 1f)

        // Where a seek is headed while a finger drags it, in milliseconds
        var seekTarget by remember { mutableStateOf<Long?>(null) }
        var chrome by remember { mutableStateOf(true) }
        var touched by remember { mutableIntStateOf(0) }
        val menus = LocalContextMenus.current
        var speedMenu by remember { mutableStateOf<Long?>(null) }
        val sticky = !playback.playWhenReady || playback.ended || playback.error != null ||
            seekTarget != null || menus?.isShowing(speedMenu) == true
        val currentSticky by rememberUpdatedState(sticky)
        // Coming out of a pause, an end or a seek, the controls stay a while before hiding
        LaunchedEffect(sticky) {
            if (!sticky) chrome = true
        }
        LaunchedEffect(chrome, sticky, touched, phase) {
            if (chrome && !sticky && phase == Phase.Open) {
                delay(ChromeTimeout)
                chrome = false
            }
        }

        LaunchedEffect(Unit) {
            progress.animateTo(1f, Motion.present(Motion.ProgressThreshold))
            phase = Phase.Open
        }

        fun close() {
            if (phase == Phase.Closing) return
            viewer.leave(video.key, player.currentPosition, duration)
            player.pause()
            far = fitted.dragged(drag, dragScale, container)
            // Turned sideways, the cover is no longer where it was: fade out in place instead
            near = viewer.thumbnails[video.key]?.takeIf { it.visible && !landscape }?.bounds?.translate(-origin)
            closingFade = dragFade
            drag = Offset.Zero
            dragging = false
            phase = Phase.Closing
            scope.launch {
                progress.snapTo(1f)
                progress.animateTo(0f, Motion.dismiss(Motion.ProgressThreshold))
                viewer.closed()
            }
        }

        fun setLandscape(on: Boolean) {
            activity?.requestedOrientation =
                if (on) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }

        fun togglePlaying() {
            touched++
            when {
                playback.error != null -> return
                playback.ended -> {
                    player.seekTo(0)
                    player.play()
                }

                player.playWhenReady -> player.pause()
                else -> player.play()
            }
        }

        fun retry() {
            player.prepare()
            player.play()
        }

        val onClose by rememberUpdatedState<() -> Unit> { close() }
        BackHandler(enabled = phase != Phase.Closing) {
            if (landscape) setLandscape(false) else close()
        }

        val enterPip = pictureInPicture(
            activity = activity,
            // Leaving takes the video into a window only when the user wants that; the button always does
            enabled = autoPip && phase == Phase.Open && playback.playWhenReady && !playback.ended && playback.error == null,
            playing = playback.playWhenReady && !playback.ended,
            aspect = aspect,
            bounds = fitted.translate(origin),
            onToggle = { togglePlaying() },
        )

        val p = progress.value
        val black = if (phase == Phase.Closing) p * closingFade else p.coerceIn(0f, 1f) * dragFade
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = black }
                .background(Color.Black)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        )

        // The video, flying between its cover (near; null to fade in place) and where it plays
        val open = fitted.dragged(drag, dragScale, container)
        val target = if (phase == Phase.Closing) far ?: open else open
        val rect = near?.let { lerp(it, target, p) } ?: target.scaledAround(lerp(0.9f, 1f, p))
        val corner = if (near != null) lerp(8f, 0f, p.coerceIn(0f, 1f)) else 0f
        val coverAlpha by animateFloatAsState(
            targetValue = if (playback.firstFrame) 0f else 1f,
            animationSpec = Motion.fadeOut(),
            label = "cover",
        )
        val glass = rememberHazeState()
        Box(
            modifier = Modifier
                .offsetTo(rect)
                // What the controls' glass shows through
                .hazeSource(glass)
                .graphicsLayer {
                    alpha = if (near == null) p.coerceIn(0f, 1f) else 1f
                    shape = SmoothCornerShape(corner.dp)
                    clip = true
                },
        ) {
            Box(modifier = Modifier.covering(aspect)) {
                AndroidView(
                    factory = { TextureView(it).also { view -> player.setVideoTextureView(view) } },
                    // The player may be gone by now, released as the viewer closed
                    onRelease = { runCatching { player.clearVideoTextureView(it) } },
                    modifier = Modifier.fillMaxSize(),
                )
                if (coverAlpha > 0f) {
                    AsyncImage(
                        model = video.cover,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer { alpha = coverAlpha },
                    )
                }
            }
        }

        if (phase == Phase.Open && (playback.ended || playback.error != null)) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)))
        }

        // Fingers on the video
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    val slop = viewConfiguration.touchSlop
                    val doubleTap = viewConfiguration.doubleTapTimeoutMillis
                    var lastTap = 0L
                    var pendingTap: Job? = null
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        if (phase != Phase.Open || inPip) return@awaitEachGesture
                        val tracker = VelocityTracker()
                        tracker.addPosition(down.uptimeMillis, down.position)
                        var total = Offset.Zero
                        var gesture = Gesture.Tap
                        // Until it moves or lifts, a finger held still becomes a hold
                        val decided = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                            while (true) {
                                val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id }
                                if (change == null || !change.pressed) break
                                total += change.positionChange()
                                tracker.addPosition(change.uptimeMillis, change.position)
                                if (total.getDistance() > slop) {
                                    gesture = when {
                                        abs(total.x) > abs(total.y) -> Gesture.Seek
                                        total.y > 0 -> Gesture.Dismiss
                                        else -> Gesture.None
                                    }
                                    change.consume()
                                    break
                                }
                            }
                        }
                        if (decided == null) gesture = Gesture.Hold
                        when (gesture) {
                            Gesture.Tap -> {
                                if (down.uptimeMillis - lastTap < doubleTap && pendingTap?.isActive == true) {
                                    pendingTap?.cancel()
                                    lastTap = 0L
                                    togglePlaying()
                                } else {
                                    lastTap = down.uptimeMillis
                                    pendingTap = scope.launch {
                                        delay(doubleTap)
                                        chrome = !(chrome || currentSticky)
                                        touched++
                                    }
                                }
                            }

                            Gesture.Hold -> {
                                if (player.playWhenReady && !playback.ended) {
                                    fast = true
                                    while (true) {
                                        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id }
                                        if (change == null || !change.pressed) break
                                        change.consume()
                                    }
                                    fast = false
                                }
                            }

                            Gesture.Seek -> {
                                val start = player.currentPosition
                                val length = duration.coerceAtLeast(1L)
                                // The width spans the whole of a short video, a minute and a half of a long one
                                val span = minOf(length, 90_000L).toFloat()
                                seekTarget = start
                                while (true) {
                                    val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id }
                                    if (change == null || !change.pressed) break
                                    total += change.positionChange()
                                    change.consume()
                                    seekTarget = (start + total.x / size.width * span).toLong().coerceIn(0L, length)
                                }
                                seekTarget?.let { player.seekTo(it) }
                                seekTarget = null
                                touched++
                            }

                            Gesture.Dismiss -> {
                                dragging = true
                                drag = total
                                while (true) {
                                    val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id }
                                    if (change == null || !change.pressed) break
                                    change.consume()
                                    drag += change.positionChange()
                                    tracker.addPosition(change.uptimeMillis, change.position)
                                }
                                val velocity = tracker.calculateVelocity()
                                if (drag.y > 96.dp.toPx() || velocity.y > 900.dp.toPx()) {
                                    onClose()
                                } else {
                                    scope.launch {
                                        Animatable(drag, Offset.VectorConverter).animateTo(Offset.Zero, Motion.settle()) { drag = value }
                                        dragging = false
                                    }
                                }
                            }

                            Gesture.None -> Unit
                        }
                    }
                },
        )

        val showing = phase == Phase.Open && !dragging && !fast && !inPip && (chrome || sticky)
        val chromeAlpha by animateFloatAsState(
            targetValue = if (showing) 1f else 0f,
            animationSpec = if (showing) Motion.fadeIn() else Motion.fadeOut(),
            label = "chrome",
        )

        ProvideMediaGlass(glass) {
            // The middle: where a seek is going, or why the video is not playing
            if (phase == Phase.Open && !dragging && !inPip) {
                var loading by remember { mutableStateOf(false) }
                val waiting = playback.error == null && playback.playWhenReady &&
                    (playback.state == Player.STATE_BUFFERING || !playback.firstFrame)
                LaunchedEffect(waiting) {
                    loading = false
                    if (waiting) {
                        delay(LoaderDelay)
                        loading = true
                    }
                }
                val error = playback.error
                val signedIn = LocalAccount.current != null
                Box(modifier = Modifier.align(Alignment.Center)) {
                    when {
                        seekTarget != null -> SeekTime(seekTarget ?: 0L, duration)
                        error != null -> Failure(error.message(signedIn), onRetry = ::retry)
                        playback.ended -> BigButton(Symbols.ReplayFill, stringResource(id = R.string.btn_play)) { togglePlaying() }
                        !playback.playWhenReady -> BigButton(Symbols.PlayArrowFill, stringResource(id = R.string.btn_play)) { togglePlaying() }
                        loading -> WavyLoader(color = Color.White, modifier = Modifier.size(44.dp))
                    }
                }
            }

            if (fast && phase == Phase.Open) {
                MediaGlassPill(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .statusBarsPadding()
                        .padding(top = 48.dp),
                ) {
                    Icon(
                        imageVector = Symbols.FastForwardFill,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.padding(start = 14.dp).size(16.dp),
                    )
                    Text(text = "2×", color = Color.White, style = TimeStyle.copy(fontSize = 13.sp), modifier = Modifier.padding(end = 14.dp, top = 6.dp, bottom = 6.dp))
                }
            }

            if (phase == Phase.Open && !dragging && !inPip && chromeAlpha < 1f) {
                ProgressLine(
                    fraction = if (duration > 0) position.toFloat() / duration else 0f,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(bottom = 6.dp)
                        .graphicsLayer { alpha = 1f - chromeAlpha },
                )
            }

            if (chromeAlpha > 0f && phase != Phase.Closing) {
                VideoChrome(
                    video = video,
                    landscape = landscape,
                    playing = playback.playWhenReady && !playback.ended,
                    ended = playback.ended,
                    position = seekTarget ?: position,
                    buffered = buffered,
                    duration = duration,
                    speed = speed,
                    onClose = { close() },
                    onTogglePlaying = { togglePlaying() },
                    onSeek = { seekTarget = it },
                    onSeekEnd = {
                        player.seekTo(it)
                        seekTarget = null
                        touched++
                    },
                    onSpeed = { anchor ->
                        touched++
                        speedMenu = menus?.show(
                            items = Speeds.map { value ->
                                ContextMenuItem(icon = null, label = speedLabel(value), selected = value == speed) { speed = value }
                            },
                            anchor = anchor,
                        )
                    },
                    onLandscape = { setLandscape(!landscape) },
                    onCopyLink = { TiebaUtil.copyText(context, video.url) },
                    onShare = {
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, video.url)
                        }
                        // Not a leaving of the app, which would shrink the video into its own window
                        val chooser = Intent.createChooser(intent, context.getString(R.string.title_share))
                            .addFlags(Intent.FLAG_ACTIVITY_NO_USER_ACTION)
                        runCatching { context.startActivity(chooser) }
                    },
                    onPip = enterPip,
                    saving = viewer.saving[video.key],
                    onSave = { viewer.save(context, video) },
                    modifier = Modifier.graphicsLayer { alpha = chromeAlpha },
                )
            }
        }
    }
}

/**
 * Lets the video shrink into a picture-in-picture window, with a play and pause button, when the
 * user leaves the app while it plays: by itself from Android 12, on leaving before that.
 *
 * @return what shrinks it there at once, or null where the device has no such windows
 */
@Composable
private fun pictureInPicture(
    activity: Activity?,
    enabled: Boolean,
    playing: Boolean,
    aspect: Float,
    bounds: Rect,
    onToggle: () -> Unit,
): (() -> Unit)? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || activity !is ComponentActivity) return null
    val context = LocalContext.current
    val supported = remember(context) { context.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE) }
    if (!supported) return null
    val toggle by rememberUpdatedState(onToggle)
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) = toggle()
        }
        ContextCompat.registerReceiver(context, receiver, IntentFilter(PipToggle), ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose { context.unregisterReceiver(receiver) }
    }
    val playLabel = stringResource(id = R.string.btn_play)
    val pauseLabel = stringResource(id = R.string.btn_pause)
    val params = remember(enabled, playing, aspect, bounds) {
        val intent = PendingIntent.getBroadcast(
            context,
            0,
            Intent(PipToggle).setPackage(context.packageName),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val label = if (playing) pauseLabel else playLabel
        val icon = Icon.createWithResource(context, if (playing) R.drawable.symbol_pause_fill else R.drawable.symbol_play_arrow_fill)
        // The window takes ratios between 1:2.39 and 2.39:1 only
        val ratio = (1f / aspect).coerceIn(1f / 2.39f, 2.39f)
        PictureInPictureParams.Builder()
            .setAspectRatio(Rational((ratio * 1000).roundToInt(), 1000))
            .setActions(listOf(RemoteAction(icon, label, label, intent)))
            .setSourceRectHint(android.graphics.Rect(bounds.left.roundToInt(), bounds.top.roundToInt(), bounds.right.roundToInt(), bounds.bottom.roundToInt()))
            .apply { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) setAutoEnterEnabled(enabled) }
            .build()
    }
    LaunchedEffect(params) { runCatching { activity.setPictureInPictureParams(params) } }
    val currentParams by rememberUpdatedState(params)
    val currentEnabled by rememberUpdatedState(enabled)
    DisposableEffect(activity) {
        // Before Android 12 the window opens on leaving; from 12 the params above open it
        val leave = Runnable {
            if (currentEnabled && Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                runCatching { activity.enterPictureInPictureMode(currentParams) }
            }
        }
        activity.addOnUserLeaveHintListener(leave)
        onDispose {
            activity.removeOnUserLeaveHintListener(leave)
            // Closed, the viewer no longer takes the app into a window
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                runCatching { activity.setPictureInPictureParams(PictureInPictureParams.Builder().setAutoEnterEnabled(false).setActions(emptyList()).build()) }
            }
        }
    }
    return { runCatching { activity.enterPictureInPictureMode(currentParams) } }
}

private const val PipToggle = "oolong.client.video.PIP_TOGGLE"

/** The close button on top; who posted it, the actions and the playing controls along the bottom. */
@Composable
private fun BoxScope.VideoChrome(
    video: ViewerVideo,
    landscape: Boolean,
    playing: Boolean,
    ended: Boolean,
    position: Long,
    buffered: Long,
    duration: Long,
    speed: Float,
    onClose: () -> Unit,
    onTogglePlaying: () -> Unit,
    onSeek: (Long) -> Unit,
    onSeekEnd: (Long) -> Unit,
    onSpeed: (anchor: Rect) -> Unit,
    onLandscape: () -> Unit,
    onCopyLink: () -> Unit,
    onShare: () -> Unit,
    /** Shrinks the video into a picture-in-picture window; null where there are none. */
    onPip: (() -> Unit)?,
    /** How much of the video is saved, 0–1 or -1 when not known; null when it is not being saved. */
    saving: Float?,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sides = if (landscape) 40.dp else 0.dp
    Box(modifier = modifier.matchParentSize()) {
        TopButton(
            icon = Symbols.Close,
            description = stringResource(id = R.string.btn_close),
            onClick = onClose,
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(start = 12.dp + sides, top = 8.dp),
        )
        if (onPip != null) {
            TopButton(
                icon = Symbols.PictureInPictureAlt,
                description = stringResource(id = R.string.video_pip),
                onClick = onPip,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(end = 12.dp + sides, top = 8.dp),
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.65f))))
                .navigationBarsPadding()
                .padding(start = 16.dp + sides, end = 8.dp + sides, top = 48.dp, bottom = 8.dp),
        ) {
            if (!landscape) {
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(modifier = Modifier.weight(1f).padding(bottom = 6.dp)) {
                        var expanded by remember { mutableStateOf(false) }
                        video.author?.takeIf { it.isNotBlank() }?.let {
                            Text(text = it, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        }
                        video.text?.takeIf { it.isNotBlank() }?.let {
                            Text(
                                text = it,
                                color = Color.White.copy(alpha = 0.85f),
                                fontSize = 13.5.sp,
                                lineHeight = 20.sp,
                                maxLines = if (expanded) 8 else 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .padding(top = 2.dp)
                                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { expanded = !expanded },
                            )
                        }
                    }
                    MediaGlassActions {
                        RoundAction(Symbols.Link, stringResource(id = R.string.title_copy_link), onCopyLink)
                        RoundAction(Symbols.Share, stringResource(id = R.string.title_share), onShare)
                        SaveAction(saving = saving, onClick = onSave)
                    }
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = if (landscape) 0.dp else 10.dp).height(40.dp),
            ) {
                ControlButton(
                    icon = when {
                        ended -> Symbols.ReplayFill
                        playing -> Symbols.PauseFill
                        else -> Symbols.PlayArrowFill
                    },
                    description = stringResource(id = if (playing) R.string.btn_pause else R.string.btn_play),
                    onClick = onTogglePlaying,
                )
                Text(text = formatDuration(position), color = Color.White.copy(alpha = 0.9f), style = TimeStyle, modifier = Modifier.widthIn(min = 32.dp))
                Scrubber(
                    position = position,
                    buffered = buffered,
                    duration = duration,
                    onSeek = onSeek,
                    onSeekEnd = onSeekEnd,
                    modifier = Modifier.weight(1f),
                )
                Text(text = formatDuration(duration), color = Color.White.copy(alpha = 0.9f), style = TimeStyle)
                var speedBounds by remember { mutableStateOf(Rect.Zero) }
                MediaGlassPill(
                    onClick = { onSpeed(speedBounds) },
                    onClickLabel = stringResource(id = R.string.video_speed),
                    modifier = Modifier.onGloballyPositioned { speedBounds = it.boundsInWindow() },
                ) {
                    Text(
                        text = speedLabel(speed),
                        color = Color.White,
                        style = TimeStyle.copy(fontSize = 12.sp),
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                    )
                }
                if (video.aspect in 0.01f..0.99f || landscape) {
                    ControlButton(
                        icon = if (landscape) Symbols.FullscreenExit else Symbols.Fullscreen,
                        description = stringResource(id = if (landscape) R.string.reply_exit_full_screen else R.string.btn_full_screen),
                        onClick = onLandscape,
                    )
                }
            }
        }
    }
}

/** A seek bar: thin while left alone, thicker under a finger, which may tap or drag along it. */
@Composable
private fun Scrubber(
    position: Long,
    buffered: Long,
    duration: Long,
    onSeek: (Long) -> Unit,
    onSeekEnd: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var held by remember { mutableStateOf(false) }
    val track by animateDpAsState(if (held) 7.dp else 3.dp, Motion.snap(), label = "track")
    val knob by animateDpAsState(if (held) 8.dp else 6.dp, Motion.snap(), label = "knob")
    val length = duration.coerceAtLeast(1L)
    val currentSeek by rememberUpdatedState(onSeek)
    val currentEnd by rememberUpdatedState(onSeekEnd)
    Canvas(
        modifier = modifier
            .height(24.dp)
            .pointerInput(length) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    held = true
                    fun at(x: Float) = (x / size.width).coerceIn(0f, 1f).let { (it * length).toLong() }
                    var target = at(down.position.x)
                    currentSeek(target)
                    while (true) {
                        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id }
                        if (change == null || !change.pressed) break
                        change.consume()
                        target = at(change.position.x)
                        currentSeek(target)
                    }
                    held = false
                    currentEnd(target)
                }
            },
    ) {
        val h = track.toPx()
        val top = (size.height - h) / 2
        val radius = CornerRadius(h / 2)
        val played = (position.toFloat() / length).coerceIn(0f, 1f) * size.width
        val loaded = (buffered.toFloat() / length).coerceIn(0f, 1f) * size.width
        drawRoundRect(Color.White.copy(alpha = 0.28f), Offset(0f, top), Size(size.width, h), radius)
        drawRoundRect(Color.White.copy(alpha = 0.35f), Offset(0f, top), Size(loaded, h), radius)
        drawRoundRect(Color.White, Offset(0f, top), Size(played, h), radius)
        drawCircle(Color.Black.copy(alpha = 0.25f), knob.toPx() + 1.dp.toPx(), Offset(played, size.height / 2))
        drawCircle(Color.White, knob.toPx(), Offset(played, size.height / 2))
    }
}

/** How far the video has played, along the bottom while the controls are hidden. */
@Composable
private fun ProgressLine(fraction: Float, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.fillMaxWidth().height(2.dp)) {
        drawRect(Color.White.copy(alpha = 0.2f))
        drawRect(Color.White.copy(alpha = 0.85f), size = Size(size.width * fraction.coerceIn(0f, 1f), size.height))
    }
}

@Composable
private fun SeekTime(target: Long, duration: Long) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(text = formatDuration(target), color = Color.White, style = TimeStyle.copy(fontSize = 28.sp, fontWeight = FontWeight.SemiBold))
        Text(text = " / ${formatDuration(duration)}", color = Color.White.copy(alpha = 0.7f), style = TimeStyle.copy(fontSize = 16.sp), modifier = Modifier.padding(bottom = 3.dp))
    }
}

@Composable
private fun Failure(message: Int, onRetry: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(text = stringResource(id = message), color = Color.White, fontSize = 15.sp)
        MediaGlassPill(onClick = onRetry) {
            Text(
                text = stringResource(id = R.string.button_retry),
                color = Color.White,
                fontSize = 14.sp,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 7.dp),
            )
        }
    }
}

@Composable
private fun BigButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    MediaGlassIconButton(icon = icon, description = description, onClick = onClick, size = 64.dp, iconSize = 34.dp)
}

@Composable
private fun ControlButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(imageVector = icon, contentDescription = description, tint = Color.White, modifier = Modifier.size(26.dp))
    }
}

@Composable
private fun RoundAction(icon: ImageVector, description: String, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(imageVector = icon, contentDescription = description, tint = Color.White, modifier = Modifier.size(22.dp))
    }
}

/** A button along the top, over the video. */
@Composable
private fun TopButton(icon: ImageVector, description: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    MediaGlassIconButton(icon = icon, description = description, onClick = onClick, modifier = modifier, size = 40.dp)
}

/** Saves the video; while it downloads, a ring fills in the button's place, which then does nothing. */
@Composable
private fun SaveAction(saving: Float?, onClick: () -> Unit) {
    val description = stringResource(id = R.string.video_save)
    IconButton(onClick = onClick, enabled = saving == null) {
        if (saving == null) {
            Icon(imageVector = Symbols.Download, contentDescription = description, tint = Color.White, modifier = Modifier.size(22.dp))
        } else if (saving < 0f) {
            WavyLoader(color = Color.White, modifier = Modifier.size(26.dp))
        } else {
            Canvas(modifier = Modifier.size(24.dp)) {
                val stroke = 2.5.dp.toPx()
                drawCircle(Color.White.copy(alpha = 0.3f), radius = size.minDimension / 2 - stroke / 2, style = Stroke(stroke))
                drawArc(
                    color = Color.White,
                    startAngle = -90f,
                    sweepAngle = 360f * saving,
                    useCenter = false,
                    style = Stroke(stroke, cap = StrokeCap.Round),
                    topLeft = Offset(stroke / 2, stroke / 2),
                    size = Size(size.width - stroke, size.height - stroke),
                )
            }
        }
    }
}

/** Why the video stopped, as the user can act on it. */
@OptIn(UnstableApi::class)
private fun PlaybackException.message(signedIn: Boolean): Int = when {
    errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
        errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> R.string.video_offline

    !signedIn && (cause as? HttpDataSource.InvalidResponseCodeException)?.responseCode in setOf(401, 403) -> R.string.video_sign_in
    else -> R.string.video_play_failed
}

private fun speedLabel(speed: Float): String =
    if (speed % 1f == 0f) "${speed.toInt()}×" else "$speed×"

private val Speeds = listOf(2f, 1.5f, 1.25f, 1f, 0.75f, 0.5f)

private val TimeStyle = TextStyle(fontSize = 12.5.sp, fontFeatureSettings = "tnum")

private const val ChromeTimeout = 3_000L

/** Buffering shorter than this shows nothing, so quick stalls don't flash a loader. */
private const val LoaderDelay = 300L

/** A video of height over width [aspect], whole and centred in [container]. */
private fun fitRect(container: Size, aspect: Float): Rect {
    if (container.width <= 0f || container.height <= 0f) return Rect(Offset.Zero, container)
    return if (aspect > container.height / container.width) {
        val width = container.height / aspect
        Rect((container.width - width) / 2, 0f, (container.width + width) / 2, container.height)
    } else {
        val height = container.width * aspect
        Rect(0f, (container.height - height) / 2, container.width, (container.height + height) / 2)
    }
}

/** This rect as a drag of [offset], scaled by [scale] about the container's centre, leaves it. */
private fun Rect.dragged(offset: Offset, scale: Float, container: Size): Rect {
    val centre = Offset(container.width / 2, container.height / 2)
    val topLeft = centre + (topLeft - centre) * scale + offset
    return Rect(topLeft, Size(width * scale, height * scale))
}

private fun Rect.scaledAround(scale: Float): Rect =
    Rect(center - Offset(width * scale / 2, height * scale / 2), Size(width * scale, height * scale))

/** Lays the content out at height over width [aspect], covering the space it is given, centred. */
private fun Modifier.covering(aspect: Float): Modifier = layout { measurable, constraints ->
    val width = constraints.maxWidth
    val height = constraints.maxHeight
    val (w, h) = when {
        aspect <= 0f || width == 0 -> width to height
        height.toFloat() / width > aspect -> (height / aspect).toInt() to height
        else -> width to (width * aspect).toInt()
    }
    val placeable = measurable.measure(Constraints.fixed(w, h))
    layout(width, height) { placeable.place((width - w) / 2, (height - h) / 2) }
}
