package com.huanchengfly.tieba.post.ui.page.user.edit

import android.app.Activity
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Card
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.constraintlayout.compose.Dimension
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.huanchengfly.tieba.post.R
import com.huanchengfly.tieba.post.toastShort
import com.huanchengfly.tieba.post.ui.common.theme.compose.ExtendedTheme
import com.huanchengfly.tieba.post.ui.common.theme.utils.ThemeUtils
import com.huanchengfly.tieba.post.ui.navigation.Navigator
import com.huanchengfly.tieba.post.ui.page.LocalNavigator
import com.huanchengfly.tieba.post.ui.widgets.compose.ActionItem
import com.huanchengfly.tieba.post.ui.widgets.compose.BackNavigationIcon
import com.huanchengfly.tieba.post.ui.widgets.compose.BaseTextField
import com.huanchengfly.tieba.post.ui.widgets.compose.CounterTextField
import com.huanchengfly.tieba.post.ui.widgets.compose.Dialog
import com.huanchengfly.tieba.post.ui.widgets.compose.DialogNegativeButton
import com.huanchengfly.tieba.post.ui.widgets.compose.Toolbar
import com.huanchengfly.tieba.post.ui.widgets.compose.picker.ListSinglePicker
import com.huanchengfly.tieba.post.ui.widgets.compose.placeholder.placeholder
import com.huanchengfly.tieba.post.ui.widgets.compose.rememberDialogState
import com.huanchengfly.tieba.post.utils.PickMediasContract
import com.huanchengfly.tieba.post.utils.PickMediasRequest
import com.huanchengfly.tieba.post.utils.StringUtil
import com.yalantis.ucrop.UCrop
import kotlinx.collections.immutable.persistentListOf
import java.io.File

@Composable
fun EditProfilePage(
    navigator: Navigator = LocalNavigator.current,
    viewModel: EditProfileViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.effect.collect { effect ->
            when (effect) {
                EditProfileEffect.Done -> navigator.navigateUp()
            }
        }
    }
    val pickPortrait = rememberPortraitPicker(onPicked = viewModel::uploadPortrait)
    EditProfileScreen(
        state = state,
        onBack = { navigator.navigateUp() },
        onSave = viewModel::save,
        onNickNameChange = viewModel::setNickName,
        onIntroChange = viewModel::setIntro,
        onSexChange = viewModel::setSex,
        onUploadPortrait = pickPortrait,
    )
}

/**
 * Picks an image and crops it square, then calls [onPicked] with the cropped file. Returns what
 * starts it.
 */
@Composable
private fun rememberPortraitPicker(onPicked: (File) -> Unit): () -> Unit {
    val context = LocalContext.current
    val cropped = remember(context) { File(context.cacheDir, "cropped_portrait") }
    val cropLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == Activity.RESULT_OK) {
            onPicked(cropped)
        } else if (it.resultCode == UCrop.RESULT_ERROR) {
            it.data?.let(UCrop::getError)?.printStackTrace()
        }
    }
    val pickLauncher = rememberLauncherForActivityResult(PickMediasContract) { (_, uris) ->
        val source = uris.firstOrNull() ?: return@rememberLauncherForActivityResult
        val intent = UCrop.of(source, Uri.fromFile(cropped))
            .withAspectRatio(1f, 1f)
            .withOptions(UCrop.Options().apply {
                setShowCropFrame(true)
                setShowCropGrid(true)
                setToolbarColor(ThemeUtils.getColorByAttr(context, R.attr.colorPrimary))
                setToolbarWidgetColor(ThemeUtils.getColorByAttr(context, R.attr.colorTextOnPrimary))
                setActiveControlsWidgetColor(ThemeUtils.getColorByAttr(context, R.attr.colorAccent))
                setLogoColor(ThemeUtils.getColorByAttr(context, R.attr.colorPrimary))
                setCompressionFormat(Bitmap.CompressFormat.JPEG)
            })
            .getIntent(context)
        cropLauncher.launch(intent)
    }
    return { pickLauncher.launch(PickMediasRequest()) }
}

/** The editing page: the profile card under a bar with save, or placeholders while it loads. */
@Composable
fun EditProfileScreen(
    state: EditProfileState,
    onBack: () -> Unit,
    onSave: () -> Unit,
    onNickNameChange: (String) -> Unit,
    onIntroChange: (String) -> Unit,
    onSexChange: (Int) -> Unit,
    onUploadPortrait: () -> Unit,
) {
    val profile = state.profile
    Scaffold(
        topBar = {
            Toolbar(
                title = stringResource(id = R.string.title_activity_edit_profile),
                navigationIcon = { BackNavigationIcon { onBack() } },
                actions = {
                    if (profile != null) {
                        ActionItem(
                            icon = ImageVector.vectorResource(id = R.drawable.ic_round_save_24),
                            contentDescription = stringResource(id = R.string.button_save_profile),
                            onClick = onSave,
                        )
                    }
                }
            )
        },
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .padding(contentPadding)
                .padding(horizontal = dimensionResource(id = R.dimen.card_margin))
                .imePadding()
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            val dialogState = rememberDialogState()
            Dialog(
                dialogState = dialogState,
                title = { Text(text = stringResource(id = R.string.title_modify_sex)) },
                buttons = { DialogNegativeButton(text = stringResource(id = R.string.button_cancel)) }
            ) {
                ListSinglePicker(
                    itemTitles = persistentListOf(
                        stringResource(id = R.string.profile_sex_male),
                        stringResource(id = R.string.profile_sex_female)
                    ),
                    itemValues = persistentListOf(1, 2),
                    selectedPosition = when (state.sex) {
                        1 -> 0
                        2 -> 1
                        else -> -1
                    },
                    onItemSelected = { _, _, value, _ ->
                        onSexChange(value)
                        dismiss()
                    }
                )
            }
            Spacer(modifier = Modifier.height(dimensionResource(id = R.dimen.card_margin)))
            EditProfileCard(
                portrait = profile?.portrait.orEmpty(),
                name = profile?.name.orEmpty(),
                nickName = state.nickName,
                sex = state.sex,
                intro = state.intro,
                loading = profile == null,
                onNickNameChange = onNickNameChange,
                onIntroChange = onIntroChange,
                onUploadPortrait = onUploadPortrait,
                onModifySex = { dialogState.show = true }
            )
            Spacer(modifier = Modifier.height(dimensionResource(id = R.dimen.card_margin)))
        }
    }
}

@Composable
fun EditProfileCard(
    portrait: String,
    name: String,
    nickName: String,
    sex: Int,
    intro: String,
    // birthdayShowStatus: Boolean,
    // birthdayTime: Long,
    loading: Boolean,
    onNickNameChange: ((String) -> Unit)? = null,
    onIntroChange: ((String) -> Unit)? = null,
    onUploadPortrait: (() -> Unit)? = null,
    onModifySex: (() -> Unit)? = null,
    color: Color = ExtendedTheme.colors.background,
) {
    val context = LocalContext.current
    Card(elevation = 0.dp, backgroundColor = color) {
        Column(
            modifier = Modifier
                .padding(dimensionResource(id = R.dimen.card_margin))
                .fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .align(Alignment.CenterHorizontally)
                    .placeholder(visible = loading)
            ) {
                AsyncImage(
                    model = StringUtil.getAvatarUrl(portrait),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize()
                )
                IconButton(
                    modifier = Modifier
                        .background(color = Color(0x77000000))
                        .fillMaxSize(),
                    onClick = {
                        onUploadPortrait?.invoke()
                    }
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PhotoCamera,
                        contentDescription = stringResource(id = R.string.upload_portrait),
                        modifier = Modifier
                            .size(24.dp)
                            .align(Alignment.Center),
                        tint = Color.White
                    )
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
            ConstraintLayout(
                modifier = Modifier.fillMaxWidth()
            ) {
                val (
                    nameTitle,
                    nameContent,
                    nickNameTitle,
                    nickNameContent,
                    sexTitle,
                    sexContent,
                    introTitle,
                    introContent,
                ) = createRefs()

                val titleEndBarrier = createEndBarrier(
                    nameTitle,
                    nickNameTitle,
                    sexTitle,
                    introTitle,
                    margin = 24.dp
                )

                Text(
                    text = stringResource(id = R.string.title_username),
                    color = ExtendedTheme.colors.textDisabled,
                    modifier = Modifier.constrainAs(nameTitle) {
                        top.linkTo(parent.top)
                        bottom.linkTo(nameContent.bottom)
                        start.linkTo(parent.start)
                    }
                )
                Text(
                    text = name,
                    color = ExtendedTheme.colors.textDisabled,
                    modifier = Modifier
                        .constrainAs(nameContent) {
                            top.linkTo(nameTitle.top)
                            bottom.linkTo(nameTitle.bottom)
                            start.linkTo(titleEndBarrier)
                            end.linkTo(parent.end)
                            width = Dimension.fillToConstraints
                        }
                        .placeholder(visible = loading)
                )

                Text(
                    text = stringResource(id = R.string.title_nickname),
                    color = ExtendedTheme.colors.textDisabled,
                    modifier = Modifier.constrainAs(nickNameTitle) {
                        top.linkTo(nameTitle.bottom, margin = 16.dp)
                        start.linkTo(parent.start)
                    }
                )
                Row(
                    modifier = Modifier
                        .constrainAs(nickNameContent) {
                            top.linkTo(nickNameTitle.top)
                            bottom.linkTo(nickNameTitle.bottom)
                            start.linkTo(titleEndBarrier)
                            end.linkTo(parent.end)
                            width = Dimension.fillToConstraints
                        }
                        .placeholder(visible = loading)
                ) {
                    BaseTextField(
                        value = nickName,
                        onValueChange = { onNickNameChange?.invoke(it) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        maxLines = 1,
                    )
                }

                Text(
                    text = stringResource(id = R.string.profile_sex),
                    color = ExtendedTheme.colors.textDisabled,
                    modifier = Modifier.constrainAs(sexTitle) {
                        top.linkTo(nickNameTitle.bottom, margin = 16.dp)
                        start.linkTo(parent.start)
                    }
                )
                Row(
                    modifier = Modifier
                        .constrainAs(sexContent) {
                            top.linkTo(sexTitle.top)
                            bottom.linkTo(sexTitle.bottom)
                            start.linkTo(titleEndBarrier)
                            end.linkTo(parent.end)
                            width = Dimension.fillToConstraints
                        }
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onModifySex?.invoke() }
                        .placeholder(visible = loading)
                ) {
                    Text(
                        text = stringResource(
                            id = when (sex) {
                                1 -> R.string.profile_sex_male
                                2 -> R.string.profile_sex_female
                                else -> R.string.profile_sex_unset
                            }
                        ),
                        color = ExtendedTheme.colors.text,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        imageVector = ImageVector.vectorResource(id = R.drawable.ic_round_chevron_right),
                        contentDescription = null,
                        tint = ExtendedTheme.colors.textSecondary,
                        modifier = Modifier
                            .size(16.dp)
                            .align(Alignment.CenterVertically)
                    )
                }

                Text(
                    text = stringResource(id = R.string.title_intro),
                    color = ExtendedTheme.colors.textDisabled,
                    modifier = Modifier.constrainAs(introTitle) {
                        top.linkTo(sexTitle.bottom, margin = 16.dp)
                        start.linkTo(parent.start)
                    }
                )
                CounterTextField(
                    value = intro,
                    onValueChange = { onIntroChange?.invoke(it) },
                    maxLength = 500,
                    countWhitespace = false,
                    onLengthBeyondRestrict = { context.toastShort(R.string.toast_intro_length_beyond_restrict) },
                    placeholder = { Text(text = stringResource(id = R.string.tip_no_intro)) },
                    modifier = Modifier
                        .constrainAs(introContent) {
                            top.linkTo(introTitle.top)
                            start.linkTo(titleEndBarrier)
                            end.linkTo(parent.end)
                            width = Dimension.fillToConstraints
                        }
                        .placeholder(visible = loading),
                )
            }
        }
    }
}

@Preview
@Composable
fun EditProfileCardPreview() {
    EditProfileCard(
        portrait = "",
        name = "test",
        nickName = "test",
        sex = 1,
        intro = "test",
        loading = false,
        color = Color.White
    )
}

@Preview
@Composable
fun EditProfileCardLoadingPreview() {
    EditProfileCard(
        portrait = "",
        name = "",
        nickName = "",
        sex = 0,
        intro = "",
        loading = true,
        color = Color.White
    )
}
