package com.huanchengfly.tieba.post.repository

import com.huanchengfly.tieba.core.network.TiebaServerException
import com.huanchengfly.tieba.core.network.agree.AgreeRemoteDataSource
import com.huanchengfly.tieba.core.network.upload.UploadRemoteDataSource
import com.huanchengfly.tieba.core.network.user.LikedForumsResponse
import com.huanchengfly.tieba.core.network.user.UserRemoteDataSource
import com.huanchengfly.tieba.post.api.models.protos.BazhuSign
import com.huanchengfly.tieba.post.api.models.protos.NewGodInfo
import com.huanchengfly.tieba.post.api.models.protos.PostInfoList
import com.huanchengfly.tieba.post.api.models.protos.User
import com.huanchengfly.tieba.post.api.models.protos.profile.ProfileResponseData
import com.huanchengfly.tieba.post.api.models.protos.userPost.UserPostResponseData
import com.huanchengfly.tieba.post.model.LikedForum
import com.huanchengfly.tieba.post.model.UserProfile
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UserRepositoryTest {
    private val remote = mockk<UserRemoteDataSource>()
    private val uploadRemote = mockk<UploadRemoteDataSource>()
    private val repository = UserRepository(remote, mockk<AgreeRemoteDataSource>(relaxed = true), uploadRemote, Dispatchers.Unconfined, { _, _ -> })

    @Test
    fun `maps the profile`() = runTest {
        coEvery { remote.profile(7) } returns ProfileResponseData(
            user = User(
                id = 7, name = "name", nameShow = "昵称", portrait = "p", intro = "简介", sex = 2, tieba_uid = "123",
                ip_address = "浙江", tb_age = "10.5", concern_num = 3, fans_num = 4, total_agree_num = 5,
                thread_num = 6, post_num = 7, my_like_num = 8, has_concerned = 1,
                bazhu_grade = BazhuSign(desc = "测试吧吧主"), new_god_data = NewGodInfo(status = 1, field_name = "游戏"),
            ),
        )

        assertEquals(
            UserProfile(
                id = 7, name = "name", nameShow = "昵称", portrait = "p", intro = "简介", sex = 2, tiebaUid = "123",
                ipLocation = "浙江", tbAge = "10.5", followCount = 3, fanCount = 4, agreeCount = 5, threadCount = 6,
                postCount = 7, forumCount = 8, followed = true, bazhuTitle = "测试吧吧主", godField = "游戏",
            ),
            repository.profile(7),
        )
    }

    @Test
    fun `an unverified expert and an empty title are left out`() = runTest {
        coEvery { remote.profile(7) } returns ProfileResponseData(
            user = User(id = 7, name = "n", nameShow = "n", bazhu_grade = BazhuSign(desc = ""), new_god_data = NewGodInfo(status = 0, field_name = "游戏")),
        )

        val profile = repository.profile(7)

        assertEquals("", profile.nameShow)
        assertNull(profile.bazhuTitle)
        assertNull(profile.godField)
    }

    @Test
    fun `posts page on while a page has any, and say when they are hidden`() = runTest {
        coEvery { remote.posts(7, 1, true) } returns UserPostResponseData(post_list = listOf(PostInfoList(thread_id = 1)), hide_post = 1)
        coEvery { remote.posts(7, 2, true) } returns UserPostResponseData()

        val first = repository.posts(7, threads = true, page = 1)
        val second = repository.posts(7, threads = true, page = 2)

        assertTrue(first.hidden)
        assertEquals(2, first.posts.next)
        assertNull(second.posts.next)
    }

    @Test
    fun `liked forums page on while the server has more`() = runTest {
        coEvery { remote.likedForums(7, 1) } returns LikedForumsResponse(
            hasMore = "1",
            forumList = LikedForumsResponse.ForumList(listOf(LikedForumsResponse.Forum("52", "吧名", "7", "a", "标语"))),
        )

        val page = repository.likedForums(7, 1)

        assertEquals(listOf(LikedForum(52, "吧名", "a", 7, "标语")), page.items)
        assertEquals(2, page.next)
    }

    @Test
    fun `a portrait held for review counts as uploaded and says so`() = runTest {
        coEvery { uploadRemote.uploadPortrait(any()) } throws TiebaServerException(300003, "头像已上传，审核中", null)

        assertEquals("头像已上传，审核中", repository.uploadPortrait(byteArrayOf(1)))
    }

    @Test
    fun `other upload errors are failures`() = runTest {
        coEvery { uploadRemote.uploadPortrait(any()) } throws TiebaServerException(1, "错误", null)

        assertFailsWith<TiebaServerException> { repository.uploadPortrait(byteArrayOf(1)) }
    }

    @Test
    fun `saving the profile sends the birthday back unchanged`() = runTest {
        coEvery { remote.modifyProfile(any(), any(), any(), any(), any()) } returns Unit
        val profile = UserProfile(id = 7, name = "n", nameShow = "", portrait = "", birthdayTime = 946684800, birthdayShown = true)

        repository.modifyProfile(profile, nickName = "新昵称", sex = 1, intro = "新简介")

        io.mockk.coVerify { remote.modifyProfile("新昵称", 1, "新简介", true, 946684800) }
    }
}
