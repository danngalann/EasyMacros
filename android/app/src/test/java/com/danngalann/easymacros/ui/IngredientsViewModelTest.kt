package com.danngalann.easymacros.ui

import androidx.lifecycle.SavedStateHandle
import com.danngalann.easymacros.data.repository.CandidateReview
import com.danngalann.easymacros.data.repository.IngredientCandidate
import com.danngalann.easymacros.data.repository.IngredientDetailModel
import com.danngalann.easymacros.data.repository.IngredientDraft
import com.danngalann.easymacros.data.repository.IngredientNutrient
import com.danngalann.easymacros.data.repository.IngredientImage
import com.danngalann.easymacros.data.repository.IngredientRepository
import com.danngalann.easymacros.data.repository.IngredientSummary
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
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class IngredientsViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun `manual source opens shared review and confirms exactly once`() = runTest {
        val repository = FakeIngredientRepository()
        val viewModel = IngredientsViewModel(repository, SavedStateHandle())
        runCurrent()

        viewModel.chooseManual()
        viewModel.updateManual(name = "Oats", protein = "13", carbohydrates = "68", fat = "7")
        viewModel.submitManual()
        runCurrent()

        assertEquals(IngredientFlow.Review, viewModel.state.value.flow)
        viewModel.confirmCandidate()
        viewModel.confirmCandidate()
        runCurrent()

        assertEquals(1, repository.confirmCount)
        assertEquals(IngredientFlow.List, viewModel.state.value.flow)
        assertEquals("ingredient-1", viewModel.state.value.confirmedIngredient?.id)
        assertEquals("Oats was created.", viewModel.state.value.successMessage)

        viewModel.clearSuccessMessage()
        assertEquals(null, viewModel.state.value.successMessage)
    }

    @Test fun `recipe origin returns directly from the creation chooser`() {
        assertEquals(true, shouldReturnToRecipe(true, IngredientFlow.Method))
        assertEquals(false, shouldReturnToRecipe(false, IngredientFlow.Method))
        assertEquals(false, shouldReturnToRecipe(true, IngredientFlow.Manual))
    }

    @Test fun `duplicate barcode frames submit once and invalid checksum stays local`() = runTest {
        val repository = FakeIngredientRepository()
        val viewModel = IngredientsViewModel(repository, SavedStateHandle())
        runCurrent()

        viewModel.onBarcodeScanned("4006381333931", 1_000)
        viewModel.onBarcodeScanned("4006381333931", 1_100)
        runCurrent()
        assertEquals(1, repository.barcodeCount)

        viewModel.updateBarcode("4006381333932")
        viewModel.submitBarcode()
        runCurrent()
        assertEquals(1, repository.barcodeCount)
        assertFalse(viewModel.state.value.error.isNullOrBlank())
    }

    @Test fun `saved candidate id resumes polling after process recreation`() = runTest {
        val repository = FakeIngredientRepository(pendingReads = 1)
        val viewModel = IngredientsViewModel(repository, SavedStateHandle(mapOf("candidateId" to "candidate-1")))
        runCurrent()
        assertEquals(IngredientFlow.Processing, viewModel.state.value.flow)

        advanceTimeBy(1_600)
        runCurrent()

        assertEquals(IngredientFlow.Review, viewModel.state.value.flow)
        assertEquals("candidate-1", viewModel.state.value.candidate?.id)
    }

    @Test fun `permission denial keeps manual barcode fallback visible`() = runTest {
        val viewModel = IngredientsViewModel(FakeIngredientRepository(), SavedStateHandle())
        runCurrent()
        viewModel.chooseBarcode()
        viewModel.onPermissionDenied()
        assertEquals(true, viewModel.state.value.permissionDenied)
        assertEquals(false, viewModel.state.value.scannerEnabled)
    }

    @Test fun `submits front and nutrition images together`() = runTest {
        val repository = FakeIngredientRepository()
        val viewModel = IngredientsViewModel(repository, SavedStateHandle())
        runCurrent()
        viewModel.chooseImage()
        viewModel.addImage(byteArrayOf(1), "image/jpeg")
        viewModel.addImage(byteArrayOf(2), "image/jpeg")

        viewModel.submitImage()
        runCurrent()

        assertEquals(2, repository.aiImageCount)
        assertEquals(IngredientFlow.Review, viewModel.state.value.flow)
    }
}

private class FakeIngredientRepository(
    private var pendingReads: Int = 0,
) : IngredientRepository {
    var confirmCount = 0
    var barcodeCount = 0
    var aiImageCount = 0
    private val candidate = IngredientCandidate(
        "candidate-1",
        "version-1",
        "ready",
        "manual",
        "Oats",
        "",
        "",
        listOf(
            IngredientNutrient("protein", "Protein", "13", "g"),
            IngredientNutrient("carbohydrates", "Carbohydrates", "68", "g"),
            IngredientNutrient("fat", "Fat", "7", "g"),
        ),
        null,
        emptyList(),
        "user",
        null,
    )

    override suspend fun ingredients(query: String, hidden: Boolean) = Result.success(emptyList<IngredientSummary>())
    override suspend fun manual(draft: IngredientDraft) = Result.success(candidate.copy(name = draft.name))
    override suspend fun barcode(barcode: String): Result<IngredientCandidate> {
        barcodeCount += 1
        return Result.success(candidate.copy(source = "barcode", barcode = barcode))
    }
    override suspend fun candidate(id: String): Result<IngredientCandidate> {
        if (pendingReads-- > 0) return Result.success(candidate.copy(status = "processing"))
        return Result.success(candidate)
    }
    override suspend fun confirm(id: String, review: CandidateReview): Result<IngredientDetailModel> {
        confirmCount += 1
        return Result.success(detail(review.name))
    }
    override suspend fun ingredient(id: String) = Result.success(detail("Oats"))
    override suspend fun update(id: String, name: String, brand: String) = Result.success(detail(name))
    override suspend fun setHidden(id: String, hidden: Boolean) = Result.success(detail("Oats").copy(hidden = hidden))
    override suspend fun ai(images: List<IngredientImage>, description: String, mode: String): Result<IngredientCandidate> {
        aiImageCount = images.size
        return Result.success(candidate.copy(source = "ai"))
    }
    override suspend fun retry(id: String) = Result.success(candidate)
    override suspend fun cancel(id: String) = Result.success(Unit)

    private fun detail(name: String) = IngredientDetailModel(
        "ingredient-1",
        name,
        "",
        "",
        false,
        "manual",
        null,
        emptyList(),
        emptyList(),
    )
}
