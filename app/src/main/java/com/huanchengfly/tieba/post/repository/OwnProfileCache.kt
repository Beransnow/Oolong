package com.huanchengfly.tieba.post.repository

import com.huanchengfly.tieba.post.model.UserProfile
import javax.inject.Inject

/** Keeps the logged-in user's profile with their account, which shows it before it reloads. */
fun interface OwnProfileCache {
    fun store(profile: UserProfile)
}

class AccountOwnProfileCache @Inject constructor(private val accounts: AccountRepository) : OwnProfileCache {
    override fun store(profile: UserProfile) {
        val account = accounts.accountByUid(profile.id.toString())?.copy() ?: return
        account.apply {
            nameShow = profile.displayName
            portrait = profile.portrait
            intro = profile.intro
            sex = profile.sex.toString()
            fansNum = profile.fanCount.toString()
            postNum = profile.postCount.toString()
            threadNum = profile.threadCount.toString()
            concernNum = profile.followCount.toString()
            tbAge = profile.tbAge
            birthdayShowStatus = if (profile.birthdayShown) "1" else "0"
            birthdayTime = profile.birthdayTime.toString()
            tiebaUid = profile.tiebaUid
            loadSuccess = true
        }
        accounts.saveAsync(account)
    }
}
