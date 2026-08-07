package com.danngalann.easymacros.ui

import com.danngalann.easymacros.data.repository.AuthRepository
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
class AuthViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun `restores a persisted session`() = runTest {
        val viewModel = AuthViewModel(FakeAuthRepository(restored = true))
        runCurrent()
        assertEquals(AuthUiState(isRestoring = false, isAuthenticated = true), viewModel.state.value)
    }

    @Test fun `keeps a login failure visible`() = runTest {
        val viewModel = AuthViewModel(FakeAuthRepository(loginResult = Result.failure(IllegalStateException("offline"))))
        runCurrent()
        viewModel.login("sam", "password")
        runCurrent()
        assertEquals("Something went wrong. Try again.", viewModel.state.value.error)
    }

    @Test fun `moves to the main graph after login`() = runTest {
        val viewModel = AuthViewModel(FakeAuthRepository(loginResult = Result.success(Unit)))
        runCurrent()
        viewModel.login("sam", "password")
        runCurrent()
        assertEquals(AuthUiState(isRestoring = false, isAuthenticated = true), viewModel.state.value)
    }
}

private class FakeAuthRepository(
    private val restored: Boolean = false,
    private val loginResult: Result<Unit> = Result.success(Unit),
) : AuthRepository {
    override suspend fun restoreSession() = restored
    override suspend fun login(username: String, password: String) = loginResult
    override suspend fun register(username: String, password: String) = loginResult
    override suspend fun logout() = Unit
    override suspend fun refresh() = false
}