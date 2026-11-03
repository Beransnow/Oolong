package oolong.client.ui.page.threadstore

import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import oolong.client.arch.MessageController
import oolong.client.arch.Page
import oolong.client.model.StoredThread
import oolong.client.repository.ThreadStoreRepository
import oolong.client.testing.MainDispatcherRule
import org.junit.Rule
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals

class ThreadStoreViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = mockk<ThreadStoreRepository>()
    private val messages = mockk<MessageController>(relaxed = true)

    private fun thread(id: Long) = StoredThread(id, "t$id", null, false, 0, 0, 0, 0)

    @Test
    fun `a removed thread leaves the list only once the server agrees`() = runTest {
        coEvery { repository.page(0) } returns Page(listOf(thread(1), thread(2), thread(3)))
        coEvery { repository.remove(2) } returns Unit
        coEvery { repository.remove(3) } throws IOException()
        val viewModel = ThreadStoreViewModel(repository, messages)
        runCurrent()

        viewModel.remove(thread(2))
        viewModel.remove(thread(3))
        runCurrent()

        assertEquals(listOf(1L, 3L), viewModel.state.value.items.map { it.threadId })
        verify { messages.showError(any(), any()) }
    }
}
