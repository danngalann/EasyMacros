package com.danngalann.easymacros.ui

import com.danngalann.easymacros.data.repository.IngredientSummary
import com.danngalann.easymacros.data.repository.IngredientDraft
import com.danngalann.easymacros.data.repository.ApiFailure
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
        viewModel.updateEditor(name = "Rice bowl")
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
        assertEquals("Add a name and positive grams for every ingredient.", viewModel.state.value.editor?.error)

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
        viewModel.updateEditor(name = "Rice bowl")
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

    @Test fun `recipe list failure never reports a save error`() = runTest {
        val viewModel = RecipesViewModel(FakeRecipeRepository(recipesResult = Result.failure(ApiFailure.Http(500))))

        runCurrent()

        assertEquals("Unable to load recipes right now. Try again.", viewModel.state.value.error)
    }

    @Test fun `adds an ingredient returned by the shared creation flow`() = runTest {
        val repository = FakeRecipeRepository()
        val viewModel = RecipesViewModel(repository)
        runCurrent()
        viewModel.createRecipe()
        runCurrent()

        viewModel.addCreatedIngredient("ingredient-created", "Greek yogurt")
        assertEquals("ingredient-created", viewModel.state.value.editor?.components?.single()?.ingredientId)
    }

    @Test fun `ingredient list failure never reports a recipe save error`() = runTest {
        val repository = FakeRecipeRepository(ingredientsResult = Result.failure(ApiFailure.Http(500)))
        val viewModel = RecipesViewModel(repository)
        runCurrent()

        viewModel.createRecipe()
        runCurrent()

        assertEquals("Unable to load ingredients right now. Try again.", viewModel.state.value.editor?.error)
    }
}

private class FakeRecipeRepository(
    private val saveResult: Result<RecipeDetail> = Result.success(recipeDetail()),
    private val recipesResult: Result<List<RecipeSummary>> = Result.success(listOf(RecipeSummary("recipe-1", "Rice bowl", "300", 1))),
    private val ingredientsResult: Result<List<IngredientSummary>> = Result.success(listOf(IngredientSummary("ingredient-1", "Rice", false))),
) : RecipeRepository {
    var previewedDraft: RecipeDraft? = null
    val hiddenRequests = mutableListOf<Boolean>()
    var saveCalls = 0
    var createdIngredientDraft: IngredientDraft? = null

    override suspend fun recipes(query: String) = recipesResult
    override suspend fun recipe(recipeId: String) = Result.success(recipeDetail())
    override suspend fun ingredients(query: String, hidden: Boolean): Result<List<IngredientSummary>> {
        hiddenRequests += hidden
        return ingredientsResult.map { ingredients -> ingredients.map { it.copy(hidden = hidden) } }
    }
    override suspend fun createIngredient(draft: IngredientDraft): Result<IngredientSummary> {
        createdIngredientDraft = draft
        return Result.success(IngredientSummary("ingredient-created", draft.name, false))
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