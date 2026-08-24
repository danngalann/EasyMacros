package com.danngalann.easymacros.data.api

import android.util.Log
import com.danngalann.easymacros.data.repository.RefreshCoordinator
import com.danngalann.easymacros.data.repository.SessionStore
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route

@Singleton
class AuthHeaderInterceptor @Inject constructor(private val sessionStore: SessionStore) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val token = runBlocking { sessionStore.read()?.accessToken }
        val request = token?.let { chain.request().newBuilder().header("Authorization", "Bearer $it").build() } ?: chain.request()
        return chain.proceed(request)
    }
}

class NetworkFailureLoggingInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        return try {
            chain.proceed(request)
        } catch (error: IOException) {
            Log.e(LOG_TAG, request.networkFailureMessage(), error)
            throw error
        }
    }
}

@Singleton
class RefreshAuthenticator @Inject constructor(private val coordinator: RefreshCoordinator) : Authenticator {
    override fun authenticate(route: Route?, response: Response): Request? {
        if (response.request.url.encodedPath == "/auth/refresh" || responseCount(response) >= 2) return null
        return if (runBlocking { coordinator.refresh() }) {
            val token = runBlocking { coordinator.currentSession()?.accessToken } ?: return null
            response.request.newBuilder().header("Authorization", "Bearer $token").build()
        } else null
    }

    private fun responseCount(response: Response): Int {
        var count = 1
        var previous = response.priorResponse
        while (previous != null) { count++; previous = previous.priorResponse }
        return count
    }
}

internal fun Request.networkFailureMessage() = "Network request failed: $method ${url.host}${url.encodedPath}"

private const val LOG_TAG = "EasyMacrosNetwork"