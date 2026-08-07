package com.danngalann.easymacros.data.api

import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query
import retrofit2.http.PUT

interface AuthApi {
    @POST("auth/login")
    suspend fun login(@Body credentials: CredentialsRequest): TokenResponse

    @POST("auth/register")
    suspend fun register(@Body credentials: CredentialsRequest): TokenResponse

    @POST("auth/refresh")
    suspend fun refresh(@Body request: RefreshRequest): TokenResponse

    @POST("auth/logout")
    suspend fun logout()

    @GET("me")
    suspend fun currentUser(): UserResponse
}

data class CredentialsRequest(val username: String, val password: String)
data class RefreshRequest(val refreshToken: String, val idempotencyKey: String)
data class TokenResponse(
    val accessToken: String,
    val expiresIn: Long,
    val refreshToken: String,
    val sessionId: String,
)
data class UserResponse(val id: String, val username: String, val timezone: String)

interface DashboardApi {
    @GET("dashboard")
    suspend fun dashboard(@Query("date") date: String): DashboardResponse
}

data class DashboardResponse(
    val date: String,
    val nutrients: List<NutrientAmountResponse>,
    val calories: String,
    val goal: GoalResponse?,
)

data class NutrientAmountResponse(val code: String, val amount: String)
data class GoalResponse(val effectiveLocalDate: String, val nutrients: List<NutrientAmountResponse>)

interface RecipeApi {
    @GET("recipes")
    suspend fun recipes(@Query("query") query: String): RecipeListResponse

    @GET("recipes/{recipeId}")
    suspend fun recipe(@retrofit2.http.Path("recipeId") recipeId: String): RecipeDetailResponse

    @POST("recipes/preview")
    suspend fun preview(@Body request: RecipeRequest): RecipePreviewResponse

    @POST("recipes")
    suspend fun create(@Body request: RecipeRequest): RecipeDetailResponse

    @PUT("recipes/{recipeId}")
    suspend fun update(
        @retrofit2.http.Path("recipeId") recipeId: String,
        @Body request: RecipeRequest,
    ): RecipeDetailResponse

    @DELETE("recipes/{recipeId}")
    suspend fun delete(@retrofit2.http.Path("recipeId") recipeId: String)
}

interface IngredientApi {
    @GET("ingredients")
    suspend fun ingredients(
        @Query("query") query: String,
        @Query("hidden") hidden: Boolean,
    ): IngredientListResponse
}

data class RecipeListResponse(val recipes: List<RecipeSummaryResponse>)
data class RecipeSummaryResponse(
    val id: String,
    val name: String,
    val yieldGrams: String,
    val versionNumber: Int,
    val updatedAt: String,
)
data class RecipeDetailResponse(
    val recipeId: String,
    val name: String,
    val notes: String?,
    val yieldGrams: String,
    val versionNumber: Int,
    val components: List<RecipeComponentResponse>,
    val nutrients: List<NutrientAmountResponse>,
    val calories: String,
)
data class RecipeComponentResponse(
    val ingredientId: String,
    val ingredientName: String,
    val quantityGrams: String,
    val nutrients: List<NutrientAmountResponse>,
)
data class RecipePreviewResponse(
    val name: String,
    val yieldGrams: String,
    val nutrients: List<NutrientAmountResponse>,
    val calories: String,
)
data class RecipeRequest(
    val name: String,
    val notes: String?,
    val yieldGrams: String,
    val components: List<RecipeComponentRequest>,
    val expectedVersion: Int? = null,
)
data class RecipeComponentRequest(val ingredientId: String, val quantityGrams: String)
data class IngredientListResponse(val ingredients: List<IngredientSummaryResponse>)
data class IngredientSummaryResponse(val id: String, val name: String, val hidden: Boolean)

sealed class ApiException : Exception() {
    data class Http(val statusCode: Int, val retryAfterSeconds: Int?) : ApiException()
    data object Network : ApiException()
    data object Unexpected : ApiException()
}