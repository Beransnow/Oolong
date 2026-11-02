package com.huanchengfly.tieba.post.ui.page.settings.block

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.huanchengfly.tieba.post.R
import com.huanchengfly.tieba.post.arch.Actions
import com.huanchengfly.tieba.post.arch.MessageController
import com.huanchengfly.tieba.post.model.BlockRule
import com.huanchengfly.tieba.post.repository.BlockListRepository
import com.huanchengfly.tieba.post.repository.BlockRuleBackup
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** The block settings: how many rules are on, and keeping a word out for a while. */
@HiltViewModel
class BlockSettingsViewModel @Inject constructor(
    private val repository: BlockListRepository,
    private val messages: MessageController,
    @ApplicationContext private val context: Context,
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
                    condition = BlockRule.Condition.Text(BlockRule.Pattern.Words(words)),
                    expiresAt = System.currentTimeMillis() + SpoilerDays * DayMillis,
                ),
            )
        }
    }

    /** Writes every rule to [uri], as [BlockRuleBackup] has them. */
    fun export(uri: Uri) = actions.launch("export") {
        runCatching {
            val rules = repository.blockRules.first()
            val text = BlockRuleBackup.encode(rules)
            withContext(Dispatchers.IO) {
                checkNotNull(context.contentResolver.openOutputStream(uri)).use { it.write(text.toByteArray()) }
            }
            rules.size
        }
            .onSuccess { messages.show(R.string.toast_rules_exported, it) }
            .onFailure { messages.showError(R.string.toast_rules_export_failed, it) }
    }

    /** Adds the rules in the backup at [uri] that are not saved already. */
    fun import(uri: Uri) = actions.launch("import") {
        runCatching {
            val text = withContext(Dispatchers.IO) {
                checkNotNull(context.contentResolver.openInputStream(uri)).use { it.readBytes().decodeToString() }
            }
            repository.import(BlockRuleBackup.decode(text))
        }
            .onSuccess { messages.show(R.string.toast_rules_imported, it) }
            .onFailure { messages.show(R.string.toast_rules_import_failed) }
    }

    companion object {
        const val SpoilerDays = 7
    }
}
