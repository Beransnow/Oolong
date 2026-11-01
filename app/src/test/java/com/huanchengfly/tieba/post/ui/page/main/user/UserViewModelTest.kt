package com.huanchengfly.tieba.post.ui.page.main.user

import com.huanchengfly.tieba.core.network.identity.Session
import com.huanchengfly.tieba.post.R
import com.huanchengfly.tieba.post.arch.MessageController
import com.huanchengfly.tieba.post.model.UserProfile
import com.huanchengfly.tieba.post.repository.UserRepository
import com.huanchengfly.tieba.post.testing.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals

class UserViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = mockk<UserRepository>()
    private val stored = mutableListOf<UserProfile>()
    private val shown = mutableListOf<Int>()
    private var session: Session? = Session(uid = "99", name = "me", bduss = "b", stoken = null, tbs = null, zid = null)

    private fun createViewModel() = UserViewModel(
        repository = repository,
        cache = { stored += it },
        messages = object : MessageController {
            override fun show(message: Int, vararg args: Any) {
                shown += message
            }
        },
        sessionProvider = { session },
    )

    @Test
    fun `refreshing keeps the reloaded profile with the account`() = runTest {
        val profile = UserProfile(id = 99, name = "me", nameShow = "", portrait = "p")
        coEvery { repository.profile(99) } returns profile
        val viewModel = createViewModel()

        viewModel.refresh()
        runCurrent()

        assertEquals(listOf(profile), stored)
        assertEquals(false, viewModel.state.value.isLoading)
    }

    @Test
    fun `a guest has nothing to refresh`() = runTest {
        session = null
        val viewModel = createViewModel()

        viewModel.refresh()
        runCurrent()

        coVerify(exactly = 0) { repository.profile(any()) }
    }

    @Test
    fun `a failed refresh says why`() = runTest {
        coEvery { repository.profile(99) } throws IOException("network")
        val viewModel = createViewModel()

        viewModel.refresh()
        runCurrent()

        assertEquals(listOf(R.string.toast_server_message), shown)
        assertEquals(false, viewModel.state.value.isLoading)
    }
}
