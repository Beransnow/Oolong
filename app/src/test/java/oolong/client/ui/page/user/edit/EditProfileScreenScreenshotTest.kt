package oolong.client.ui.page.user.edit

import oolong.client.model.UserProfile
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
class EditProfileScreenScreenshotTest {
    @get:Rule
    val legacyApp = LegacyAppRule()

    @Test
    fun loaded() {
        val profile = UserProfile(id = 1, name = "username", nameShow = "昵称", portrait = "", sex = 2, intro = "一段简介")
        captureScreenshot {
            EditProfileScreen(
                state = EditProfileState(profile = profile, nickName = "昵称", sex = 2, intro = "一段简介"),
                onBack = {}, onSave = {}, onNickNameChange = {}, onIntroChange = {}, onSexChange = {}, onUploadPortrait = {},
            )
        }
    }
}
