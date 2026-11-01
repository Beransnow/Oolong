package com.huanchengfly.tieba.post.ui.widgets.compose

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import kotlin.test.Test
import kotlin.test.assertEquals

class CollapsingScrollBehaviorTest {
    private val state = CollapsingState(heightOffsetLimit = -100f)
    private val connection = CollapsingScrollBehavior(state, flingAnimationSpec = null, snapAnimationSpec = null)
        .nestedScrollConnection

    private fun preScroll(dy: Float) = connection.onPreScroll(Offset(0f, dy), NestedScrollSource.UserInput).y
    private fun postScroll(consumed: Float, available: Float) =
        connection.onPostScroll(Offset(0f, consumed), Offset(0f, available), NestedScrollSource.UserInput).y

    @Test
    fun `scrolling up collapses the header before the content moves`() {
        assertEquals(-30f, preScroll(-30f))
        assertEquals(-30f, state.heightOffset)
        assertEquals(0.3f, state.collapsedFraction)
    }

    @Test
    fun `the header takes no more than it can collapse`() {
        assertEquals(-100f, preScroll(-250f))
        assertEquals(1f, state.collapsedFraction)
        assertEquals(0f, preScroll(-10f))
    }

    @Test
    fun `scrolling down leaves the content to scroll back first`() {
        state.heightOffset = -100f
        assertEquals(0f, preScroll(40f))
        assertEquals(-100f, state.heightOffset)
    }

    @Test
    fun `the header opens with what the content leaves over, and passes on the rest`() {
        state.heightOffset = -100f
        // The content was already at its start, so all 150 is left over: the header takes 100
        assertEquals(100f, postScroll(consumed = 0f, available = 150f))
        assertEquals(0f, state.heightOffset)
        assertEquals(0f, postScroll(consumed = 0f, available = 50f))
    }
}
