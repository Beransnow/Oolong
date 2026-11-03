package oolong.client.ui.page.settings.custom

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.collections.immutable.persistentListOf
import oolong.client.R
import oolong.client.dataStore
import oolong.client.model.Agree
import oolong.client.model.Post
import oolong.client.model.PostContent
import oolong.client.model.SubPost
import oolong.client.model.TextSpan
import oolong.client.model.User
import oolong.client.ui.common.LocalAccount
import oolong.client.ui.common.prefs.PrefsScreen
import oolong.client.ui.common.theme.compose.ExtendedTheme
import oolong.client.ui.navigation.Navigator
import oolong.client.ui.page.LocalNavigator
import oolong.client.ui.page.thread.PostCardOptions
import oolong.client.ui.page.thread.PostItem
import oolong.client.ui.page.thread.ThreadFloor
import oolong.client.ui.widgets.compose.BackNavigationIcon
import oolong.client.ui.widgets.compose.GlassScaffold
import oolong.client.ui.widgets.compose.GlassTopBar
import oolong.client.ui.widgets.compose.GlassTopBarTitle
import oolong.client.ui.widgets.compose.MyBackHandler
import oolong.client.ui.widgets.compose.Slider
import oolong.client.utils.appPreferences
import kotlin.math.roundToInt

/** The app's text scale, from 80% to 130% in steps of 5%. */
object FontScale {
    const val MIN = 0.8f
    const val STEP = 0.05f
    const val STEPS = 10

    fun of(step: Int): Float = MIN + step * STEP

    fun stepOf(scale: Float): Int = ((scale - MIN) / STEP).roundToInt().coerceIn(0, STEPS)

    /** What the size at [step] is called. */
    fun nameOf(step: Int): Int = when (step) {
        0, 1 -> R.string.text_size_small
        2, 3 -> R.string.text_size_little_small
        4 -> R.string.text_size_default
        5, 6 -> R.string.text_size_little_large
        7, 8 -> R.string.text_size_large
        else -> R.string.text_size_very_large
    }
}

/**
 * The app's text size: a post shown at the chosen size, and a slider to choose it. Only the post
 * follows the slider; leaving the page saves the size, which every page then takes.
 */
@Composable
fun FontSizePage(
    navigator: Navigator = LocalNavigator.current,
) {
    val context = LocalContext.current
    val initial = remember { context.appPreferences.fontScale }
    var step by rememberSaveable { mutableIntStateOf(FontScale.stepOf(initial)) }
    val scale = FontScale.of(step)

    fun leave() {
        navigator.navigateUp()
        if (scale != initial) context.appPreferences.fontScale = scale
    }
    MyBackHandler(enabled = true) { leave() }

    GlassScaffold(
        largeTitle = true,
        backgroundColor = Color.Transparent,
        topBar = {
            GlassTopBar(
                title = { GlassTopBarTitle(text = stringResource(id = R.string.title_custom_font_size)) },
                navigationIcon = { BackNavigationIcon(onBackPressed = { leave() }) },
            )
        },
    ) { paddingValues ->
        PrefsScreen(
            dataStore = context.dataStore,
            dividerThickness = 0.dp,
            modifier = Modifier.fillMaxSize(),
            contentPadding = paddingValues,
        ) {
            prefsGroup(header = { FontSizePreview(scale) }) {
                prefsItem {
                    FontSizeSlider(
                        step = step,
                        onStepChange = { step = it },
                    )
                }
            }
        }
    }
}

/** A floor and a reply in it, as the thread page shows them at [scale]. */
@Composable
private fun FontSizePreview(scale: Float) {
    val account = LocalAccount.current
    val asker = User(
        id = 1,
        name = account?.name.orEmpty(),
        nameShow = account?.nameShow ?: account?.name ?: stringResource(id = R.string.app_name),
        portrait = account?.portrait.orEmpty(),
    )
    val app = User(id = 2, name = "", nameShow = stringResource(id = R.string.app_name), portrait = "")
    val question = stringResource(id = R.string.bubble_want_change_font_size)
    val answer = stringResource(id = R.string.bubble_change_font_size)
    val item = remember(asker, app, question, answer) {
        PostItem(
            Post(
                id = 2,
                floor = 2,
                title = "",
                time = System.currentTimeMillis() / 1000,
                author = asker,
                content = text(question),
                agree = Agree(count = 0, agreed = false),
                subPosts = persistentListOf(SubPost(id = 3, author = app, content = text(answer), time = 0)),
                subPostCount = 1,
            )
        )
    }
    val density = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(density.density, scale)) {
        // Only a picture of a post: taps and long presses do nothing
        Box(
            modifier = Modifier
                .padding(vertical = 8.dp)
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                        }
                    }
                }
        ) {
            ThreadFloor(
                item = item,
                options = PostCardOptions(threadAuthorId = 0),
                onEvent = {},
                listState = rememberLazyListState(),
                key = "Preview",
                startsRule = true,
            )
        }
    }
}

private fun text(value: String) = persistentListOf<PostContent>(PostContent.Text(persistentListOf(TextSpan.Plain(value))))

@Composable
private fun FontSizeSlider(step: Int, onStepChange: (Int) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(id = R.string.title_custom_font_size),
                color = ExtendedTheme.colors.text,
                fontSize = 16.sp,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "${stringResource(id = FontScale.nameOf(step))} · ${(FontScale.of(step) * 100).roundToInt()}%",
                color = ExtendedTheme.colors.textSecondary,
                fontSize = 14.sp,
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(text = "A", color = ExtendedTheme.colors.textSecondary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Slider(
                value = step.toFloat(),
                onValueChange = { onStepChange(it.roundToInt()) },
                valueRange = 0f..FontScale.STEPS.toFloat(),
                steps = FontScale.STEPS - 1,
                modifier = Modifier.weight(1f),
            )
            Text(text = "A", color = ExtendedTheme.colors.textSecondary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
    }
}
