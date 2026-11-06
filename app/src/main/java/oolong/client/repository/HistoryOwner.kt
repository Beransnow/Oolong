package oolong.client.repository

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import oolong.client.arch.ApplicationScope
import javax.inject.Inject
import javax.inject.Singleton

/** Whose browsing history is read and written: the current account's uid, empty when logged out. */
interface HistoryOwner {
    val uid: StateFlow<String>
}

@Singleton
class AccountHistoryOwner @Inject constructor(
    accounts: AccountRepository,
    @ApplicationScope scope: CoroutineScope,
) : HistoryOwner {
    override val uid: StateFlow<String> = accounts.current
        .map { it?.uid.orEmpty() }
        .stateIn(scope, SharingStarted.Eagerly, accounts.current.value?.uid.orEmpty())
}
