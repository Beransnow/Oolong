package com.huanchengfly.tieba.post.ui.page.settings.block

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.huanchengfly.tieba.post.arch.Actions
import com.huanchengfly.tieba.post.model.BlockRule
import com.huanchengfly.tieba.post.model.FlatCondition
import com.huanchengfly.tieba.post.model.flat
import com.huanchengfly.tieba.post.repository.BlockListRepository
import com.huanchengfly.tieba.post.ui.navigation.BlockRuleKey
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update

@Immutable
data class BlockRuleState(
    /** The rule as edited so far; null while the saved one is read. */
    val draft: BlockRule? = null,
    val isNew: Boolean = true,
    /** When the page opened, for how long the rule has left. */
    val now: Long = System.currentTimeMillis(),
) {
    /** The draft's tests as rows, or null when they are joined more deeply than rows can show. */
    val conditions: FlatCondition? get() = draft?.condition?.flat()

    /** Whether every test of the draft tests something, so it can be saved. */
    val canSave: Boolean get() = draft?.condition?.isComplete == true
}

/** Making a rule, or changing one: its tests and the parts of its sentence. */
@HiltViewModel(assistedFactory = BlockRuleViewModel.Factory::class)
class BlockRuleViewModel @AssistedInject constructor(
    @Assisted private val key: BlockRuleKey,
    private val repository: BlockListRepository,
) : ViewModel() {
    private val actions = Actions(viewModelScope)
    private val _state = MutableStateFlow(BlockRuleState(isNew = key.id == 0L))
    val state: StateFlow<BlockRuleState> = _state.asStateFlow()

    private val closes = Channel<Unit>(Channel.CONFLATED)

    /** Fires once the rule is saved or deleted. */
    val closed = closes.receiveAsFlow()

    init {
        actions.launch("load") {
            val rule = if (key.id == 0L) {
                BlockRule(condition = conditionOf(ConditionField.TitleOrBody))
            } else {
                repository.blockRules.first().firstOrNull { it.id == key.id }
            }
            if (rule == null) closes.send(Unit) else _state.update { it.copy(draft = rule) }
        }
    }

    fun edit(transform: (BlockRule) -> BlockRule) {
        _state.update { state -> state.copy(draft = state.draft?.let(transform)) }
    }

    private fun editTests(transform: (FlatCondition) -> FlatCondition) = edit { rule ->
        val flat = rule.condition.flat() ?: return@edit rule
        rule.copy(condition = transform(flat).toCondition())
    }

    /** Joins the tests all by and when [all], all by or otherwise. */
    fun setJoin(all: Boolean) = editTests { it.copy(all = all) }

    fun setTest(index: Int, test: FlatCondition.Test) = editTests { flat ->
        flat.copy(tests = flat.tests.toMutableList().also { if (index in it.indices) it[index] = test })
    }

    fun addTest(field: ConditionField) = editTests { flat -> flat.copy(tests = flat.tests + FlatCondition.Test(conditionOf(field))) }

    fun removeTest(index: Int) = editTests { flat -> flat.copy(tests = flat.tests.filterIndexed { i, _ -> i != index }) }

    /** Sets the rule to end [days] from now, or never with null. */
    fun setExpiry(days: Int?) = edit { it.copy(expiresAt = days?.let { d -> _state.value.now + d * DayMillis }) }

    fun save() {
        val state = _state.value
        val draft = state.draft ?: return
        if (!state.canSave) return
        actions.launch("save") {
            val rule = draft.copy(condition = draft.condition.cleaned())
            if (state.isNew) repository.add(rule) else repository.update(rule)
            closes.send(Unit)
        }
    }

    fun delete() {
        val draft = _state.value.draft ?: return
        actions.launch("delete") {
            repository.remove(listOf(draft.id))
            closes.send(Unit)
        }
    }

    @AssistedFactory
    interface Factory {
        fun create(key: BlockRuleKey): BlockRuleViewModel
    }
}
