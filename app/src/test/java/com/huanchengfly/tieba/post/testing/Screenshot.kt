package com.huanchengfly.tieba.post.testing

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import com.github.takahirom.roborazzi.captureRoboImage
import com.huanchengfly.tieba.post.ui.common.theme.compose.TiebaLiteTheme
import com.huanchengfly.tieba.post.ui.page.LocalNavigator
import com.ramcosta.composedestinations.navigation.EmptyDestinationsNavigator

/**
 * Renders [content] in the app theme and compares it with the stored screenshot named after the
 * calling test (`recordRoborazziDebug` records, `verifyRoborazziDebug` compares).
 */
fun captureScreenshot(content: @Composable () -> Unit) {
    captureRoboImage {
        CompositionLocalProvider(LocalNavigator provides EmptyDestinationsNavigator) {
            TiebaLiteTheme {
                Box(Modifier.background(MaterialTheme.colors.background)) {
                    content()
                }
            }
        }
    }
}
