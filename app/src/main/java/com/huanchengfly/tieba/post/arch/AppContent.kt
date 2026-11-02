package com.huanchengfly.tieba.post.arch

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import com.huanchengfly.tieba.post.repository.AccountRepository
import com.huanchengfly.tieba.post.ui.common.ProvideAccounts
import com.huanchengfly.tieba.post.ui.common.theme.compose.TiebaLiteTheme
import com.huanchengfly.tieba.post.ui.common.windowsizeclass.LocalWindowSizeClass
import com.huanchengfly.tieba.post.ui.common.windowsizeclass.calculateWindowSizeClass
import com.huanchengfly.tieba.post.ui.utils.rememberSystemUiController
import com.huanchengfly.tieba.post.utils.ThemeUtil

/**
 * What every activity's content is drawn in: the theme, the system bars clear over it with icons
 * to suit it, the accounts and the window's size class. The activity draws edge to edge.
 */
@Composable
fun AppContent(
    activity: Activity,
    accountRepository: AccountRepository,
    content: @Composable () -> Unit,
) {
    TiebaLiteTheme {
        val systemUiController = rememberSystemUiController()
        SideEffect {
            systemUiController.setStatusBarColor(Color.Transparent, darkIcons = ThemeUtil.isStatusBarFontDark())
            systemUiController.setNavigationBarColor(
                Color.Transparent,
                darkIcons = ThemeUtil.isNavigationBarFontDark(),
                navigationBarContrastEnforced = false,
            )
        }
        ProvideAccounts(accountRepository) {
            CompositionLocalProvider(LocalWindowSizeClass provides calculateWindowSizeClass(activity = activity), content = content)
        }
    }
}
