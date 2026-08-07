package com.danngalann.easymacros.data.repository

import com.danngalann.easymacros.data.api.ApiException
import com.danngalann.easymacros.data.api.AuthApi
import com.danngalann.easymacros.data.api.CredentialsRequest
import com.danngalann.easymacros.data.api.RefreshRequest
import com.danngalann.easymacros.data.api.TokenResponse
import com.danngalann.easymacros.data.api.UserResponse
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import retrofit2.HttpException

data class Session(val accessToken: String, val refreshToken: String, val sessionId: String, val expiresAtMillis: Long)

interface SessionStore {
    suspend fun read(): Session?
    suspend fun write(session: Session)
    suspend fun clear()
}

interface AuthRepository {
    suspend fun restoreSession(): Boolean
    suspend fun login(username: String, password: String): Result<Unit>
    suspend fun register(username: String, password: String): Result<Unit>
    suspend fun logout()
    suspend fun refresh(): Boolean
}

@Singleton
class DefaultAuthRepository @Inject constructor(
    private val api: AuthApi,
    private val refreshCoordinator: RefreshCoordinator,
) : AuthRepository {
    override suspend fun restoreSession(): Boolean {
        val session = refreshCoordinator.currentSession() ?: return false
        return if (session.expiresAtMillis > System.currentTimeMillis() || refresh()) true else false
    }

    override suspend fun login(username: String, password: String) = authenticate { api.login(CredentialsRequest(username, password)) }

    override suspend fun register(username: String, password: String) = authenticate { api.register(CredentialsRequest(username, password)) }

    override suspend fun logout() {
        runCatching { api.logout() }
        refreshCoordinator.clear()
    }

    override suspend fun refresh() = refreshCoordinator.refresh()

    private suspend fun authenticate(call: suspend () -> TokenResponse): Result<Unit> = try {
        save(call())
        Result.success(Unit)
    } catch (error: Exception) {
        Result.failure(error.toApiException())
    }

    private suspend fun save(tokens: TokenResponse) {
        refreshCoordinator.save(tokens)
    }
}

@Singleton
class RefreshCoordinator @Inject constructor(
    @javax.inject.Named("refresh") private val refreshApi: AuthApi,
    private val sessionStore: SessionStore,
) {
    private val refreshMutex = Mutex()

    suspend fun currentSession() = sessionStore.read()
    suspend fun clear() = sessionStore.clear()

    suspend fun refresh(): Boolean = refreshMutex.withLock {
        val current = sessionStore.read() ?: return false
        return try {
            save(refreshApi.refresh(RefreshRequest(current.refreshToken, UUID.randomUUID().toString())))
            true
        } catch (_: Exception) {
            sessionStore.clear()
            false
        }
    }

    suspend fun save(tokens: TokenResponse) {
        sessionStore.write(Session(tokens.accessToken, tokens.refreshToken, tokens.sessionId, System.currentTimeMillis() + tokens.expiresIn * 1_000))
    }
}

private fun Exception.toApiException(): ApiException = when (this) {
    is HttpException -> ApiException.Http(code(), response()?.headers()?.get("Retry-After")?.toIntOrNull())
    is IOException -> ApiException.Network
    else -> ApiException.Unexpected
}