package com.huanchengfly.tieba.post.ui.page.picker

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
class ImagePickerScreenshotTest {
    @get:Rule
    val legacyApp = LegacyAppRule()

    @Test
    fun picking() {
        captureScreenshot { ImagePickerPreviewScreen(ImagePickerPreviewData.multiple, PickerAccess.All) }
    }

    @Test
    fun onlySelectedPhotos() {
        captureScreenshot { ImagePickerPreviewScreen(ImagePickerPreviewData.multiple, PickerAccess.Selected) }
    }

    @Test
    fun noAccess() {
        captureScreenshot { ImagePickerPreviewScreen(ImagePickerState(maxItems = 9), PickerAccess.Denied) }
    }
}
