package com.huanchengfly.tieba.post.ui.page.settings.theme

import android.app.Activity
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Icon
import androidx.compose.material.Slider
import androidx.compose.material.SliderDefaults
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.FormatColorReset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.transformations
import com.godaddy.android.colorpicker.HsvColor
import com.godaddy.android.colorpicker.harmony.ColorHarmonyMode
import com.godaddy.android.colorpicker.harmony.HarmonyColorPicker
import com.huanchengfly.tieba.post.R
import com.huanchengfly.tieba.post.model.Agree
import com.huanchengfly.tieba.post.model.Post
import com.huanchengfly.tieba.post.model.PostContent
import com.huanchengfly.tieba.post.model.SubPost
import com.huanchengfly.tieba.post.model.TextSpan
import com.huanchengfly.tieba.post.model.User
import com.huanchengfly.tieba.post.toastShort
import com.huanchengfly.tieba.post.ui.common.LocalAccount
import com.huanchengfly.tieba.post.ui.common.theme.compose.ExtendedTheme
import com.huanchengfly.tieba.post.ui.common.theme.compose.LocalExtendedColors
import com.huanchengfly.tieba.post.ui.common.theme.compose.getThemeColorForTheme
import com.huanchengfly.tieba.post.ui.navigation.Navigator
import com.huanchengfly.tieba.post.ui.page.LocalNavigator
import com.huanchengfly.tieba.post.ui.page.thread.PostCardOptions
import com.huanchengfly.tieba.post.ui.page.thread.PostItem
import com.huanchengfly.tieba.post.ui.page.thread.ThreadPostCard
import com.huanchengfly.tieba.post.ui.utils.rememberSystemUiController
import com.huanchengfly.tieba.post.ui.widgets.compose.BackNavigationIcon
import com.huanchengfly.tieba.post.ui.widgets.compose.Dialog
import com.huanchengfly.tieba.post.ui.widgets.compose.DialogNegativeButton
import com.huanchengfly.tieba.post.ui.widgets.compose.DialogPositiveButton
import com.huanchengfly.tieba.post.ui.widgets.compose.rememberDialogState
import com.huanchengfly.tieba.post.utils.PickMediasContract
import com.huanchengfly.tieba.post.utils.PickMediasRequest
import com.huanchengfly.tieba.post.utils.ThemeUtil
import com.yalantis.ucrop.UCrop
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.roundToInt

/**
 * Sets up the translucent theme: the wallpaper fills the page as the app will draw it, with a
 * post over it in the chosen colours, and the settings sit in a panel at the bottom. Nothing is
 * saved until it is applied.
 */
@Composable
fun TranslucentThemePage(
    navigator: Navigator = LocalNavigator.current,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val source = remember { TranslucentWallpaper.source(context) }
    // Bumped whenever a new image is cropped into [source], so the preview reloads it
    var sourceVersion by rememberSaveable { mutableIntStateOf(if (source.exists()) 1 else 0) }
    var darkText by rememberSaveable { mutableStateOf(false) }
    var dim by rememberSaveable { mutableStateOf(TranslucentWallpaper.DEFAULT_DIM) }
    var blur by rememberSaveable { mutableIntStateOf(0) }
    var accent by rememberSaveable { mutableStateOf<Int?>(null) }
    var loaded by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (loaded) return@LaunchedEffect
        val saved = TranslucentWallpaper.saved(context)
        darkText = saved.darkText
        dim = saved.dim
        blur = saved.blur
        accent = saved.accent
        loaded = true
    }
    var applying by remember { mutableStateOf(false) }
    val wallpaper = TranslucentWallpaper(darkText, dim, blur, accent)

    var swatches by remember { mutableStateOf(emptyList<Int>()) }
    LaunchedEffect(sourceVersion) {
        if (sourceVersion > 0) swatches = context.wallpaperColors(source)
    }

    val pickImage = rememberWallpaperPicker(source) { sourceVersion++ }

    // Status bar icons follow the preview's text colour while the page is open
    val systemUiController = rememberSystemUiController()
    DisposableEffect(systemUiController, darkText) {
        systemUiController.setStatusBarColor(Color.Transparent, darkIcons = darkText)
        onDispose { systemUiController.setStatusBarColor(Color.Transparent, darkIcons = ThemeUtil.isStatusBarFontDark()) }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        if (sourceVersion > 0) {
            WallpaperPreview(source, sourceVersion, wallpaper)
        }
        PreviewColors(wallpaper) {
            Column(modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp),
                ) {
                    BackNavigationIcon(onBackPressed = { navigator.navigateUp() })
                    Text(
                        text = stringResource(id = R.string.title_theme_translucent),
                        color = ExtendedTheme.colors.text,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                if (sourceVersion > 0) SamplePost()
            }
        }
        SettingsPanel(modifier = Modifier.align(Alignment.BottomCenter)) {
            SettingsBlock {
                PanelRow(
                    title = stringResource(id = R.string.title_translucent_background),
                    onClick = pickImage,
                    trailing = { Text(text = "›", color = ExtendedTheme.colors.textSecondary, fontSize = 20.sp) },
                )
            }
            if (sourceVersion > 0) {
                SettingsBlock {
                    PanelRow(title = stringResource(id = R.string.title_translucent_text_color)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Choice(stringResource(id = R.string.light_color), selected = !darkText) { darkText = false }
                            Choice(stringResource(id = R.string.dark_color), selected = darkText) { darkText = true }
                        }
                    }
                    AccentRow(accent, swatches) { accent = it }
                    SliderRow(
                        title = stringResource(id = R.string.title_translucent_theme_dim),
                        value = dim,
                        range = 0f..TranslucentWallpaper.MAX_DIM,
                        label = "${(dim * 100).roundToInt()}%",
                        onChange = { dim = it },
                    )
                    var blurDraft by remember(blur) { mutableStateOf(wallpaper.blurPosition) }
                    SliderRow(
                        title = stringResource(id = R.string.title_translucent_theme_blur),
                        value = blurDraft,
                        range = 0f..1f,
                        label = "${(blurDraft * 100).roundToInt()}%",
                        onChange = { blurDraft = it },
                        // Blurring redraws the image, so only once the thumb is let go
                        onChangeFinished = { blur = TranslucentWallpaper.blurAt(blurDraft) },
                    )
                }
                ApplyButton(enabled = !applying) {
                    applying = true
                    scope.launch {
                        val applied = context.applyWallpaper(source, wallpaper)
                        applying = false
                        if (applied) navigator.navigateUp() else context.toastShort(R.string.text_load_failed)
                    }
                }
            }
        }
    }
}

/** The wallpaper blurred and dimmed as [wallpaper] says, filling the page. */
@Composable
private fun WallpaperPreview(source: File, version: Int, wallpaper: TranslucentWallpaper) {
    val context = LocalContext.current
    val request = remember(version, wallpaper.blur) {
        ImageRequest.Builder(context)
            .data(source)
            .memoryCacheKey("translucent-preview:$version:${wallpaper.blur}")
            .diskCachePolicy(CachePolicy.DISABLED)
            .apply { wallpaper.blurTransformation(context)?.let { transformations(it) } }
            .build()
    }
    AsyncImage(
        model = request,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize(),
    )
    Box(modifier = Modifier.fillMaxSize().background(Color(wallpaper.dimColor).copy(alpha = wallpaper.dim)))
}

/** The translucent theme's colours for [wallpaper], whatever theme the app uses now. */
@Composable
private fun PreviewColors(wallpaper: TranslucentWallpaper, content: @Composable () -> Unit) {
    val colors = getThemeColorForTheme(ThemeUtil.THEME_TRANSLUCENT, wallpaper.variant).let { colors ->
        wallpaper.accent?.let { colors.copy(primary = Color(it)) } ?: colors
    }
    CompositionLocalProvider(LocalExtendedColors provides colors, content = content)
}

/** A floor and a reply in it, to judge how well text reads on the wallpaper. */
@Composable
private fun SamplePost() {
    val account = LocalAccount.current
    val appName = stringResource(id = R.string.app_name)
    val author = User(
        id = 1,
        name = account?.name.orEmpty(),
        nameShow = account?.nameShow ?: account?.name ?: appName,
        portrait = account?.portrait.orEmpty(),
    )
    val app = User(id = 2, name = "", nameShow = appName, portrait = "")
    val question = stringResource(id = R.string.sample_translucent_question)
    val answer = stringResource(id = R.string.sample_translucent_answer)
    val item = remember(author, question, answer) {
        PostItem(
            Post(
                id = 2,
                floor = 2,
                title = "",
                time = System.currentTimeMillis() / 1000,
                author = author,
                content = text(question),
                agree = Agree(count = 0, agreed = false),
                subPosts = persistentListOf(SubPost(id = 3, author = app, content = text(answer), time = 0)),
                subPostCount = 1,
            )
        )
    }
    // Only a picture of a post: taps and long presses do nothing
    Box(
        modifier = Modifier.pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
            }
        }
    ) {
        ThreadPostCard(item = item, options = PostCardOptions(threadAuthorId = 0), onEvent = {})
    }
}

private fun text(value: String) = persistentListOf<PostContent>(PostContent.Text(persistentListOf(TextSpan.Plain(value))))

/** The opaque panel at the bottom, as sheets are opaque on the translucent theme. */
@Composable
private fun BoxScope.SettingsPanel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = 520.dp)
            .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .background(ExtendedTheme.colors.windowBackground)
            .verticalScroll(rememberScrollState())
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

/** A grey block of rows, as in the settings pages. */
@Composable
private fun SettingsBlock(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(ExtendedTheme.colors.chip)
            .padding(vertical = 4.dp),
        content = content,
    )
}

@Composable
private fun PanelRow(
    title: String,
    onClick: (() -> Unit)? = null,
    trailing: @Composable () -> Unit = {},
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp),
    ) {
        Text(text = title, color = ExtendedTheme.colors.text, fontSize = 16.sp, modifier = Modifier.weight(1f))
        trailing()
    }
}

@Composable
private fun Choice(text: String, selected: Boolean, onClick: () -> Unit) {
    val colors = ExtendedTheme.colors
    Text(
        text = text,
        color = if (selected) colors.onAccent else colors.textSecondary,
        fontSize = 14.sp,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        modifier = Modifier
            .clip(CircleShape)
            .background(if (selected) colors.accent else colors.divider)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 6.dp),
    )
}

/** The emphasis colour: the theme's default, colours from the wallpaper, or one of the user's own. */
@Composable
private fun AccentRow(accent: Int?, swatches: List<Int>, onChange: (Int?) -> Unit) {
    val dialog = rememberDialogState()
    var custom by remember { mutableStateOf(Color(accent ?: swatches.firstOrNull() ?: Color.Gray.toArgb())) }
    Dialog(
        dialogState = dialog,
        title = { Text(text = stringResource(id = R.string.title_select_color)) },
        buttons = {
            DialogPositiveButton(text = stringResource(id = R.string.button_sure_default)) { onChange(custom.toArgb()) }
            DialogNegativeButton(text = stringResource(id = R.string.button_cancel))
        },
    ) {
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            HarmonyColorPicker(
                harmonyMode = ColorHarmonyMode.NONE,
                color = HsvColor.from(custom),
                onColorChanged = { custom = it.toColor() },
                modifier = Modifier.sizeIn(maxWidth = 280.dp, maxHeight = 280.dp),
            )
        }
    }
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(
            text = stringResource(id = R.string.title_select_color),
            color = ExtendedTheme.colors.text,
            fontSize = 16.sp,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Spacer(modifier = Modifier.height(10.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
        ) {
            item {
                Swatch(color = null, selected = accent == null, onClick = { onChange(null) }) {
                    Icon(Icons.Rounded.FormatColorReset, contentDescription = null, tint = ExtendedTheme.colors.textSecondary)
                }
            }
            items(swatches) { color ->
                Swatch(color = Color(color), selected = accent == color, onClick = { onChange(color) })
            }
            item {
                val isCustom = accent != null && accent !in swatches
                Swatch(color = if (isCustom) Color(accent!!) else null, selected = isCustom, onClick = { dialog.show() }) {
                    if (!isCustom) Icon(Icons.Rounded.Add, contentDescription = null, tint = ExtendedTheme.colors.textSecondary)
                }
            }
        }
    }
}

@Composable
private fun Swatch(
    color: Color?,
    selected: Boolean,
    onClick: () -> Unit,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val colors = ExtendedTheme.colors
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(color ?: colors.divider)
            .border(if (selected) 2.dp else 0.dp, if (selected) colors.text else Color.Transparent, CircleShape)
            .clickable(onClick = onClick),
    ) {
        if (selected && color != null) Icon(Icons.Rounded.Check, contentDescription = null, tint = Color.White)
        content()
    }
}

@Composable
private fun SliderRow(
    title: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    label: String,
    onChange: (Float) -> Unit,
    onChangeFinished: (() -> Unit)? = null,
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = title, color = ExtendedTheme.colors.text, fontSize = 16.sp, modifier = Modifier.weight(1f))
            Text(text = label, color = ExtendedTheme.colors.textSecondary, fontSize = 14.sp)
        }
        Slider(
            value = value,
            onValueChange = onChange,
            onValueChangeFinished = onChangeFinished,
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = ExtendedTheme.colors.accent,
                activeTrackColor = ExtendedTheme.colors.accent,
                inactiveTrackColor = ExtendedTheme.colors.textSecondary.copy(alpha = 0.24f),
            ),
        )
    }
}

@Composable
private fun ApplyButton(enabled: Boolean, onClick: () -> Unit) {
    val colors = ExtendedTheme.colors
    Text(
        text = stringResource(id = R.string.button_apply),
        color = colors.onAccent,
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold,
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clip(CircleShape)
            .background(if (enabled) colors.accent else colors.accent.copy(alpha = 0.5f))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 14.dp),
    )
}

/**
 * Picks an image and crops it to the screen's shape into [output], then calls [onPicked].
 * Returns what starts it.
 */
@Composable
private fun rememberWallpaperPicker(output: File, onPicked: () -> Unit): () -> Unit {
    val context = LocalContext.current
    val colors = ExtendedTheme.colors
    val cropLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == Activity.RESULT_OK) {
            onPicked()
        } else if (it.resultCode == UCrop.RESULT_ERROR) {
            it.data?.let(UCrop::getError)?.printStackTrace()
        }
    }
    val pickLauncher = rememberLauncherForActivityResult(PickMediasContract) { (_, uris) ->
        val picked = uris.firstOrNull() ?: return@rememberLauncherForActivityResult
        val metrics = context.resources.displayMetrics
        val intent = UCrop.of(picked, Uri.fromFile(output))
            .withAspectRatio(metrics.widthPixels.toFloat(), metrics.heightPixels.toFloat())
            .withOptions(UCrop.Options().apply {
                setShowCropFrame(true)
                setShowCropGrid(true)
                setToolbarColor(colors.windowBackground.toArgb())
                setToolbarWidgetColor(colors.text.toArgb())
                setActiveControlsWidgetColor(colors.accent.toArgb())
                setCompressionFormat(Bitmap.CompressFormat.JPEG)
                setCompressionQuality(95)
            })
            .getIntent(context)
        cropLauncher.launch(intent)
    }
    return { pickLauncher.launch(PickMediasRequest()) }
}
