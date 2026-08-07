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

interface TrackingApi {
    @GET("rations")
    suspend fun rations(
        @Query("date") date: String? = null,
        @Query("from") from: String? = null,
        @Query("to") to: String? = null,
        @Query("query") query: String? = null,
        @Query("cursor") cursor: String? = null,
    ): RationListResponse

    @GET("rations/{rationId}")
    suspend fun ration(@retrofit2.http.Path("rationId") rationId: String): RationDetailResponse

    @POST("rations")
    suspend fun createRation(@Body request: RationRequest): RationCreatedResponse

    @POST("rations/preview")
    suspend fun previewRation(@Body request: RationPreviewRequest): RationPreviewResponse

    @DELETE("rations/{rationId}")
    suspend fun deleteRation(@retrofit2.http.Path("rationId") rationId: String)

    @PUT("goals")
    suspend fun setGoal(@Body request: GoalRequest): GoalResponse

    @PUT("me/timezone")
    suspend fun updateTimezone(@Body request: TimezoneRequest): TimezoneResponse
}

data class RationRequest(
    val recipeId: String,
    val consumedGrams: String,
    val consumedAt: String,
    val note: String? = null,
)
data class RationCreatedResponse(
    val id: String,
    val consumedLocalDate: String,
    val nutrients: List<NutrientAmountResponse>,
    val calories: String,
)
data class RationPreviewRequest(val recipeId: String, val consumedGrams: String)
data class RationPreviewResponse(
    val recipeName: String,
    val nutrients: List<NutrientAmountResponse>,
    val calories: String,
)
data class RationListResponse(val rations: List<RationSummaryResponse>, val nextCursor: String?)
data class RationSummaryResponse(
    val id: String,
    val recipeName: String,
    val consumedGrams: String,
    val consumedAt: String,
    val consumedLocalDate: String,
    val note: String?,
)
data class RationDetailResponse(
    val id: String,
    val recipeName: String,
    val recipeYieldGrams: String,
    val consumedGrams: String,
    val consumedAt: String,
    val consumedLocalDate: String,
    val timezone: String,
    val note: String?,
    val nutrients: List<NutrientAmountResponse>,
    val calories: String,
)
data class GoalRequest(val effectiveLocalDate: String, val nutrients: List<NutrientAmountResponse>)
data class TimezoneRequest(val timezone: String)
data class TimezoneResponse(val timezone: String)

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