package com.danngalann.easymacros.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.danngalann.easymacros.data.repository.ApiFailure
import com.danngalann.easymacros.data.repository.DailyDashboard
import com.danngalann.easymacros.data.repository.DashboardRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class DashboardUiState(
    val date: LocalDate = LocalDate.now(),
    val dashboard: DailyDashboard? = null,
    val isLoading: Boolean = true,
    val error: String? = null,
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val repository: DashboardRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(DashboardUiState())
    val state: StateFlow<DashboardUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun previousDay() = load(_state.value.date.minusDays(1))
    fun nextDay() = load(_state.value.date.plusDays(1))
    fun retry() = load(_state.value.date)

    private fun load(date: LocalDate = _state.value.date) = viewModelScope.launch {
        _state.value = DashboardUiState(date = date)
        repository.dashboard(date.toString())
            .onSuccess { _state.value = DashboardUiState(date = date, dashboard = it, isLoading = false) }
            .onFailure { _state.value = DashboardUiState(date = date, isLoading = false, error = it.messageForUser()) }
    }
}

private fun Throwable.messageForUser(): String = when (this) {
    is ApiFailure.Http -> if (statusCode == 401) "Your session has expired. Sign in again." else "Unable to load this day. Try again."
    ApiFailure.Network -> "Unable to reach EasyMacros. Check your connection and try again."
    else -> "Something went wrong. Try again."
}