package com.danngalann.easymacros.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.hasStateDescription
import androidx.lifecycle.SavedStateHandle
import com.danngalann.easymacros.data.repository.CandidateReview
import com.danngalann.easymacros.data.repository.ApiFailure
import com.danngalann.easymacros.data.repository.IngredientCandidate
import com.danngalann.easymacros.data.repository.IngredientDetailModel
import com.danngalann.easymacros.data.repository.IngredientDraft
import com.danngalann.easymacros.data.repository.IngredientNutrient
import com.danngalann.easymacros.data.repository.IngredientImage
import com.danngalann.easymacros.data.repository.IngredientRepository
import com.danngalann.easymacros.data.repository.IngredientSummary
import kotlinx.coroutines.delay
import org.junit.Rule
import org.junit.Test

class IngredientsScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun loadingStateIsVisible() {
        val viewModel = IngredientsViewModel(ScreenRepository(loadDelay = 10_000), SavedStateHandle())
        compose.setContent { IngredientsScreen({}, viewModel = viewModel) }
        compose.onNode(hasStateDescription("Loading ingredients")).assertIsDisplayed()
    }

    @Test fun networkErrorOffersRetry() {
        val viewModel = IngredientsViewModel(ScreenRepository(failLoad = true), SavedStateHandle())
        compose.setContent { IngredientsScreen({}, viewModel = viewModel) }
        compose.waitForIdle()
        compose.onNodeWithText("Unable to reach EasyMacros. Your form is preserved; check the connection and retry.")
            .assertIsDisplayed()
        compose.onNodeWithText("Try again").assertIsDisplayed()
    }

    @Test fun manualCandidateUsesSharedReviewScreen() {
        val viewModel = IngredientsViewModel(ScreenRepository(), SavedStateHandle())
        compose.setContent { IngredientsScreen({}, viewModel = viewModel) }
        compose.runOnIdle {
            viewModel.chooseManual()
            viewModel.updateManual(name = "Oats", protein = "13", carbohydrates = "68", fat = "7")
            viewModel.submitManual()
        }
        compose.waitForIdle()
        compose.onNodeWithText("Source: user").assertIsDisplayed()
        compose.onNodeWithText("Confirm and create ingredient").assertIsDisplayed()
    }
}

private class ScreenRepository(
    private val failLoad: Boolean = false,
    private val loadDelay: Long = 0,
) : IngredientRepository {
    private val candidate = IngredientCandidate(
        "candidate-1", "version-1", "ready", "manual", "Oats", "", "",
        listOf(
            IngredientNutrient("protein", "Protein", "13", "g"),
            IngredientNutrient("carbohydrates", "Carbohydrates", "68", "g"),
            IngredientNutrient("fat", "Fat", "7", "g"),
        ),
        null, emptyList(), "user", null,
    )

    override suspend fun ingredients(query: String, hidden: Boolean): Result<List<IngredientSummary>> {
        if (loadDelay > 0) delay(loadDelay)
        return if (failLoad) Result.failure(ApiFailure.Network) else Result.success(emptyList())
    }
    override suspend fun manual(draft: IngredientDraft) = Result.success(candidate)
    override suspend fun barcode(barcode: String) = Result.success(candidate.copy(source = "barcode"))
    override suspend fun ai(images: List<IngredientImage>, description: String, mode: String) =
        Result.success(candidate.copy(source = "ai"))
    override suspend fun candidate(id: String) = Result.success(candidate)
    override suspend fun retry(id: String) = Result.success(candidate)
    override suspend fun cancel(id: String) = Result.success(Unit)
    override suspend fun confirm(id: String, review: CandidateReview) = Result.success(detail())
    override suspend fun ingredient(id: String) = Result.success(detail())
    override suspend fun update(id: String, name: String, brand: String) = Result.success(detail())
    override suspend fun setHidden(id: String, hidden: Boolean) = Result.success(detail().copy(hidden = hidden))

    private fun detail() = IngredientDetailModel(
        "ingredient-1", "Oats", "", "", false, "manual", null, emptyList(), emptyList(),
    )
}
