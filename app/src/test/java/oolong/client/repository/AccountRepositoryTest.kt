package oolong.client.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.mockk.mockk
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import oolong.client.database.AppDatabase
import oolong.client.models.database.Account
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class AccountRepositoryTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    private val dao = database.accountDao()
    private val prefs = context.getSharedPreferences("accountData", Context.MODE_PRIVATE)

    @Before
    fun setUp() {
        prefs.edit().clear().commit()
    }

    @After
    fun tearDown() = database.close()

    private fun TestScope.repository() =
        AccountRepository(context, dao, mockk(), TestScope(UnconfinedTestDispatcher(testScheduler)), { mockk(relaxed = true) })

    private suspend fun TestScope.withAccounts(vararg names: String): AccountRepository {
        val repository = repository()
        names.forEach { repository.save(Account(uid = it, name = it, bduss = "bduss-$it")) }
        return repository
    }

    @Test
    fun `starts with the accounts saved and the one last switched to`() = runTest {
        val first = withAccounts("a", "b")
        first.switchTo(first.accountByUid("b")!!.id)

        val restarted = repository()

        assertEquals(listOf("a", "b"), restarted.accounts.value.map { it.uid })
        assertEquals("b", restarted.current.value?.uid)
    }

    @Test
    fun `switching to an account that is not there changes nothing`() = runTest {
        val repository = withAccounts("a")
        repository.switchTo(repository.accountByUid("a")!!.id)

        assertFalse(repository.switchTo(999))
        assertEquals("a", repository.current.value?.uid)
    }

    @Test
    fun `saving the current account updates it`() = runTest {
        val repository = withAccounts("a")
        repository.switchTo(repository.accountByUid("a")!!.id)

        repository.save(repository.current.value!!.copy(tbs = "new tbs"))

        assertEquals("new tbs", repository.current.value?.tbs)
        assertEquals(1, repository.accounts.value.size)
    }

    @Test
    fun `logging out switches to another account, then to none`() = runTest {
        val repository = withAccounts("a", "b")
        repository.switchTo(repository.accountByUid("a")!!.id)

        assertEquals("b", repository.logOut()?.uid)
        assertEquals("b", repository.current.value?.uid)
        assertEquals(listOf("b"), dao.getAll().map { it.uid })

        assertNull(repository.logOut())
        assertNull(repository.current.value)
        assertTrue(repository.accounts.value.isEmpty())
        assertNull(repository().current.value)
    }
}
