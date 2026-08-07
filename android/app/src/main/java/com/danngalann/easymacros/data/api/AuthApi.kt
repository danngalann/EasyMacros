package com.danngalann.easymacros.data.api

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

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

sealed class ApiException : Exception() {
    data class Http(val statusCode: Int, val retryAfterSeconds: Int?) : ApiException()
    data object Network : ApiException()
    data object Unexpected : ApiException()
}