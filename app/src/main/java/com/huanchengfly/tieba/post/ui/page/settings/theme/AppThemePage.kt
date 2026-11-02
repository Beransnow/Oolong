package com.huanchengfly.tieba.post.ui.page.settings.theme

import android.content.Context
import android.os.Build
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.material.TextFieldDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BorderColor
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ColorLens
import androidx.compose.material.icons.rounded.Colorize
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.PhotoSizeSelectActual
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.core.graphics.toColorInt
import androidx.datastore.preferences.core.stringPreferencesKey
import coil3.compose.AsyncImage
import com.godaddy.android.colorpicker.HsvColor
import com.godaddy.android.colorpicker.harmony.ColorHarmonyMode
import com.godaddy.android.colorpicker.harmony.HarmonyColorPicker
import com.huanchengfly.tieba.post.R
import com.huanchengfly.tieba.post.rememberPreferenceAsState
import com.huanchengfly.tieba.post.ui.common.theme.compose.ExtendedTheme
import com.huanchengfly.tieba.post.ui.common.theme.compose.dynamicTonalPalette
import com.huanchengfly.tieba.post.ui.common.theme.compose.pickedColor
import com.huanchengfly.tieba.post.ui.common.theme.compose.themeColors
import com.huanchengfly.tieba.post.ui.navigation.Navigator
import com.huanchengfly.tieba.post.ui.navigation.TranslucentThemeKey
import com.huanchengfly.tieba.post.ui.page.LocalNavigator
import com.huanchengfly.tieba.post.ui.widgets.compose.BackNavigationIcon
import com.huanchengfly.tieba.post.ui.widgets.compose.Dialog
import com.huanchengfly.tieba.post.ui.widgets.compose.DialogNegativeButton
import com.huanchengfly.tieba.post.ui.widgets.compose.DialogPositiveButton
import com.huanchengfly.tieba.post.ui.widgets.compose.GlassScaffold
import com.huanchengfly.tieba.post.ui.widgets.compose.GlassTopBar
import com.huanchengfly.tieba.post.ui.widgets.compose.GlassTopBarTitle
import com.huanchengfly.tieba.post.ui.widgets.compose.ProvideContentColor
import com.huanchengfly.tieba.post.ui.widgets.compose.rememberDialogState
import com.huanchengfly.tieba.post.utils.ThemeUtil
import com.huanchengfly.tieba.post.utils.appPreferences
import com.huanchengfly.tieba.post.utils.extension.toHexString
import java.io.File

@Composable
fun AppThemePage(
    navigator: Navigator = LocalNavigator.current,
) {
    val context = LocalContext.current
    val themeValues = stringArrayResource(id = R.array.theme_values)
    val themeNames = stringArrayResource(id = R.array.themeNames)
    val settings by remember { ThemeUtil.settingsState }
    val customPrimaryColorDialogState = rememberDialogState()
    var customPrimaryColor by remember {
        mutableStateOf(
            savedCustomColor(context)
        )
    }

    Dialog(
        dialogState = customPrimaryColorDialogState,
        title = { Text(text = stringResource(id = R.string.title_custom_theme)) },
        buttons = {
            DialogPositiveButton(
                text = stringResource(id = R.string.button_finish),
                onClick = {
                    context.appPreferences.customPrimaryColor = customPrimaryColor.toHexString()
                    ThemeUtil.selectLightTheme(ThemeUtil.THEME_CUSTOM)
                }
            )
            DialogNegativeButton(
                text = stringResource(id = R.string.button_cancel),
                onClick = {
                    customPrimaryColor = savedCustomColor(context)
                }
            )
        }
    ) {
        var useInput by remember { mutableStateOf(false) }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AnimatedContent(
                targetState = useInput,
                label = "",
                modifier = Modifier
                    .wrapContentHeight()
                    .animateContentSize()
            ) { input ->
                if (input) {
                    var inputHexColor by remember { mutableStateOf(customPrimaryColor.toHexString()) }
                    val lastValidColor by produceState(
                        initialValue = customPrimaryColor,
                        inputHexColor
                    ) {
                        if ("^#([0-9a-fA-F]{6})$".toRegex().matches(inputHexColor)) {
                            value = Color(inputHexColor.toColorInt())
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Spacer(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(lastValidColor)
                        )
                        OutlinedTextField(
                            value = inputHexColor,
                            onValueChange = {
                                if ("^#([0-9a-fA-F]{0,6})$".toRegex().matches(it)) {
                                    inputHexColor = it
                                }
                            },
                            maxLines = 1,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            modifier = Modifier.weight(1f),
                            colors = TextFieldDefaults.outlinedTextFieldColors(
                                cursorColor = ExtendedTheme.colors.emphasis,
                                focusedBorderColor = ExtendedTheme.colors.emphasis,
                                focusedLabelColor = ExtendedTheme.colors.emphasis
                            )
                        )
                        IconButton(
                            onClick = {
                                customPrimaryColor = Color(inputHexColor.toColorInt())
                                useInput = false
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = stringResource(id = R.string.button_sure_default)
                            )
                        }
                    }
                } else {
                    Box {
                        HarmonyColorPicker(
                            harmonyMode = ColorHarmonyMode.ANALOGOUS,
                            color = HsvColor.from(customPrimaryColor),
                            onColorChanged = {
                                customPrimaryColor = it.toColor()
                            },
                            modifier = Modifier.sizeIn(maxWidth = 320.dp, maxHeight = 320.dp)
                        )

                        IconButton(
                            onClick = { useInput = true },
                            modifier = Modifier.align(Alignment.TopEnd)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.BorderColor,
                                contentDescription = stringResource(id = R.string.desc_input_color)
                            )
                        }
                    }
                }
            }
        }
    }

    GlassScaffold(
        backgroundColor = Color.Transparent,
        topBar = {
            GlassTopBar(
                title = { GlassTopBarTitle(text = stringResource(id = R.string.title_theme)) },
                navigationIcon = {
                    BackNavigationIcon(onBackPressed = { navigator.navigateUp() })
                }
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = paddingValues,
            ) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    item {
                        val tonalPalette = remember { dynamicTonalPalette(context) }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    brush = Brush.sweepGradient(
                                        colors = listOf(
                                            tonalPalette.primary50,
                                            tonalPalette.secondary50,
                                            tonalPalette.tertiary50,
                                            tonalPalette.primary50,
                                        )
                                    )
                                )
                                .clickable {
                                    ThemeUtil.selectDynamicColor()
                                }
                                .padding(all = 16.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                            ) {
                                Icon(
                                    imageVector = if (settings.isDynamic) {
                                        Icons.Rounded.Check
                                    } else {
                                        Icons.Rounded.Colorize
                                    },
                                    contentDescription = null,
                                    tint = ExtendedTheme.colors.sheet
                                )
                                Text(
                                    text = stringResource(id = R.string.title_dynamic_theme),
                                    fontWeight = FontWeight.Bold,
                                    color = ExtendedTheme.colors.sheet
                                )
                            }
                        }
                    }
                }
                item {
                    ProvideContentColor(color = ExtendedTheme.colors.sheet) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(IntrinsicSize.Min)
                                .padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(
                                        color = customPrimaryColor,
                                    )
                                    .clickable {
                                        customPrimaryColorDialogState.show()
                                    }
                                    .padding(all = 16.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                                ) {
                                    Icon(
                                        imageVector = if (settings.isLightTheme(ThemeUtil.THEME_CUSTOM)) {
                                            Icons.Rounded.Check
                                        } else {
                                            Icons.Rounded.ColorLens
                                        },
                                        contentDescription = null
                                    )
                                    Text(
                                        text = stringResource(id = R.string.title_custom_color),
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(6.dp))
                                    // On the translucent theme the tile shows the page's own wallpaper
                                    .border(1.dp, ExtendedTheme.colors.divider, RoundedCornerShape(6.dp))
                                    .clickable {
                                        navigator.navigate(TranslucentThemeKey)
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                val backgroundPath by rememberPreferenceAsState(
                                    key = stringPreferencesKey(ThemeUtil.KEY_TRANSLUCENT_THEME_BACKGROUND_PATH),
                                    defaultValue = "",
                                )
                                val previewImage: Any =
                                    backgroundPath.takeIf { it.isNotEmpty() }?.let(::File) ?: R.drawable.user_header
                                AsyncImage(
                                    model = previewImage,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    // The tile takes its height from its label, not the image's
                                    modifier = Modifier.matchParentSize()
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(all = 16.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                                    ) {
                                        Icon(
                                            imageVector = if (settings.wallpaper) {
                                                Icons.Rounded.Check
                                            } else {
                                                Icons.Rounded.PhotoSizeSelectActual
                                            },
                                            contentDescription = null,
                                            // Over a picture, so always light
                                            tint = Color.White,
                                        )
                                        Text(
                                            text = stringResource(id = R.string.title_theme_translucent),
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                itemsIndexed(
                    items = themeValues.toList(),
                    key = { _, item -> item }
                ) { index, item ->
                    val name = themeNames[index]
                    val colors = remember { themeColors(item, pickedColor(context, item)) }
                    if (index == 0) {
                        Spacer(modifier = Modifier.size(16.dp))
                    }
                    if (ThemeUtil.isNightMode(item)) {
                        ThemeItem(
                            themeName = name,
                            themeValue = item,
                            primaryColor = colors.background,
                            accentColor = colors.background,
                            contentColor = colors.text.copy(alpha = 1f),
                            selected = settings.darkTheme == item,
                            onClick = { ThemeUtil.selectDarkTheme(item) }
                        )
                    } else {
                        ThemeItem(
                            themeName = name,
                            themeValue = item,
                            primaryColor = colors.emphasis,
                            accentColor = colors.accent,
                            contentColor = colors.onAccent,
                            selected = settings.isLightTheme(item),
                            onClick = { ThemeUtil.selectLightTheme(item) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ThemeItem(
    themeName: String,
    themeValue: String,
    primaryColor: Color,
    accentColor: Color,
    contentColor: Color,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clickable(
                onClickLabel = themeName,
                onClick = onClick
            )
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(
                    brush = Brush.radialGradient(
                        listOf(
                            primaryColor,
                            accentColor,
                        )
                    )
                )
                .padding(9.dp),
        ) {
            if (ThemeUtil.isNightMode(themeValue)) {
                Icon(
                    imageVector = Icons.Rounded.NightsStay,
                    contentDescription = stringResource(id = R.string.desc_night_theme),
                    tint = contentColor
                )
            }
        }
        Text(
            text = themeName,
            modifier = Modifier.weight(1f)
        )
        if (selected) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = stringResource(id = R.string.desc_checked),
                tint = ExtendedTheme.colors.emphasis
            )
        }
    }
}

/** The custom theme's colour as saved, or its default. */
private fun savedCustomColor(context: Context): Color =
    themeColors(ThemeUtil.THEME_CUSTOM, pickedColor(context, ThemeUtil.THEME_CUSTOM)).accent
