package com.danngalann.easymacros.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.danngalann.easymacros.data.api.ApiException
import com.danngalann.easymacros.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

data class AuthUiState(val isRestoring: Boolean = true, val isAuthenticated: Boolean = false, val isLoading: Boolean = false, val error: String? = null)

@HiltViewModel
class AuthViewModel @Inject constructor(private val repository: AuthRepository) : ViewModel() {
    private val _state = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repository.sessionInvalidations.collect {
                _state.value = AuthUiState(isRestoring = false)
            }
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(
                isRestoring = false,
                isAuthenticated = repository.restoreSession(),
            )
        }
    }

    fun login(username: String, password: String) = authenticate { repository.login(username, password) }
    fun register(username: String, password: String) = authenticate { repository.register(username, password) }
    fun logout() = viewModelScope.launch { repository.logout(); _state.value = AuthUiState(isRestoring = false) }

    private fun authenticate(action: suspend () -> Result<Unit>) = viewModelScope.launch {
        _state.value = _state.value.copy(isLoading = true, error = null)
        action().onSuccess { _state.value = AuthUiState(isRestoring = false, isAuthenticated = true) }
            .onFailure { _state.value = _state.value.copy(isLoading = false, error = it.messageForUser()) }
    }
}

private fun Throwable.messageForUser(): String = when (this) {
    is ApiException.Http -> when (statusCode) {
        403 -> "Registration is unavailable."
        429 -> "Please wait${retryAfterSeconds?.let { " $it seconds" } ?: ""} before trying again."
        else -> "Your username or password was not accepted."
    }
    ApiException.Network -> "Unable to reach EasyMacros. Check your connection and try again."
    else -> "Something went wrong. Try again."
}