package com.lidseeker.app.ui.requests

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.lidseeker.app.data.ActionResult
import com.lidseeker.app.data.MusicRequest
import com.lidseeker.app.data.Repository
import com.lidseeker.app.data.ServiceLink
import com.lidseeker.app.data.StatsOut
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
class RequestsViewModelTest {

    @get:Rule
    val instantTaskRule = InstantTaskExecutorRule()

    private val dispatcher = StandardTestDispatcher()
    private lateinit var repo: Repository
    private lateinit var vm: RequestsViewModel

    private val fakeRequest = MusicRequest(
        id = 1, type = "album", foreignId = "abc", title = "Test Album",
        artist = "Test Artist", status = "pending", createdAt = "2024-01-01",
    )
    private val fakeStats = StatsOut(total = 3, available = 1, pending = 1, downloading = 1, failed = 0)

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
        repo = mockk(relaxed = true)
        coEvery { repo.requests() } returns listOf(fakeRequest)
        coEvery { repo.stats() } returns fakeStats
        coEvery { repo.services() } returns emptyList()
        vm = RequestsViewModel(repo)
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `refresh loads items and stats`() = runTest {
        vm.refresh(initial = true)
        advanceUntilIdle()

        assertEquals(listOf(fakeRequest), vm.items)
        assertEquals(fakeStats, vm.stats)
        assertFalse(vm.loading)
    }

    @Test
    fun `refresh clears error on success`() = runTest {
        coEvery { repo.requests() } throws RuntimeException("net error") andThen listOf(fakeRequest)
        vm.refresh(initial = true)
        advanceUntilIdle()

        vm.refresh()
        advanceUntilIdle()

        assertNull(vm.error)
        assertEquals(listOf(fakeRequest), vm.items)
    }

    @Test
    fun `loadServices populates services list`() = runTest {
        val links = listOf(ServiceLink("Plex", "plex://play"), ServiceLink("Navidrome", "http://nav/"))
        coEvery { repo.services() } returns links

        vm.loadServices()
        advanceUntilIdle()

        assertEquals(links, vm.services)
    }

    @Test
    fun `retry calls retryRequest and refreshes`() = runTest {
        coEvery { repo.retryRequest(1) } returns ActionResult(ok = true, message = "Retrying…")

        vm.retry(1)
        advanceUntilIdle()

        coVerify { repo.retryRequest(1) }
        assertEquals("Retrying…", vm.actionMessage)
        assertNull(vm.retryingId)
    }

    @Test
    fun `retry is no-op when already retrying`() = runTest {
        // Make retryRequest block so retryingId stays set
        coEvery { repo.retryRequest(any()) } coAnswers {
            kotlinx.coroutines.delay(Long.MAX_VALUE)
            ActionResult()
        }

        vm.retry(1)
        // retryingId should be 1 now
        assertEquals(1, vm.retryingId)

        // Second retry on a different id should be a no-op
        vm.retry(2)
        advanceUntilIdle()
        // retryRequest(2) should never have been called
        coVerify(exactly = 0) { repo.retryRequest(2) }
    }

    @Test
    fun `remove calls deleteRequest and refreshes`() = runTest {
        coEvery { repo.deleteRequest(1) } returns ActionResult(ok = true, message = "Removed.")

        vm.remove(1)
        advanceUntilIdle()

        coVerify { repo.deleteRequest(1) }
        assertEquals("Removed.", vm.actionMessage)
    }

    @Test
    fun `searchNow calls searchRequestNow per-request`() = runTest {
        coEvery { repo.searchRequestNow(1) } returns ActionResult(ok = true, message = "Searching now…")

        vm.searchNow(1)
        advanceUntilIdle()

        coVerify { repo.searchRequestNow(1) }
        assertEquals("Searching now…", vm.actionMessage)
        assertNull(vm.searchingId)
    }

    @Test
    fun `searchNow is no-op when already searching`() = runTest {
        coEvery { repo.searchRequestNow(any()) } coAnswers {
            kotlinx.coroutines.delay(Long.MAX_VALUE)
            ActionResult()
        }

        vm.searchNow(1)
        assertEquals(1, vm.searchingId)

        vm.searchNow(2)
        advanceUntilIdle()
        coVerify(exactly = 0) { repo.searchRequestNow(2) }
    }

    @Test
    fun `consumeActionMessage clears message`() = runTest {
        coEvery { repo.retryRequest(1) } returns ActionResult(message = "Retrying…")
        vm.retry(1)
        advanceUntilIdle()

        vm.consumeActionMessage()

        assertNull(vm.actionMessage)
    }

    @Test
    fun `stats not updated on requests fetch failure`() = runTest {
        coEvery { repo.requests() } throws RuntimeException("net error")

        vm.refresh(initial = true)
        advanceUntilIdle()

        // stats should remain null — we didn't reach the stats call
        assertNull(vm.stats)
        assertTrue(vm.error != null)
    }
}
