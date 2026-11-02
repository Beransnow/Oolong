package com.huanchengfly.tieba.post.testing

import com.huanchengfly.tieba.core.network.agree.AgreeRemoteDataSource
import com.huanchengfly.tieba.post.arch.query.QueryClient
import com.huanchengfly.tieba.post.repository.ThreadActions
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.TestScope

/** The app's cache for one test, living as long as it and timed by its scheduler. */
fun TestScope.queryClient() = QueryClient(backgroundScope, testScheduler.timeSource)

/** Likes going through [client] to [agreeRemote]. */
fun threadActions(client: QueryClient, agreeRemote: AgreeRemoteDataSource = mockk(relaxed = true)) =
    ThreadActions(client, agreeRemote, Dispatchers.Unconfined)
