package oolong.client.ui.page.settings.block

import oolong.client.model.BlockRule
import oolong.client.model.BlockRule.Condition
import oolong.client.model.BlockRule.Pattern
import oolong.client.model.ContentKind
import oolong.client.testing.LegacyAppRule
import oolong.client.testing.captureScreenshot
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
