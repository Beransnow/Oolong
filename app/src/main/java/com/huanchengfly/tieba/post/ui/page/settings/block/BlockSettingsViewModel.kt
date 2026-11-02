package com.huanchengfly.tieba.post.ui.page.settings.block

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.huanchengfly.tieba.post.arch.Actions
import com.huanchengfly.tieba.post.model.BlockRule
import com.huanchengfly.tieba.post.repository.BlockListRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** The block settings: how many rules are on, and keeping a word out for a while. */
@HiltViewModel
class BlockSettingsViewModel @Inject constructor(
    private val repository: BlockListRepository,
) : ViewModel() {
    private val actions = Actions(viewModelScope)

    /** The rules in force now, or null while they are read. */
    val activeRules: StateFlow<Int?> = repository.blockRules
        .map { rules -> rules.count { it.isActive(System.currentTimeMillis()) } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Blocks content containing [word] for the next [SpoilerDays] days. */
    fun blockSpoiler(word: String) {
        val words = word.split(' ', '　').filter { it.isNotBlank() }
        if (words.isEmpty()) return
        actions.launch("spoiler") {
            repository.add(
                BlockRule(
                    match = BlockRule.Match.Keyword(words),
                    expiresAt = System.currentTimeMillis() + SpoilerDays * DayMillis,
                ),
            )
        }
    }

    companion object {
        const val SpoilerDays = 7
    }
}
