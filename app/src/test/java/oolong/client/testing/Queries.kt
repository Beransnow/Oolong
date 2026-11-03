package oolong.client.testing

import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.TestScope
import oolong.client.arch.query.QueryClient
import oolong.client.repository.ThreadActions
import oolong.core.network.agree.AgreeRemoteDataSource

/** The app's cache for one test, living as long as it and timed by its scheduler. */
fun TestScope.queryClient() = QueryClient(backgroundScope, testScheduler.timeSource)

/** Likes going through [client] to [agreeRemote]. */
fun threadActions(client: QueryClient, agreeRemote: AgreeRemoteDataSource = mockk(relaxed = true)) =
    ThreadActions(client, agreeRemote, Dispatchers.Unconfined)
