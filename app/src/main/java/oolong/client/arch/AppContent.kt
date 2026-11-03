package oolong.client.arch

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import oolong.client.repository.AccountRepository
import oolong.client.ui.common.ProvideAccounts
import oolong.client.ui.common.theme.compose.TiebaLiteTheme
import oolong.client.ui.common.windowsizeclass.LocalWindowSizeClass
import oolong.client.ui.common.windowsizeclass.calculateWindowSizeClass
import oolong.client.ui.utils.rememberSystemUiController
import oolong.client.utils.ThemeUtil

/**
 * What every activity's content is drawn in: the theme, the system bars clear over it with icons
 * to suit it, the accounts and the window's size class. The activity draws edge to edge.
 *
 * @param dark set, makes the theme dark or light whatever the settings say, as for an editor
 */
@Composable
fun AppContent(
    activity: Activity,
    accountRepository: AccountRepository,
    dark: Boolean? = null,
    content: @Composable () -> Unit,
) {
    TiebaLiteTheme(dark = dark) {
        val systemUiController = rememberSystemUiController()
        SideEffect {
            systemUiController.setStatusBarColor(Color.Transparent, darkIcons = dark != true && ThemeUtil.isStatusBarFontDark())
            systemUiController.setNavigationBarColor(
                Color.Transparent,
                darkIcons = dark != true && ThemeUtil.isNavigationBarFontDark(),
                navigationBarContrastEnforced = false,
            )
        }
        ProvideAccounts(accountRepository) {
            CompositionLocalProvider(LocalWindowSizeClass provides calculateWindowSizeClass(activity = activity), content = content)
        }
    }
}
