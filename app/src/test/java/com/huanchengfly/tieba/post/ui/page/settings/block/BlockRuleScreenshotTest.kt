package com.huanchengfly.tieba.post.ui.page.settings.block

import com.huanchengfly.tieba.post.model.BlockRule
import com.huanchengfly.tieba.post.model.BlockRule.Match
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
@Config(qualifiers = "w393dp-h851dp-xxhdpi")
class BlockRuleScreenshotTest {
    @get:Rule
    val legacyApp = LegacyAppRule()

    private fun screen(state: BlockRuleState) = captureScreenshot {
        BlockRuleScreen(state = state, onBack = {}, onEdit = {}, onKind = {}, onExpiry = {}, onSave = {}, onDelete = {})
    }

    @Test
    fun newKeyword() = screen(
        BlockRuleState(draft = BlockRule(match = Match.Keyword(listOf("剧透"))), isNew = true, now = 0),
    )

    @Test
    fun editedRegex() = screen(
        BlockRuleState(
            draft = BlockRule(
                id = 5,
                match = Match.Keyword(listOf("互关|互粉"), regex = true),
                action = BlockRule.Action.Hide,
                scope = BlockRule.Scope.Feeds,
                inForum = "星穹铁道",
                titleOnly = true,
                expiresAt = 7 * DayMillis,
            ),
            isNew = false,
            now = 0,
        ),
    )
}
