package com.danngalann.easymacros.ui

import com.danngalann.easymacros.data.repository.DailyDashboard
import com.danngalann.easymacros.data.repository.DashboardRepository
import com.danngalann.easymacros.data.repository.Goal
import com.danngalann.easymacros.data.repository.NutrientAmount
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun `loads the current daily dashboard`() = runTest {
        val dashboard = DailyDashboard("2026-08-07", listOf(NutrientAmount("protein_g", "120")), "480", null)
        val viewModel = DashboardViewModel(FakeDashboardRepository(Result.success(dashboard)))

        runCurrent()

        assertEquals(dashboard, viewModel.state.value.dashboard)
        assertEquals(false, viewModel.state.value.isLoading)
    }

    @Test fun `moves to the previous date and reloads`() = runTest {
        val repository = FakeDashboardRepository(
            Result.success(DailyDashboard("2026-08-06", emptyList(), "0", Goal("2026-08-01", emptyList()))),
        )
        val viewModel = DashboardViewModel(repository)
        runCurrent()

        viewModel.previousDay()
        runCurrent()

        assertEquals(LocalDate.now().minusDays(1).toString(), repository.requestedDates.last())
    }

    @Test fun `shows a retryable error`() = runTest {
        val viewModel = DashboardViewModel(FakeDashboardRepository(Result.failure(IllegalStateException())))

        runCurrent()

        assertEquals("Something went wrong. Try again.", viewModel.state.value.error)
        assertEquals(false, viewModel.state.value.isLoading)
    }
}

private class FakeDashboardRepository(
    private val result: Result<DailyDashboard>,
) : DashboardRepository {
    val requestedDates = mutableListOf<String>()

    override suspend fun dashboard(date: String): Result<DailyDashboard> {
        requestedDates += date
        return result
    }
}