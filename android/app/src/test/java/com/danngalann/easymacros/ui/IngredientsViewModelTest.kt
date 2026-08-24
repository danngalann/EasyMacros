package com.danngalann.easymacros.ui

import com.danngalann.easymacros.data.repository.IngredientDraft
import com.danngalann.easymacros.data.repository.IngredientSummary
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
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class IngredientsViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun `confirms a standalone ingredient and refreshes the catalog`() = runTest {
        val repository = IngredientCatalogFakeRepository()
        val viewModel = IngredientsViewModel(repository)
        runCurrent()

        assertEquals(emptyList<IngredientSummary>(), viewModel.state.value.ingredients)
        viewModel.openEditor()
        viewModel.updateEditor(name = "Oats", protein = "13", carbohydrates = "68", fat = "7")
        viewModel.review()
        assertEquals(true, viewModel.state.value.editor?.isReviewing)

        viewModel.confirm()
        runCurrent()

        assertEquals("Oats", repository.createdDraft?.name)
        assertEquals(listOf(IngredientSummary("ingredient-1", "Oats", false)), viewModel.state.value.ingredients)
        assertEquals(null, viewModel.state.value.editor)
    }
}

private class IngredientCatalogFakeRepository : RecipeRepository {
    private val ingredients = mutableListOf<IngredientSummary>()
    var createdDraft: IngredientDraft? = null

    override suspend fun ingredients(query: String, hidden: Boolean) = Result.success(
        ingredients.filter { it.name.contains(query, ignoreCase = true) },
    )

    override suspend fun createIngredient(draft: IngredientDraft): Result<IngredientSummary> {
        createdDraft = draft
        return Result.success(IngredientSummary("ingredient-1", draft.name, false)).also { result ->
            ingredients += result.getOrThrow()
        }
    }

    override suspend fun recipes(query: String): Result<List<RecipeSummary>> = error("not used")
    override suspend fun recipe(recipeId: String): Result<RecipeDetail> = error("not used")
    override suspend fun preview(draft: RecipeDraft): Result<RecipePreview> = error("not used")
    override suspend fun save(recipeId: String?, draft: RecipeDraft): Result<RecipeDetail> = error("not used")
    override suspend fun delete(recipeId: String): Result<Unit> = error("not used")
}