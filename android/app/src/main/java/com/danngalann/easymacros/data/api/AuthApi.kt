package com.danngalann.easymacros.data.api

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

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

sealed class ApiException : Exception() {
    data class Http(val statusCode: Int, val retryAfterSeconds: Int?) : ApiException()
    data object Network : ApiException()
    data object Unexpected : ApiException()
}