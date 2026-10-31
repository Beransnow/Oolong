package com.huanchengfly.tieba.post.ui.page.forum.rule

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.huanchengfly.tieba.post.arch.loadable
import com.huanchengfly.tieba.post.repository.ForumRepository
import com.huanchengfly.tieba.post.ui.page.destinations.ForumRuleDetailPageDestination
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class ForumRuleDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    forumRepository: ForumRepository,
) : ViewModel() {
    private val forumId = ForumRuleDetailPageDestination.argsFrom(savedStateHandle).forumId

    val ruleDetail = loadable { forumRepository.forumRuleDetail(forumId) }
}
