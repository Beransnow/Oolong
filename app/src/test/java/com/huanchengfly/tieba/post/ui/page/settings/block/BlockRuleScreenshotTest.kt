package com.huanchengfly.tieba.post.ui.page.settings.block

import com.huanchengfly.tieba.post.model.BlockRule
import com.huanchengfly.tieba.post.model.BlockRule.Condition
import com.huanchengfly.tieba.post.model.BlockRule.Pattern
import com.huanchengfly.tieba.post.model.ContentKind
import com.huanchengfly.tieba.post.testing.LegacyAppRule
import com.huanchengfly.tieba.post.testing.captureScreenshot
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "+w393dp-h851dp-xxhdpi")
class BlockRuleScreenshotTest {
    @get:Rule
    val legacyApp = LegacyAppRule()

    private fun screen(state: BlockRuleState) = captureScreenshot {
        BlockRuleScreen(
            state = state,
            onBack = {},
            onEdit = {},
            onJoin = {},
            onTest = { _, _ -> },
            onAddTest = {},
            onRemoveTest = {},
            onExpiry = {},
            onSave = {},
            onDelete = {},
        )
    }

    @Test
    fun newKeyword() = screen(
        BlockRuleState(draft = BlockRule(condition = Condition.Text(Pattern.Words(listOf("剧透")))), isNew = true, now = 0),
    )

    @Test
    fun editedRegex() = screen(
        BlockRuleState(
            draft = BlockRule(
                id = 5,
                condition = Condition.AllOf(
                    listOf(
                        Condition.Forum("星穹铁道"),
                        Condition.Not(Condition.Text(Pattern.Regex("互关|互粉"), BlockRule.TextField.Title)),
                        Condition.LevelBelow(3),
                        Condition.Only(ContentKind.Emoticon),
                    ),
                ),
                action = BlockRule.Action.Hide,
                scope = BlockRule.Scope.Feeds,
                expiresAt = 7 * DayMillis,
            ),
            isNew = false,
            now = 0,
        ),
    )

    @Test
    @Config(qualifiers = "+en")
    fun editedRegexEnglish() = screen(
        BlockRuleState(
            draft = BlockRule(
                id = 5,
                condition = Condition.AllOf(
                    listOf(
                        Condition.Forum("星穹铁道"),
                        Condition.Not(Condition.Text(Pattern.Regex("互关|互粉"), BlockRule.TextField.Title)),
                        Condition.LevelBelow(3),
                        Condition.Only(ContentKind.Emoticon),
                    ),
                ),
                action = BlockRule.Action.Hide,
                scope = BlockRule.Scope.Feeds,
                expiresAt = 7 * DayMillis,
            ),
            isNew = false,
            now = 0,
        ),
    )
}
