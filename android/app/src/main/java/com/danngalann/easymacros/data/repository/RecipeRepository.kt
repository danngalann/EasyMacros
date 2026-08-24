package com.danngalann.easymacros.data.repository

import com.danngalann.easymacros.data.api.IngredientApi
import com.danngalann.easymacros.data.api.ConfirmIngredientCandidateRequest
import com.danngalann.easymacros.data.api.ManualIngredientRequest
import com.danngalann.easymacros.data.api.NutrientAmountResponse
import com.danngalann.easymacros.data.api.RecipeApi
import com.danngalann.easymacros.data.api.RecipeDetailResponse
import com.danngalann.easymacros.data.api.RecipeRequest
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import retrofit2.HttpException

data class RecipeSummary(
    val id: String,
    val name: String,
    val yieldGrams: String,
    val versionNumber: Int,
)

data class RecipeDetail(
    val id: String,
    val name: String,
    val notes: String?,
    val yieldGrams: String,
    val versionNumber: Int,
    val components: List<RecipeComponent>,
    val nutrients: List<NutrientAmount>,
    val calories: String,
)

data class RecipeComponent(val ingredientId: String, val ingredientName: String, val quantityGrams: String)
data class RecipePreview(val nutrients: List<NutrientAmount>, val calories: String)
data class IngredientSummary(val id: String, val name: String, val hidden: Boolean)
data class IngredientDraft(
    val name: String,
    val brand: String,
    val protein: String,
    val carbohydrates: String,
    val fat: String,
)
data class RecipeDraft(
    val name: String,
    val notes: String,
    val yieldGrams: String,
    val components: List<RecipeComponent>,
    val expectedVersion: Int? = null,
)

interface RecipeRepository {
    suspend fun recipes(query: String): Result<List<RecipeSummary>>
    suspend fun recipe(recipeId: String): Result<RecipeDetail>
    suspend fun ingredients(query: String, hidden: Boolean): Result<List<IngredientSummary>>
    suspend fun createIngredient(draft: IngredientDraft): Result<IngredientSummary>
    suspend fun preview(draft: RecipeDraft): Result<RecipePreview>
    suspend fun save(recipeId: String?, draft: RecipeDraft): Result<RecipeDetail>
    suspend fun delete(recipeId: String): Result<Unit>
}

@Singleton
class DefaultRecipeRepository @Inject constructor(
    private val recipeApi: RecipeApi,
    private val ingredientApi: IngredientApi,
) : RecipeRepository {
    override suspend fun recipes(query: String) = request {
        recipeApi.recipes(query).recipes.map { RecipeSummary(it.id, it.name, it.yieldGrams, it.versionNumber) }
    }

    override suspend fun recipe(recipeId: String) = request { recipeApi.recipe(recipeId).toDomain() }

    override suspend fun ingredients(query: String, hidden: Boolean) = request {
        ingredientApi.ingredients(query, hidden).ingredients.map { IngredientSummary(it.id, it.name, it.hidden) }
    }

    override suspend fun createIngredient(draft: IngredientDraft) = request {
        val candidate = ingredientApi.createManualCandidate(
            ManualIngredientRequest(
                name = draft.name.trim(),
                brand = draft.brand.trim().takeIf { it.isNotEmpty() },
                nutrients = listOf(
                    NutrientAmountResponse("protein", draft.protein),
                    NutrientAmountResponse("carbohydrates", draft.carbohydrates),
                    NutrientAmountResponse("fat", draft.fat),
                ),
            ),
        )
        ingredientApi.confirmCandidate(candidate.id, ConfirmIngredientCandidateRequest(candidate.version))
            .let { IngredientSummary(it.id, it.name, it.hidden) }
    }

    override suspend fun preview(draft: RecipeDraft) = request {
        recipeApi.preview(draft.toRequest()).let { RecipePreview(it.nutrients.map { nutrient -> NutrientAmount(nutrient.code, nutrient.amount) }, it.calories) }
    }

    override suspend fun save(recipeId: String?, draft: RecipeDraft) = request {
        val response = if (recipeId == null) recipeApi.create(draft.toRequest()) else recipeApi.update(recipeId, draft.toRequest())
        response.toDomain()
    }

    override suspend fun delete(recipeId: String) = request { recipeApi.delete(recipeId) }

    private suspend fun <T> request(call: suspend () -> T): Result<T> = try {
        Result.success(call())
    } catch (error: Exception) {
        Result.failure(
            when (error) {
                is HttpException -> ApiFailure.Http(error.code())
                is IOException -> ApiFailure.Network
                else -> ApiFailure.Unexpected
            },
        )
    }
}

private fun RecipeDetailResponse.toDomain() = RecipeDetail(
    recipeId,
    name,
    notes,
    yieldGrams,
    versionNumber,
    components.map { RecipeComponent(it.ingredientId, it.ingredientName, it.quantityGrams) },
    nutrients.map { nutrient -> NutrientAmount(nutrient.code, nutrient.amount) },
    calories,
)

private fun RecipeDraft.toRequest() = RecipeRequest(
    name = name,
    notes = notes.takeIf { it.isNotBlank() },
    yieldGrams = yieldGrams,
    components = components.map { com.danngalann.easymacros.data.api.RecipeComponentRequest(it.ingredientId, it.quantityGrams) },
    expectedVersion = expectedVersion,
)