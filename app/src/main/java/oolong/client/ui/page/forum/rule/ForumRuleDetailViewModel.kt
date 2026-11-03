package oolong.client.ui.page.forum.rule

import androidx.lifecycle.ViewModel
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import oolong.client.arch.loadable
import oolong.client.repository.ForumRepository
import oolong.client.ui.navigation.ForumRuleDetailKey

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
