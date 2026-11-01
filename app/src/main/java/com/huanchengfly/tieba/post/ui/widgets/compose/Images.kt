package com.huanchengfly.tieba.post.ui.widgets.compose

import android.content.Context
import android.util.Log
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.util.lerp
import coil3.compose.AsyncImage
import coil3.memory.MemoryCache
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import com.huanchengfly.tieba.post.App
import com.huanchengfly.tieba.post.R
import com.huanchengfly.tieba.post.arch.BaseComposeActivity.Companion.LocalWindowSizeClass
import com.huanchengfly.tieba.post.models.PhotoViewData
import com.huanchengfly.tieba.post.ui.common.theme.compose.ExtendedTheme
import com.huanchengfly.tieba.post.ui.common.windowsizeclass.WindowWidthSizeClass
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
private fun PreviewImage(
    imageUri: String,
    show: Boolean,
    layoutSizeProvider: () -> IntSize,
    layoutOffsetProvider: () -> Offset,
    imageAspectRatioProvider: () -> Float,
    originImageUri: String? = null,
) {
    val context = LocalContext.current
    val density = LocalDensity.current

    var showFullScreenLayout by remember { mutableStateOf(false) }
    var showPreview by remember { mutableStateOf(false) }

    LaunchedEffect(show) {
        if (show) {
            showFullScreenLayout = true
            showPreview = true
        } else {
            showPreview = false
        }
    }

    val windowWidthSizeClass = LocalWindowSizeClass.current.widthSizeClass
    val widthFraction = remember(windowWidthSizeClass) {
        when (windowWidthSizeClass) {
            WindowWidthSizeClass.Compact -> 0.8f
            WindowWidthSizeClass.Medium -> 0.6f
            else -> 0.4f
        }
    }
    val imageAspectRatio = imageAspectRatioProvider()
    val previewImageWidthPx = remember(widthFraction) {
        App.ScreenInfo.EXACT_SCREEN_WIDTH * widthFraction
    }
    val previewImageHeightPx = remember(imageAspectRatio, previewImageWidthPx) {
        previewImageWidthPx * imageAspectRatio
    }
    val previewImageWidthDp = remember(previewImageWidthPx, density) {
        with(density) { previewImageWidthPx.toDp() }
    }
    val previewImageHeightDp = remember(previewImageHeightPx, density) {
        with(density) { previewImageHeightPx.toDp() }
    }

    val screenWidth = App.ScreenInfo.EXACT_SCREEN_WIDTH
    val screenCenterX = screenWidth / 2
    val screenHeight = App.ScreenInfo.EXACT_SCREEN_HEIGHT
    val screenCenterY = screenHeight / 2
    val statusBarHeight = WindowInsets.statusBars.getTop(density)

    if (showFullScreenLayout) {
        val animProgress = remember { Animatable(0f) }

        LaunchedEffect(showPreview) {
            animProgress.animateTo(
                if (showPreview) 1f else 0f,
                animationSpec = spring(
                    stiffness = Spring.StiffnessMedium,
                )
            )
            if (!showPreview) {
                showFullScreenLayout = false
            }
        }

        FullScreen {
            val (layoutWidthPx, layoutHeightPx) = layoutSizeProvider()
            val layoutWidthDp = remember(layoutWidthPx, density) {
                with(density) { layoutWidthPx.toDp() }
            }
            val layoutHeightDp = remember(layoutHeightPx, density) {
                with(density) { layoutHeightPx.toDp() }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(
                            lerp(layoutWidthDp, previewImageWidthDp, animProgress.value)
                        )
                        .height(
                            lerp(layoutHeightDp, previewImageHeightDp, animProgress.value)
                        )
                        .absoluteOffset {
                            val layoutOffset = layoutOffsetProvider()
                            val currentLayoutWidthPx = lerp(
                                layoutWidthPx.toFloat(),
                                previewImageWidthPx,
                                animProgress.value
                            )
                            val currentLayoutHeightPx = lerp(
                                layoutHeightPx.toFloat(),
                                previewImageHeightPx,
                                animProgress.value
                            )
                            IntOffset(
                                lerp(
                                    layoutOffset.x - (screenCenterX - currentLayoutWidthPx / 2),
                                    0f,
                                    animProgress.value
                                ).toInt(),
                                lerp(
                                    layoutOffset.y - (screenCenterY - currentLayoutHeightPx / 2 + statusBarHeight / 2),
                                    0f,
                                    animProgress.value
                                ).toInt()
                            )
                        }
                        .clip(RoundedCornerShape(6.dp))
                ) {
                    AsyncImage(
                        model = imageUri,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )

                    // Over the picture already shown, which stays visible until the original arrives
                    if (originImageUri != null && animProgress.value >= 1f) {
                        AsyncImage(
                            model = originImageUri,
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun NetworkImage(
    imageUri: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    photoViewData: PhotoViewData? = null,
    contentScale: ContentScale = ContentScale.Fit,
    skipNetworkCheck: Boolean = false,
    enablePreview: Boolean = false,
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

    var isLongPressing by remember { mutableStateOf(false) }

    // The viewer opens out of this picture and closes back into it
    val viewer = LocalPhotoViewer.current
    val picId = photoViewData?.picItems?.getOrNull(photoViewData.index)?.picId
    if (viewer != null && picId != null) {
        DisposableEffect(viewer, picId) { onDispose { viewer.place(picId, null) } }
    }

    if (enablePreview) {
        PreviewImage(
            imageUri = imageUri,
            show = isLongPressing,
            layoutSizeProvider = { layoutSize },
            layoutOffsetProvider = { layoutOffset },
            imageAspectRatioProvider = { imageAspectRatio },
            originImageUri = photoViewData?.data?.originUrl
        )
    }

    Box(
        modifier = Modifier
            .pointerInput(enableClick) {
                if (enableClick) {
                    detectTapGestures(
                        onLongPress = {
                            isLongPressing = true
                        },
                        onPress = {
                            tryAwaitRelease()
                            isLongPressing = false
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
                        }
                    )
                }
            }
            .pointerInput(Unit) {
                detectDragGesturesAfterLongPress { change, dragAmount ->
                    Log.i("NetworkImage", "dragAmount: $dragAmount")
                }
            }
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
    enablePreview: Boolean = false,
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
        enablePreview = enablePreview
    )
}
