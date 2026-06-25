package com.lidseeker.app.ui.settings

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.lidseeker.app.data.ActionResult
import com.lidseeker.app.data.AppSettings
import com.lidseeker.app.data.Repository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    @get:Rule
    val instantTaskRule = InstantTaskExecutorRule()

    private val dispatcher = StandardTestDispatcher()
    private lateinit var repo: Repository
    private lateinit var vm: SettingsViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
        repo = mockk(relaxed = true)
        coEvery { repo.getSettings() } returns AppSettings(
            quality = "flac",
            ntfyTopic = "lidseeker-paul",
            ntfyUrl = "https://ntfy.sh",
        )
        vm = SettingsViewModel(repo)
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `load populates settings from repo`() = runTest {
        vm.load()
        advanceUntilIdle()

        assertEquals("flac", vm.quality)
        assertEquals("lidseeker-paul", vm.ntfyTopic)
        assertEquals("https://ntfy.sh", vm.ntfyUrl)
        assertFalse(vm.loading)
    }

    @Test
    fun `load sets loading false even on error`() = runTest {
        coEvery { repo.getSettings() } throws RuntimeException("net error")

        vm.load()
        advanceUntilIdle()

        assertFalse(vm.loading)
        assertNull(vm.quality)
    }

    @Test
    fun `selectQuality saves new quality`() = runTest {
        vm.load()
        advanceUntilIdle()
        coEvery { repo.setQuality("mp3") } returns ActionResult(ok = true, message = "Saved")

        vm.selectQuality("mp3")
        advanceUntilIdle()

        coVerify { repo.setQuality("mp3") }
        assertEquals("mp3", vm.quality)
        assertEquals("Saved", vm.message)
    }

    @Test
    fun `selectQuality reverts on failure`() = runTest {
        vm.load()
        advanceUntilIdle()
        coEvery { repo.setQuality("mp3") } throws RuntimeException("server error")

        vm.selectQuality("mp3")
        advanceUntilIdle()

        assertEquals("flac", vm.quality)
        assertEquals("Couldn't change quality", vm.message)
    }

    @Test
    fun `selectQuality is no-op when value unchanged`() = runTest {
        vm.load()
        advanceUntilIdle()

        vm.selectQuality("flac")
        advanceUntilIdle()

        coVerify(exactly = 0) { repo.setQuality(any()) }
    }

    @Test
    fun `changePassword succeeds and clears fields`() = runTest {
        coEvery { repo.changePassword("old", "newpass1") } returns ActionResult(
            ok = true, message = "Password changed."
        )
        vm.pwCurrent = "old"
        vm.pwNew = "newpass1"

        vm.changePassword()
        advanceUntilIdle()

        coVerify { repo.changePassword("old", "newpass1") }
        assertTrue(vm.pwSuccess)
        assertEquals("Password changed.", vm.pwMessage)
        assertEquals("", vm.pwCurrent)
        assertEquals("", vm.pwNew)
        assertFalse(vm.pwSaving)
    }

    @Test
    fun `changePassword shows error on failure`() = runTest {
        coEvery { repo.changePassword(any(), any()) } throws RuntimeException("Wrong password")
        vm.pwCurrent = "wrong"
        vm.pwNew = "newpass1"

        vm.changePassword()
        advanceUntilIdle()

        assertFalse(vm.pwSuccess)
        assertEquals("Wrong password", vm.pwMessage)
        assertFalse(vm.pwSaving)
    }

    @Test
    fun `changePassword is no-op when fields blank`() = runTest {
        vm.pwCurrent = ""
        vm.pwNew = "newpass1"

        vm.changePassword()
        advanceUntilIdle()

        coVerify(exactly = 0) { repo.changePassword(any(), any()) }
    }

    @Test
    fun `changePassword is no-op when new password too short`() = runTest {
        vm.pwCurrent = "old"
        vm.pwNew = "short"

        vm.changePassword()
        advanceUntilIdle()

        coVerify(exactly = 0) { repo.changePassword(any(), any()) }
    }
}
