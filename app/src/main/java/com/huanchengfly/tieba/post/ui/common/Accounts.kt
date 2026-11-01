package com.huanchengfly.tieba.post.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import com.huanchengfly.tieba.post.models.database.Account
import com.huanchengfly.tieba.post.repository.AccountActions
import com.huanchengfly.tieba.post.repository.AccountRepository

/** The current account, or null when logged out. */
val LocalAccount = staticCompositionLocalOf<Account?> { null }

val LocalAllAccounts = staticCompositionLocalOf<List<Account>> { emptyList() }

/** Switching accounts, logging in and out; does nothing in previews and tests. */
val LocalAccountActions = staticCompositionLocalOf<AccountActions> { NoAccountActions }

private object NoAccountActions : AccountActions {
    override fun switchTo(id: Int) = false

    override fun logOut(): Account? = null

    override suspend fun logIn(bduss: String, sToken: String, cookie: String?): Account =
        error("No accounts to log in to")
}

@Composable
fun ProvideAccounts(repository: AccountRepository, content: @Composable () -> Unit) {
    val account by repository.current.collectAsState()
    val accounts by repository.accounts.collectAsState()
    CompositionLocalProvider(
        LocalAccount provides account,
        LocalAllAccounts provides accounts,
        LocalAccountActions provides repository,
        content = content,
    )
}
