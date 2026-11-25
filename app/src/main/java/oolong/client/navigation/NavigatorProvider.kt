package oolong.client.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import oolong.client.navigation.Navigator

val LocalNavigator = staticCompositionLocalOf<Navigator> { error("No navigator is available") }

@Composable
fun ProvideNavigator(
    navigator: Navigator,
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(LocalNavigator provides navigator, content = content)
}
