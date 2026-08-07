package com.danngalann.easymacros.ui

import com.danngalann.easymacros.data.repository.IngredientSummary
import com.danngalann.easymacros.data.repository.NutrientAmount
import com.danngalann.easymacros.data.repository.RecipeComponent
import com.danngalann.easymacros.data.repository.RecipeDetail
import com.danngalann.easymacros.data.repository.RecipeDraft
import com.danngalann.easymacros.data.repository.RecipePreview
import com.danngalann.easymacros.data.repository.RecipeRepository
import com.danngalann.easymacros.data.repository.RecipeSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
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
class RecipesViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun `loads recipes and previews server-authoritative totals`() = runTest {
        val repository = FakeRecipeRepository()
        val viewModel = RecipesViewModel(repository)
        runCurrent()

        assertEquals(listOf(RecipeSummary("recipe-1", "Rice bowl", "300", 1)), viewModel.state.value.recipes)

        viewModel.createRecipe()
        runCurrent()
        viewModel.updateEditor(name = "Rice bowl", yieldGrams = "300")
        viewModel.addIngredient(IngredientSummary("ingredient-1", "Rice", false))
        viewModel.updateComponentQuantity("ingredient-1", "150")
        viewModel.preview()
        runCurrent()

        assertEquals(RecipePreview(listOf(NutrientAmount("protein", "10.5")), "42"), viewModel.state.value.editor?.preview)
        assertEquals("Rice bowl", repository.previewedDraft?.name)
    }

    @Test fun `validates drafts and includes hidden ingredients only when requested`() = runTest {
        val repository = FakeRecipeRepository()
        val viewModel = RecipesViewModel(repository)
        runCurrent()
        viewModel.createRecipe()
        runCurrent()

        viewModel.preview()
        assertEquals("Add a name, positive yield, and positive grams for every ingredient.", viewModel.state.value.editor?.error)

        viewModel.updateEditor(showHiddenIngredients = true)
        runCurrent()

        assertEquals(true, repository.hiddenRequests.last())
    }

    @Test fun `prevents duplicate saves while submission is active`() = runTest {
        val repository = FakeRecipeRepository(saveResult = Result.success(recipeDetail()))
        val viewModel = RecipesViewModel(repository)
        runCurrent()
        viewModel.createRecipe()
        runCurrent()
        viewModel.updateEditor(name = "Rice bowl", yieldGrams = "300")
        viewModel.addIngredient(IngredientSummary("ingredient-1", "Rice", false))
        viewModel.updateComponentQuantity("ingredient-1", "150")

        viewModel.save()
        viewModel.save()
        assertEquals(true, viewModel.state.value.editor?.isSubmitting)
        runCurrent()

        assertEquals(1, repository.saveCalls)
        assertFalse(viewModel.state.value.isLoading)
        assertEquals("recipe-1", viewModel.state.value.detail?.id)
    }
}

private class FakeRecipeRepository(
    private val saveResult: Result<RecipeDetail> = Result.success(recipeDetail()),
) : RecipeRepository {
    var previewedDraft: RecipeDraft? = null
    val hiddenRequests = mutableListOf<Boolean>()
    var saveCalls = 0

    override suspend fun recipes(query: String) = Result.success(listOf(RecipeSummary("recipe-1", "Rice bowl", "300", 1)))
    override suspend fun recipe(recipeId: String) = Result.success(recipeDetail())
    override suspend fun ingredients(query: String, hidden: Boolean): Result<List<IngredientSummary>> {
        hiddenRequests += hidden
        return Result.success(listOf(IngredientSummary("ingredient-1", "Rice", hidden)))
    }
    override suspend fun preview(draft: RecipeDraft): Result<RecipePreview> {
        previewedDraft = draft
        return Result.success(RecipePreview(listOf(NutrientAmount("protein", "10.5")), "42"))
    }
    override suspend fun save(recipeId: String?, draft: RecipeDraft): Result<RecipeDetail> {
        saveCalls += 1
        return saveResult
    }
    override suspend fun delete(recipeId: String) = Result.success(Unit)
}

private fun recipeDetail() = RecipeDetail(
    "recipe-1",
    "Rice bowl",
    null,
    "300",
    1,
    listOf(RecipeComponent("ingredient-1", "Rice", "150")),
    listOf(NutrientAmount("protein", "10.5")),
    "42",
)