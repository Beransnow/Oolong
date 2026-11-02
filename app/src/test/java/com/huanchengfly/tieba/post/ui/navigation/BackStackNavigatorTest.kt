package com.huanchengfly.tieba.post.ui.navigation

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import org.junit.Assert.assertEquals
import org.junit.Test

class BackStackNavigatorTest {
    private val main = Page(MainKey)
    private val backStack = NavBackStack<NavKey>(main)
    private val navigator = BackStackNavigator(backStack)

    private val keys get() = backStack.map { it.key }

    private fun push(key: Key): Page = Page(key).also { backStack.add(it) }

    @Test
    fun aListOpensAPageAboveItself() {
        navigator.forPage(main).navigate(ThreadKey(1))

        assertEquals(listOf(MainKey, ThreadKey(1)), keys)
    }

    @Test
    fun aListReplacesThePageItOpenedBefore() {
        push(ThreadKey(1))
        push(UserProfileKey(2))

        navigator.forPage(main).navigate(ThreadKey(3))

        assertEquals(listOf(MainKey, ThreadKey(3)), keys)
    }

    @Test
    fun aListOpeningAnotherListKeepsTheOpenPage() {
        push(ThreadKey(1))

        navigator.forPage(main).navigate(ForumKey("a"))

        assertEquals(listOf(MainKey, ThreadKey(1), ForumKey("a")), keys)
    }

    @Test
    fun aPageOpensAboveItself() {
        val thread = push(ThreadKey(1))

        navigator.forPage(thread).navigate(UserProfileKey(2))

        assertEquals(listOf(MainKey, ThreadKey(1), UserProfileKey(2)), keys)
    }

    @Test
    fun leavingAPageClosesOnlyIt() {
        val thread = push(ThreadKey(1))
        push(ForumKey("a"))

        navigator.forPage(thread).navigateUp()

        assertEquals(listOf(MainKey, ForumKey("a")), keys)
    }

    @Test
    fun leavingAListClosesThePagesOpenedBesideIt() {
        val forum = push(ForumKey("a"))
        push(ThreadKey(1))
        push(UserProfileKey(2))

        navigator.forPage(forum).navigateUp()

        assertEquals(listOf(MainKey), keys)
    }

    @Test
    fun leavingAListKeepsTheListAboveIt() {
        val forum = push(ForumKey("a"))
        push(ThreadKey(1))
        push(SearchKey())

        navigator.forPage(forum).navigateUp()

        assertEquals(listOf(MainKey, SearchKey()), keys)
    }

    @Test
    fun theMainPageStays() {
        push(ThreadKey(1))

        navigator.forPage(main).navigateUp()

        assertEquals(listOf(MainKey, ThreadKey(1)), keys)
    }

    @Test
    fun leavingTwiceDoesNotCloseAnotherPage() {
        push(ThreadKey(1))
        val user = push(UserProfileKey(2))
        val pageNavigator = navigator.forPage(user)

        pageNavigator.navigateUp()
        pageNavigator.navigateUp()

        assertEquals(listOf(MainKey, ThreadKey(1)), keys)
    }

    @Test
    fun aPageOpenedFromASheetTakesItsPlace() {
        push(ThreadKey(1))
        val sheet = push(SubPostsSheetKey(threadId = 1, postId = 2))

        navigator.forPage(sheet).navigate(UserProfileKey(3))

        assertEquals(listOf(MainKey, ThreadKey(1), UserProfileKey(3)), keys)
    }

    @Test
    fun subPostsOpenAsAPageBesideTheThreadWithTwoPanes() {
        val thread = push(ThreadKey(1))
        navigator.twoPane = true

        navigator.forPage(thread).navigate(SubPostsSheetKey(threadId = 1, postId = 2))

        assertEquals(listOf(MainKey, ThreadKey(1), SubPostsKey(threadId = 1, postId = 2)), keys)
    }
}
