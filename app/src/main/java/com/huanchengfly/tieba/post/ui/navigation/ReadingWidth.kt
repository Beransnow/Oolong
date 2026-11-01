package com.huanchengfly.tieba.post.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.huanchengfly.tieba.post.arch.BaseComposeActivity.Companion.LocalWindowSizeClass
import com.huanchengfly.tieba.post.ui.common.windowsizeclass.WindowSizeClass

/** The widest a page meant for reading gets; wider, its lines get too long to follow. */
val MaxReadingWidth = 680.dp

/**
 * Centres [content] at most [MaxReadingWidth] wide. The page is then away from the window's
 * sides, so it does not pad for their insets, and it takes its own width as its window.
 */
@Composable
fun ReadingWidth(content: @Composable () -> Unit) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        val width = minOf(maxWidth, MaxReadingWidth)
        val margin = (maxWidth - width) / 2
        val sizeClass = WindowSizeClass.calculateFromSize(DpSize(width, maxHeight))
        Box(
            modifier = Modifier
                .widthIn(max = MaxReadingWidth)
                .fillMaxSize()
                .consumeWindowInsets(PaddingValues(horizontal = margin))
        ) {
            CompositionLocalProvider(
                LocalWindowSizeClass provides sizeClass,
                content = content,
            )
        }
    }
}
