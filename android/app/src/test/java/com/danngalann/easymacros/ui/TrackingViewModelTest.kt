package com.danngalann.easymacros.ui

import com.danngalann.easymacros.data.repository.DailyDashboard
import com.danngalann.easymacros.data.repository.DashboardRepository
import com.danngalann.easymacros.data.repository.Goal
import com.danngalann.easymacros.data.repository.IngredientDraft
import com.danngalann.easymacros.data.repository.NutrientAmount
import com.danngalann.easymacros.data.repository.RationDetail
import com.danngalann.easymacros.data.repository.RationPage
import com.danngalann.easymacros.data.repository.RationPreview
import com.danngalann.easymacros.data.repository.RecipeRepository
import com.danngalann.easymacros.data.repository.RecipeSummary
import com.danngalann.easymacros.data.repository.TrackingRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TrackingViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun `log ration action is hidden inside the log flow`() {
        assertEquals(true, shouldShowLogRationAction("Today", TrackingDestination.Dashboard))
        assertEquals(false, shouldShowLogRationAction("Today", TrackingDestination.Log))
    }

    @Test fun `loads dashboard and ration history for the selected day`() = runTest {
        val tracking = FakeTrackingRepository()
        val viewModel = TrackingViewModel(TrackingFakeDashboardRepository(), TrackingFakeRecipeRepository(), tracking)

        runCurrent()

        assertEquals("480", viewModel.state.value.dashboard?.calories)
        assertEquals(listOf(viewModel.state.value.date.toString()), tracking.requestedDates)
        assertEquals("Breakfast", viewModel.state.value.dayRations.single().recipeName)
    }

    @Test fun `requires a server preview before confirming a ration`() = runTest {
        val viewModel = TrackingViewModel(TrackingFakeDashboardRepository(), TrackingFakeRecipeRepository(), FakeTrackingRepository())
        runCurrent()
        viewModel.openLog()
        advanceTimeBy(250)
        runCurrent()

        viewModel.selectRecipe(RecipeSummary("recipe-1", "Breakfast", "300", 1))
        viewModel.updateDraft(grams = "50")
        viewModel.previewRation()
        runCurrent()

        assertNotNull(viewModel.state.value.rationDraft?.preview)
        assertEquals("84.000000", viewModel.state.value.rationDraft?.preview?.calories)
    }

    @Test fun `blank ration weight previews and logs the whole recipe`() = runTest {
        val tracking = FakeTrackingRepository()
        val viewModel = TrackingViewModel(TrackingFakeDashboardRepository(), TrackingFakeRecipeRepository(), tracking)
        runCurrent()
        viewModel.openLog()
        advanceTimeBy(250)
        runCurrent()

        viewModel.selectRecipe(RecipeSummary("recipe-1", "Breakfast", "300", 1))
        viewModel.previewRation()
        runCurrent()
        viewModel.confirmRation()
        runCurrent()

        assertEquals(listOf(null), tracking.previewedGrams)
        assertEquals(listOf(null), tracking.loggedGrams)
        assertEquals(TrackingDestination.Dashboard, viewModel.state.value.destination)
    }

    @Test fun `timezone draft updates without making a request`() = runTest {
        val tracking = FakeTrackingRepository()
        val viewModel = TrackingViewModel(TrackingFakeDashboardRepository(), TrackingFakeRecipeRepository(), tracking)
        runCurrent()

        viewModel.updateTimezoneDraft("Europe/Berlin")

        assertEquals("Europe/Berlin", viewModel.state.value.timezone)
        assertEquals(0, tracking.timezoneUpdates)
    }

    @Test fun `goals screen hydrates current values and preserves active edits`() = runTest {
        val viewModel = TrackingViewModel(
            TrackingFakeDashboardRepository(),
            TrackingFakeRecipeRepository(),
            FakeTrackingRepository(),
        )
        runCurrent()

        viewModel.navigate(TrackingDestination.Goals)

        assertEquals("120", viewModel.state.value.goalEditor.protein)
        assertEquals("250", viewModel.state.value.goalEditor.carbohydrates)
        assertEquals("70", viewModel.state.value.goalEditor.fat)

        viewModel.updateGoal(protein = "130")
        viewModel.navigate(TrackingDestination.Dashboard)
        viewModel.navigate(TrackingDestination.Goals)

        assertEquals("130", viewModel.state.value.goalEditor.protein)
    }
}

private class TrackingFakeDashboardRepository : DashboardRepository {
    override suspend fun dashboard(date: String) = Result.success(
        DailyDashboard(
            date,
            listOf(NutrientAmount("protein", "21")),
            "480",
            Goal(
                date,
                listOf(
                    NutrientAmount("protein", "120.000000"),
                    NutrientAmount("carbohydrates", "250.000000"),
                    NutrientAmount("fat", "70.000000"),
                ),
            ),
        ),
    )
}

private class TrackingFakeRecipeRepository : RecipeRepository {
    override suspend fun recipes(query: String) = Result.success(listOf(RecipeSummary("recipe-1", "Breakfast", "300", 1)))
    override suspend fun recipe(recipeId: String) = error("not used")
    override suspend fun ingredients(query: String, hidden: Boolean) = error("not used")
    override suspend fun createIngredient(draft: IngredientDraft) = error("not used")
    override suspend fun preview(draft: com.danngalann.easymacros.data.repository.RecipeDraft) = error("not used")
    override suspend fun save(recipeId: String?, draft: com.danngalann.easymacros.data.repository.RecipeDraft) = error("not used")
    override suspend fun delete(recipeId: String) = error("not used")
}

private class FakeTrackingRepository : TrackingRepository {
    val requestedDates = mutableListOf<String?>()
    val previewedGrams = mutableListOf<String?>()
    val loggedGrams = mutableListOf<String?>()
    var timezoneUpdates = 0

    override suspend fun rations(date: String?, from: String?, to: String?, query: String?, cursor: String?): Result<RationPage> {
        requestedDates += date
        return Result.success(RationPage(listOf(com.danngalann.easymacros.data.repository.RationSummary("ration-1", "Breakfast", "50", "2026-08-07T08:00:00Z", "2026-08-07", null)), null))
    }

    override suspend fun ration(rationId: String): Result<RationDetail> = error("not used")
    override suspend fun previewRation(recipeId: String, grams: String?): Result<RationPreview> {
        previewedGrams += grams
        return Result.success(RationPreview("Breakfast", grams ?: "300", listOf(NutrientAmount("protein", "4.5")), "84.000000"))
    }
    override suspend fun logRation(recipeId: String, grams: String?, consumedAt: String, note: String): Result<Unit> {
        loggedGrams += grams
        return Result.success(Unit)
    }
    override suspend fun deleteRation(rationId: String) = Result.success(Unit)
    override suspend fun setGoal(effectiveDate: String, nutrients: List<NutrientAmount>) = Result.success(Goal(effectiveDate, nutrients))
    override suspend fun updateTimezone(timezone: String): Result<String> {
        timezoneUpdates += 1
        return Result.success(timezone)
    }
}