package com.huanchengfly.tieba.post.ui.widgets.compose

import android.content.Context
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.toSize
import coil3.compose.AsyncImage
import coil3.memory.MemoryCache
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import com.huanchengfly.tieba.post.R
import com.huanchengfly.tieba.post.models.PhotoViewData
import com.huanchengfly.tieba.post.ui.common.theme.compose.ExtendedTheme
import com.huanchengfly.tieba.post.ui.page.photoview.LocalPhotoViewer
import com.huanchengfly.tieba.post.utils.ImageUtil
import com.huanchengfly.tieba.post.utils.NetworkUtil
import com.huanchengfly.tieba.post.utils.appPreferences

/** Darkens images in night mode, when the user asks for it. */
private val DarkenFilter = ColorFilter.tint(Color(0x99000000), BlendMode.SrcAtop)

/** What images show while they load, or when they cannot: a flat block of the placeholder colour. */
@Composable
fun rememberImagePlaceholder(): Painter {
    val color = colorResource(
        id = if (ExtendedTheme.colors.isNightMode) R.color.color_place_holder_night else R.color.color_place_holder
    )
    return remember(color) { ColorPainter(color) }
}

fun shouldLoadImage(context: Context, skipNetworkCheck: Boolean): Boolean {
    val imageLoadSettings =
        context.appPreferences.imageLoadType?.toIntOrNull() ?: ImageUtil.SETTINGS_SMART_ORIGIN
    return skipNetworkCheck ||
        imageLoadSettings == ImageUtil.SETTINGS_SMART_ORIGIN ||
        imageLoadSettings == ImageUtil.SETTINGS_ALL_ORIGIN ||
        (imageLoadSettings == ImageUtil.SETTINGS_SMART_LOAD && NetworkUtil.isWifiConnected(
            context
        ))
}

@Composable
fun NetworkImage(
    imageUri: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    photoViewData: PhotoViewData? = null,
    contentScale: ContentScale = ContentScale.Fit,
    skipNetworkCheck: Boolean = false,
) {
    val context = LocalContext.current

    var shouldLoad by remember { mutableStateOf(shouldLoadImage(context, skipNetworkCheck)) }
    val enableClick = remember(photoViewData, shouldLoad) { photoViewData != null || !shouldLoad }

    val darken = ExtendedTheme.colors.isNightMode && context.appPreferences.imageDarkenWhenNightMode
    val placeholder = rememberImagePlaceholder()

    var layoutSize by remember { mutableStateOf(IntSize.Zero) }

    var layoutOffset by remember { mutableStateOf(Offset.Zero) }

    val request = remember(imageUri, shouldLoad) {
        ImageRequest.Builder(context)
            .data(imageUri)
            .apply {
                // Until tapped, only what is cached already
                if (!shouldLoad) networkCachePolicy(CachePolicy.DISABLED)
            }
            .build()
    }

    var imageAspectRatio by remember { mutableFloatStateOf(0f) }
    var memoryCacheKey by remember { mutableStateOf<MemoryCache.Key?>(null) }

    // The viewer opens out of this picture and closes back into it
    val viewer = LocalPhotoViewer.current
    val picId = photoViewData?.picItems?.getOrNull(photoViewData.index)?.picId
    if (viewer != null && picId != null) {
        DisposableEffect(viewer, picId) { onDispose { viewer.place(picId, null) } }
    }
    val haptic = LocalHapticFeedback.current
    // After a long press, the picture's menu follows this finger until it lifts
    var menuOpen by remember { mutableStateOf(false) }
    var boxOffset by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = Modifier
            .pointerInput(enableClick, photoViewData, viewer) {
                if (enableClick) {
                    val url = photoViewData?.picItems?.getOrNull(photoViewData.index)?.originUrl
                    detectTapGestures(
                        onLongPress = if (viewer != null && url != null) {
                            { offset ->
                                if (shouldLoad) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    menuOpen = true
                                    viewer.showMenu(url, at = boxOffset + offset)
                                }
                            }
                        } else {
                            null
                        },
                        onTap = {
                            if (!shouldLoad) {
                                shouldLoad = true
                            } else if (photoViewData != null) {
                                viewer?.open(
                                    data = photoViewData,
                                    from = Rect(layoutOffset, layoutSize.toSize()),
                                    thumbnail = memoryCacheKey,
                                    aspect = imageAspectRatio,
                                )
                            }
                        },
                    )
                }
            }
            .pointerInput(viewer) {
                if (viewer == null) return@pointerInput
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    while (true) {
                        val change = awaitPointerEvent().changes.firstOrNull() ?: break
                        val position = boxOffset + change.position
                        if (!change.pressed) {
                            if (menuOpen) viewer.liftFinger(position)
                            menuOpen = false
                            break
                        }
                        if (menuOpen) viewer.moveFinger(position)
                    }
                }
            }
            .onGloballyPositioned { boxOffset = it.positionInWindow() }
            .then(modifier)
    ) {
        AsyncImage(
            model = request,
            placeholder = placeholder,
            error = placeholder,
            onSuccess = {
                imageAspectRatio = it.result.image.height.toFloat() / it.result.image.width
                memoryCacheKey = it.result.memoryCacheKey
            },
            colorFilter = if (darken) DarkenFilter else null,
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged {
                    layoutSize = it
                }
                .onGloballyPositioned {
                    layoutOffset = it.positionInWindow()
                    if (viewer != null && picId != null) {
                        val shown = it.boundsInWindow()
                        viewer.place(
                            picId = picId,
                            bounds = Rect(layoutOffset, it.size.toSize()),
                            visible = shown.height >= it.size.height / 2f && shown.width >= it.size.width / 2f,
                        )
                    }
                },
            contentDescription = contentDescription,
            contentScale = contentScale,
        )
    }
}

@Composable
fun NetworkImage(
    imageUriProvider: () -> String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    photoViewDataProvider: (() -> PhotoViewData)? = null,
    contentScale: ContentScale = ContentScale.Fit,
    skipNetworkCheck: Boolean = false,
) {
    val imageUri by rememberUpdatedState(newValue = imageUriProvider())
    val photoViewData by rememberUpdatedState(newValue = photoViewDataProvider?.invoke())

    NetworkImage(
        imageUri = imageUri,
        contentDescription = contentDescription,
        modifier = modifier,
        photoViewData = photoViewData,
        contentScale = contentScale,
        skipNetworkCheck = skipNetworkCheck,
    )
}
