package com.huanchengfly.tieba.post.ui.page.forum.rule

import androidx.lifecycle.ViewModel
import com.huanchengfly.tieba.post.arch.loadable
import com.huanchengfly.tieba.post.repository.ForumRepository
import com.huanchengfly.tieba.post.ui.navigation.ForumRuleDetailKey
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel

@HiltViewModel(assistedFactory = ForumRuleDetailViewModel.Factory::class)
class ForumRuleDetailViewModel @AssistedInject constructor(
    @Assisted key: ForumRuleDetailKey,
    forumRepository: ForumRepository,
) : ViewModel() {
    private val forumId = key.forumId

    val ruleDetail = loadable { forumRepository.forumRuleDetail(forumId) }

    @AssistedFactory
    interface Factory {
        fun create(key: ForumRuleDetailKey): ForumRuleDetailViewModel
    }
}
