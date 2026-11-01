package com.huanchengfly.tieba.post.ui.page.user.edit

import com.huanchengfly.tieba.core.network.identity.Session
import com.huanchengfly.tieba.post.R
import com.huanchengfly.tieba.post.arch.MessageController
import com.huanchengfly.tieba.post.model.UserProfile
import com.huanchengfly.tieba.post.repository.UserRepository
import com.huanchengfly.tieba.post.testing.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import java.io.File
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals

class EditProfileViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = mockk<UserRepository>(relaxed = true)
    private val shown = mutableListOf<Int>()
    private val profile = UserProfile(id = 99, name = "me", nameShow = "", portrait = "p", sex = 1, intro = "简介")

    private fun createViewModel() = EditProfileViewModel(
        repository = repository,
        messages = object : MessageController {
            override fun show(message: Int, vararg args: Any) {
                shown += message
            }
        },
        sessionProvider = { Session(uid = "99", name = "me", bduss = "b", stoken = null, tbs = null, zid = null) },
    )

    @Test
    fun `the fields start from the profile, the nickname falling back to the name`() = runTest {
        coEvery { repository.profile(99) } returns profile

        val viewModel = createViewModel()
        runCurrent()

        val state = viewModel.state.value
        assertEquals("me", state.nickName)
        assertEquals(1, state.sex)
        assertEquals("简介", state.intro)
        assertEquals(false, state.changed)
    }

    @Test
    fun `saving without changes just closes`() = runTest {
        coEvery { repository.profile(99) } returns profile
        val viewModel = createViewModel()
        val effects = mutableListOf<EditProfileEffect>()
        backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) { viewModel.effect.collect { effects += it } }
        runCurrent()

        viewModel.save()
        runCurrent()

        assertEquals(listOf<EditProfileEffect>(EditProfileEffect.Done), effects)
        coVerify(exactly = 0) { repository.modifyProfile(any(), any(), any(), any()) }
    }

    @Test
    fun `saving changes sends them and closes`() = runTest {
        coEvery { repository.profile(99) } returns profile
        val viewModel = createViewModel()
        val effects = mutableListOf<EditProfileEffect>()
        backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) { viewModel.effect.collect { effects += it } }
        runCurrent()

        viewModel.setNickName("新昵称")
        viewModel.setSex(2)
        viewModel.save()
        runCurrent()

        coVerify { repository.modifyProfile(profile, "新昵称", 2, "简介") }
        assertEquals(listOf<EditProfileEffect>(EditProfileEffect.Done), effects)
        assertEquals(listOf(R.string.toast_success), shown)
    }

    @Test
    fun `an empty nickname is not sent, and a failed save stays on the page`() = runTest {
        coEvery { repository.profile(99) } returns profile
        coEvery { repository.modifyProfile(any(), any(), any(), any()) } throws IOException("network")
        val viewModel = createViewModel()
        runCurrent()

        viewModel.setNickName("")
        viewModel.save()
        runCurrent()
        coVerify(exactly = 0) { repository.modifyProfile(any(), any(), any(), any()) }

        viewModel.setNickName("x")
        viewModel.save()
        runCurrent()
        assertEquals(listOf(R.string.toast_save_profile_failed), shown)
        assertEquals(false, viewModel.state.value.saving)
    }

    @Test
    fun `a portrait held for review says so and keeps the old one`() = runTest {
        coEvery { repository.profile(99) } returns profile
        coEvery { repository.uploadPortrait(any<File>()) } returns "审核中"
        val viewModel = createViewModel()
        runCurrent()

        viewModel.uploadPortrait(File("x"))
        runCurrent()

        assertEquals(listOf(R.string.toast_server_message), shown)
        assertEquals("p", viewModel.state.value.profile?.portrait)
    }
}
