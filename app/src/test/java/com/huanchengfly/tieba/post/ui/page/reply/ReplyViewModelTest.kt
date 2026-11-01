package com.huanchengfly.tieba.post.ui.page.reply

import com.huanchengfly.tieba.post.arch.MessageController
import com.huanchengfly.tieba.post.repository.PostRepository
import com.huanchengfly.tieba.post.repository.PostedReply
import com.huanchengfly.tieba.post.repository.ReplyTarget
import com.huanchengfly.tieba.post.testing.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals

class ReplyViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = mockk<PostRepository>()
    private val messages = mockk<MessageController>(relaxed = true)
    private val target = ReplyTarget(52, "测试", 9)

    @Test
    fun `uploads the pictures first and posts them after the text`() = runTest {
        coEvery { repository.uploadPictures("测试", listOf("content://a"), true) } returns listOf("#(pic,1,2,3)")
        coEvery { repository.reply(target, "文字\n#(pic,1,2,3)", null) } returns PostedReply(9, 100, "")
        val viewModel = ReplyViewModel(repository, messages, backgroundScope)
        viewModel.addImages(listOf("content://a"))
        viewModel.setOriginalImages(true)

        viewModel.send(target, "文字", tbs = null)
        runCurrent()

        assertEquals(PostedReply(9, 100, ""), viewModel.sent.first())
        assertEquals(true, viewModel.state.value.sent)
        assertEquals(false, viewModel.state.value.busy)
    }

    @Test
    fun `nothing is posted when a picture fails to upload`() = runTest {
        coEvery { repository.uploadPictures(any(), any(), any()) } throws IOException()
        val viewModel = ReplyViewModel(repository, messages, backgroundScope)
        viewModel.addImages(listOf("content://a", "content://b"))
        viewModel.removeImage(0)

        viewModel.send(target, "文字", tbs = null)
        runCurrent()

        coVerify { repository.uploadPictures("测试", listOf("content://b"), false) }
        coVerify(exactly = 0) { repository.reply(any(), any(), any()) }
        verify { messages.showError(any(), any()) }
        assertEquals(false, viewModel.state.value.busy)
    }
}
