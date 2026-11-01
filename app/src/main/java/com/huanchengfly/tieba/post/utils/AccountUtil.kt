package com.huanchengfly.tieba.post.utils

import android.content.Context
import android.content.Intent
import android.webkit.CookieManager
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.staticCompositionLocalOf
import com.huanchengfly.tieba.core.network.account.LoginResponse
import com.huanchengfly.tieba.post.R
import com.huanchengfly.tieba.post.api.legacyAccountRemoteDataSource
import com.huanchengfly.tieba.post.arch.GlobalEvent
import com.huanchengfly.tieba.post.arch.emitGlobalEvent
import com.huanchengfly.tieba.post.database.AccountEntity
import com.huanchengfly.tieba.post.database.appDatabase
import com.huanchengfly.tieba.post.models.database.Account
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

@Stable
object AccountUtil {
    const val TAG = "AccountUtil"
    const val ACTION_SWITCH_ACCOUNT = "com.huanchengfly.tieba.post.action.SWITCH_ACCOUNT"

    val LocalAccount = staticCompositionLocalOf<Account?> { null }
    val AllAccounts = staticCompositionLocalOf<List<Account>> { emptyList() }

    @Composable
    fun LocalAccountProvider(content: @Composable () -> Unit) {
        val account by mutableCurrentAccountState
        val allAccounts by mutableAllAccountsState
        CompositionLocalProvider(
            LocalAccount provides account,
            AllAccounts provides allAccounts
        ) {
            content()
        }
    }

    @set:Synchronized
    private var mutableCurrentAccountState: MutableState<Account?> = mutableStateOf(null)

    private var mutableAllAccountsState: MutableState<List<Account>> = mutableStateOf(emptyList())

    val currentAccount
        get() = mutableCurrentAccountState.value

    val allAccounts: List<Account>
        get() = mutableAllAccountsState.value

    private val dao get() = appDatabase.accountDao()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Loads the accounts, blocking: everything after startup expects to know who is logged in. */
    fun init(context: Context) {
        val accounts = runBlocking { dao.getAll() }.map { it.toAccount() }
        val loginUser = context.getSharedPreferences("accountData", Context.MODE_PRIVATE).getInt("now", -1)
        mutableAllAccountsState.value = accounts
        mutableCurrentAccountState.value = accounts.firstOrNull { it.id == loginUser }
    }

    @JvmStatic
    fun getLoginInfo(): Account? {
        return currentAccount
    }

    @JvmStatic
    fun <T> getAccountInfo(getter: Account.() -> T): T? {
        return currentAccount?.getter()
    }

    /**
     * Saves [account] over the account with the same uid, or as a new one, and sets its id. Then
     * calls [callback] on the main thread with whether it was saved.
     */
    fun newAccount(uid: String, account: Account, callback: (Boolean) -> Unit) {
        scope.launch {
            val saved = runCatching { save(account.apply { this.uid = uid }) }.isSuccess
            withContext(Dispatchers.Main) { callback(saved) }
        }
    }

    /** Saves the account by its uid and sets its id; the current account follows if it is that one. */
    suspend fun save(account: Account) {
        account.id = dao.saveByUid(account.toEntity())
        val accounts = dao.getAll().map { it.toAccount() }
        mutableAllAccountsState.value = accounts
        val current = currentAccount
        if (current != null && current.uid == account.uid) {
            mutableCurrentAccountState.value = accounts.firstOrNull { it.id == account.id }
        }
    }

    fun saveAsync(account: Account) {
        scope.launch { save(account) }
    }

    private fun getAccountInfo(accountId: Int): Account? = allAccounts.firstOrNull { it.id == accountId }

    @JvmStatic
    fun getAccountInfoByUid(uid: String): Account? = allAccounts.firstOrNull { it.uid == uid }

    @JvmStatic
    fun getAccountInfoByBduss(bduss: String): Account? = allAccounts.firstOrNull { it.bduss == bduss }

    @JvmStatic
    fun isLoggedIn(): Boolean {
        return getLoginInfo() != null
    }

    @JvmStatic
    fun switchAccount(context: Context, id: Int): Boolean {
        context.sendBroadcast(Intent().setAction(ACTION_SWITCH_ACCOUNT))
        val account = getAccountInfo(id) ?: return false
        mutableCurrentAccountState.value = account
        GlobalScope.launch {
            emitGlobalEvent(GlobalEvent.AccountSwitched)
        }
        return context.getSharedPreferences("accountData", Context.MODE_PRIVATE).edit()
            .putInt("now", id).commit()
    }

    private fun updateAccount(
        account: Account,
        login: LoginResponse,
    ) {
        account.apply {
            uid = login.user.id
            name = login.user.name
            portrait = login.user.portrait
            tbs = login.anti.tbs
            if (uuid.isNullOrBlank()) uuid = UUID.randomUUID().toString()
        }
    }

    fun fetchAccountFlow(account: Account = getLoginInfo()!!): Flow<Account> {
        return fetchAccountFlow(account.bduss, account.sToken, account.cookie)
    }

    /**
     * Logs in with [bduss] and [sToken], which need not be the current account's, and saves the
     * account with what the server says about it.
     */
    fun fetchAccountFlow(
        bduss: String,
        sToken: String,
        cookie: String? = null
    ): Flow<Account> = flow {
        val remote = legacyAccountRemoteDataSource
        val login = coroutineScope {
            val nickname = async { remote.initNickname(bduss, sToken) }
            remote.login(bduss, sToken, firstLogin = InstallState.firstLogin == "1").also { nickname.await() }
        }
        InstallState.onLoginSucceeded()
        val account = getAccountInfoByUid(login.user.id)?.copy()?.apply {
            this.bduss = bduss
            this.sToken = sToken
            this.cookie = cookie ?: getBdussCookie(bduss)
            updateAccount(this, login)
        } ?: Account(
            login.user.id,
            login.user.name,
            bduss,
            login.anti.tbs,
            login.user.portrait,
            sToken,
            cookie ?: getBdussCookie(bduss),
        )
        account.zid = SofireUtils.fetchZid().first()
        runCatching { remote.userInfo(account.uid.toLong(), bduss, sToken) }.onSuccess { user ->
            account.nameShow = user.nameShow
            account.portrait = user.portrait
        }
        save(account)
        emit(account)
    }.flowOn(Dispatchers.IO)

    private fun Account.toEntity() = AccountEntity(
        id = id,
        uid = uid,
        name = name,
        bduss = bduss,
        tbs = tbs,
        portrait = portrait,
        stoken = sToken,
        cookie = cookie,
        nameShow = nameShow,
        intro = intro,
        sex = sex,
        fansNum = fansNum,
        postNum = postNum,
        threadNum = threadNum,
        concernNum = concernNum,
        tbAge = tbAge,
        age = age,
        birthdayShowStatus = birthdayShowStatus,
        birthdayTime = birthdayTime,
        constellation = constellation,
        tiebaUid = tiebaUid,
        loadSuccess = loadSuccess,
        uuid = uuid,
        zid = zid,
    )

    private fun AccountEntity.toAccount() = Account(
        uid = uid,
        name = name,
        bduss = bduss,
        tbs = tbs,
        portrait = portrait,
        sToken = stoken,
        cookie = cookie,
        nameShow = nameShow,
        intro = intro,
        sex = sex,
        fansNum = fansNum,
        postNum = postNum,
        threadNum = threadNum,
        concernNum = concernNum,
        tbAge = tbAge,
        age = age,
        birthdayShowStatus = birthdayShowStatus,
        birthdayTime = birthdayTime,
        constellation = constellation,
        tiebaUid = tiebaUid,
        loadSuccess = loadSuccess,
        uuid = uuid,
        zid = zid,
        id = id,
    )

    fun parseCookie(cookie: String): Map<String, String> {
        return cookie
            .split(";")
            .map { it.trim().split("=") }
            .filter { it.size > 1 }
            .associate { it.first() to it.drop(1).joinToString("=") }
    }

    @JvmStatic
    fun updateLoginInfo(cookie: String): Boolean {
        val cookies = parseCookie(cookie).mapKeys { it.key.uppercase() }
        val bduss = cookies["BDUSS"]
        val sToken = cookies["STOKEN"]
        if (bduss != null && sToken != null) {
            val account = getAccountInfoByBduss(bduss)?.copy() ?: return false
            account.sToken = sToken
            account.cookie = cookie
            saveAsync(account)
            return true
        }
        return false
    }

    fun exit(context: Context) {
        val account = getLoginInfo() ?: return
        val accounts = allAccounts.filterNot { it.id == account.id }
        mutableAllAccountsState.value = accounts
        scope.launch { dao.delete(account.id) }
        CookieManager.getInstance().removeAllCookies(null)
        if (accounts.isNotEmpty()) {
            val next = accounts[0]
            switchAccount(context, next.id)
            Toast.makeText(context, "退出登录成功，已切换至账号 " + next.nameShow, Toast.LENGTH_SHORT).show()
            return
        }
        mutableCurrentAccountState.value = null
        context.getSharedPreferences("accountData", Context.MODE_PRIVATE).edit().clear().commit()
        Toast.makeText(context, R.string.toast_exit_account_success, Toast.LENGTH_SHORT).show()
    }

    fun getSToken(): String? {
        val account = getLoginInfo()
        return account?.sToken
    }

    fun getCookie(): String? {
        val account = getLoginInfo()
        return account?.cookie
    }

    fun getUid(): String? {
        val account = getLoginInfo()
        return account?.uid
    }

    fun getBduss(): String? {
        val account = getLoginInfo()
        return account?.bduss
    }

    @JvmStatic
    fun getBdussCookie(): String? {
        val bduss = getBduss()
        return if (bduss != null) {
            getBdussCookie(bduss)
        } else null
    }

    fun getBdussCookie(bduss: String): String {
        return "BDUSS=$bduss; Path=/; Max-Age=315360000; Domain=.baidu.com; Httponly"
    }
}