package com.huanchengfly.tieba.post.utils

import android.content.Context
import android.util.Log
import com.huanchengfly.tieba.core.network.forum.ForumGuideResponse
import com.huanchengfly.tieba.core.network.sign.MSignResponse
import com.huanchengfly.tieba.core.network.sign.SignRemoteDataSource
import com.huanchengfly.tieba.core.network.sign.SignResponse
import com.huanchengfly.tieba.post.api.legacyLikeRemoteDataSource
import com.huanchengfly.tieba.post.api.legacySignRemoteDataSource
import com.huanchengfly.tieba.post.api.retrofit.exception.getErrorCode
import com.huanchengfly.tieba.post.api.retrofit.exception.getErrorMessage
import com.huanchengfly.tieba.post.models.SignDataBean
import com.huanchengfly.tieba.post.models.database.Account
import java.lang.ref.WeakReference
import java.util.concurrent.ThreadLocalRandom
import kotlin.properties.Delegates
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext

abstract class IOKSigner(
    context: Context
) {
    private val contextWeakReference: WeakReference<Context> = WeakReference(context)

    val context: Context
        get() = contextWeakReference.get()!!

    abstract suspend fun start(): Boolean

    fun signFlow(signDataBean: SignDataBean): Flow<SignResponse> = flow {
        emit(legacySignRemoteDataSource.sign(signDataBean.forumId.toLong(), signDataBean.forumName, signDataBean.tbs))
    }

    fun getSignDelay(): Long {
        return if (context.appPreferences.oksignSlowMode) {
            ThreadLocalRandom.current().nextInt(3500, 8000).toLong()
        } else {
            2000
        }
    }
}

/*
class MultiAccountSigner(
        context: Context
) : IOKSigner(context) {
    private val accounts: MutableList<Account> = mutableListOf()

    override suspend fun start() {
        accounts.clear()
        accounts.addAll(AccountUtil.allAccounts)
    }

    interface ProgressListener {
        fun onStart(
                total: Int
        )

        fun onProgressStart(
                signDataBean: SignDataBean,
                current: Int,
                total: Int
        )

        fun onProgressFinish(
                signResultBean: SignResponse,
                current: Int,
                total: Int
        )

        fun onFinish(
                success: Boolean,
                signedCount: Int,
                total: Int
        )

        fun onFailure(
                current: Int,
                total: Int,
                errorCode: Int,
                errorMsg: String
        )
    }
}
*/

class SingleAccountSigner(
    context: Context,
    private val account: Account
) : IOKSigner(context) {
    companion object {
        const val TAG = "SingleAccountSigner"
    }

    private val signData: MutableList<SignDataBean> = mutableListOf()
    private var position = 0
    private var successCount = 0
    private var totalCount = 0
    private var mSignCount = 0

    var lastFailure: Throwable? = null

    private var mProgressListener: ProgressListener? = null

    fun setProgressListener(listener: ProgressListener?): SingleAccountSigner {
        mProgressListener = listener
        return this
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override suspend fun start(): Boolean {
        var result = false
        signData.clear()
        var userName: String by Delegates.notNull()
        var tbs: String by Delegates.notNull()
        Log.i(TAG, "start")
        AccountUtil.fetchAccountFlow(account)
            .flatMapConcat { account ->
                userName = account.name
                tbs = account.tbs
                flow { emit(legacySignRemoteDataSource.forumList()) }
            }
            .zip(
                flow { emit(followedForums()) }
            ) { getForumListBean, likeForums ->
                val useMSign = context.appPreferences.oksignUseOfficialOksign
                val mSignLevel = getForumListBean.level.toIntOrNull() ?: 0
                val mSignMax = getForumListBean.msignStepNum.toIntOrNull() ?: 0
                signData.addAll(
                    likeForums
                        .filter { it.isSign != "1" }
                        .map {
                            SignDataBean(
                                it.forumName,
                                it.forumId,
                                userName,
                                tbs,
                                (it.levelId?.toIntOrNull() ?: 0) >= mSignLevel && signData.size < mSignMax
                            )
                        }
                )
                totalCount = signData.size
                mSignCount = 0
                (if (useMSign) {
                    val mSignData = signData.filter { it.canUseMSign }
                    flow {
                        // At most MAX_MSIGN_FORUMS forums per request
                        val results = mSignData.map { it.forumId.toLong() }
                            .chunked(SignRemoteDataSource.MAX_MSIGN_FORUMS)
                            .map { legacySignRemoteDataSource.mSign(it, tbs) }
                        emit(results.flatMap { it.info })
                    }
                } else {
                    flow { emit(emptyList()) }
                })
                    .onStart {
                        withContext(Dispatchers.Main) {
                            mProgressListener?.onStart(totalCount)
                        }
                    }
                    .catch { emit(emptyList()) }
            }
            .flattenConcat()
            .flatMapConcat { mSignInfo ->
                val newSignData = if (mSignInfo.isNotEmpty()) {
                    val mSignInfoMap = mutableMapOf<String, MSignResponse.ForumResult>()
                    mSignInfo.forEach { info ->
                        info.forumId?.let { mSignInfoMap[it] = info }
                    }
                    val signedCount = mSignInfo.filter { it.signed == "1" }.size
                    successCount += signedCount
                    signData
                        .filter { !it.canUseMSign || mSignInfoMap[it.forumId]?.signed != "1" }
                } else {
                    signData.toList()
                }
                mSignCount = totalCount - newSignData.size
                newSignData
                    .asFlow()
                    .onEach {
                        position = signData.indexOf(it)
                        withContext(Dispatchers.Main) {
                            mProgressListener?.onProgressStart(
                                it,
                                position,
                                signData.size
                            )
                        }
                    }
                    .onEmpty {
                        withContext(Dispatchers.Main) {
                            mProgressListener?.onFinish(
                                successCount == totalCount,
                                successCount,
                                totalCount
                            )
                        }
                        result = true
                    }
                    .flatMapConcat { signData ->
                        // A banned or closed forum must not stop signing the remaining forums
                        signFlow(signData).catch { e ->
                            lastFailure = e
                            mProgressListener?.onFailure(
                                position,
                                totalCount,
                                e.getErrorCode(),
                                e.getErrorMessage()
                            )
                            delay(getSignDelay())
                        }
                    }
            }
            .catch { e ->
                result = false
                lastFailure = e
                mProgressListener?.onFailure(
                    position,
                    totalCount,
                    e.getErrorCode(),
                    e.getErrorMessage()
                )
                delay(getSignDelay())
            }
            .onCompletion {
                withContext(Dispatchers.Main) {
                    mProgressListener?.onFinish(
                        successCount == totalCount,
                        successCount,
                        totalCount
                    )
                }
            }
            .collect {
                result = true
                successCount += 1
                mProgressListener?.onProgressFinish(
                    signData[position],
                    it,
                    position,
                    totalCount
                )
                delay(getSignDelay())
            }
        return result
    }
}

interface ProgressListener {
    fun onStart(
        total: Int
    )

    fun onProgressStart(
        signDataBean: SignDataBean,
        current: Int,
        total: Int
    )

    fun onProgressFinish(
        signDataBean: SignDataBean,
        signResultBean: SignResponse,
        current: Int,
        total: Int
    )

    fun onFinish(
        success: Boolean,
        signedCount: Int,
        total: Int
    )

    fun onFailure(
        current: Int,
        total: Int,
        errorCode: Int,
        errorMsg: String
    )
}

/** Every forum the user follows, page by page. */
private suspend fun followedForums(): List<ForumGuideResponse.LikeForum> {
    val forums = mutableListOf<ForumGuideResponse.LikeForum>()
    var pageNo = 1
    do {
        val page = legacyLikeRemoteDataSource.forumGuide(pageNo++)
        val pageForums = page.likeForum.orEmpty()
        forums += pageForums
    } while (page.hasMore && pageForums.isNotEmpty())
    return forums
}
