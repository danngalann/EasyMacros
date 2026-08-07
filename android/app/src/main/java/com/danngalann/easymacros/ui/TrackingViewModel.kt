package com.danngalann.easymacros.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.danngalann.easymacros.data.repository.ApiFailure
import com.danngalann.easymacros.data.repository.DailyDashboard
import com.danngalann.easymacros.data.repository.DashboardRepository
import com.danngalann.easymacros.data.repository.Goal
import com.danngalann.easymacros.data.repository.NutrientAmount
import com.danngalann.easymacros.data.repository.RationDetail
import com.danngalann.easymacros.data.repository.RationPreview
import com.danngalann.easymacros.data.repository.RationSummary
import com.danngalann.easymacros.data.repository.RecipeRepository
import com.danngalann.easymacros.data.repository.RecipeSummary
import com.danngalann.easymacros.data.repository.TrackingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class TrackingDestination { Dashboard, Log, Goals, History, Settings }

data class RationDraft(
    val recipeQuery: String = "",
    val recipes: List<RecipeSummary> = emptyList(),
    val selectedRecipe: RecipeSummary? = null,
    val grams: String = "",
    val consumedAt: String = Instant.now().toString(),
    val note: String = "",
    val preview: RationPreview? = null,
    val isLoadingRecipes: Boolean = false,
    val isSubmitting: Boolean = false,
    val error: String? = null,
)

data class GoalEditorState(
    val effectiveDate: String = LocalDate.now().toString(),
    val protein: String = "",
    val carbohydrates: String = "",
    val fat: String = "",
    val isSubmitting: Boolean = false,
    val error: String? = null,
)

data class TrackingUiState(
    val destination: TrackingDestination = TrackingDestination.Dashboard,
    val date: LocalDate = LocalDate.now(),
    val dashboard: DailyDashboard? = null,
    val dayRations: List<RationSummary> = emptyList(),
    val isLoadingDashboard: Boolean = true,
    val dashboardError: String? = null,
    val rationDraft: RationDraft? = null,
    val goalEditor: GoalEditorState = GoalEditorState(),
    val historyQuery: String = "",
    val historyFrom: String = "",
    val historyTo: String = "",
    val history: List<RationSummary> = emptyList(),
    val historyCursor: String? = null,
    val isLoadingHistory: Boolean = false,
    val historyError: String? = null,
    val detail: RationDetail? = null,
    val pendingDelete: RationDetail? = null,
    val timezone: String = ZoneId.systemDefault().id,
    val timezoneError: String? = null,
    val isUpdatingTimezone: Boolean = false,
)

@HiltViewModel
class TrackingViewModel @Inject constructor(
    private val dashboardRepository: DashboardRepository,
    private val recipeRepository: RecipeRepository,
    private val trackingRepository: TrackingRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(TrackingUiState())
    val state: StateFlow<TrackingUiState> = _state.asStateFlow()
    private var recipeSearchJob: Job? = null

    init { refreshDashboard() }

    fun navigate(destination: TrackingDestination) {
        _state.value = _state.value.copy(destination = destination)
        if (destination == TrackingDestination.History && _state.value.history.isEmpty()) loadHistory()
    }

    fun previousDay() = loadDashboard(_state.value.date.minusDays(1))
    fun nextDay() = loadDashboard(_state.value.date.plusDays(1))
    fun refreshDashboard() = loadDashboard(_state.value.date)

    fun openLog() {
        _state.value = _state.value.copy(destination = TrackingDestination.Log, rationDraft = RationDraft())
        searchRecipes("")
    }

    fun updateDraft(query: String? = null, grams: String? = null, consumedAt: String? = null, note: String? = null) {
        val draft = _state.value.rationDraft ?: return
        _state.value = _state.value.copy(rationDraft = draft.copy(
            recipeQuery = query ?: draft.recipeQuery,
            grams = grams ?: draft.grams,
            consumedAt = consumedAt ?: draft.consumedAt,
            note = note ?: draft.note,
            preview = if (grams != null) null else draft.preview,
            error = null,
        ))
        if (query != null) searchRecipes(query)
    }

    fun selectRecipe(recipe: RecipeSummary) {
        _state.value = _state.value.copy(rationDraft = _state.value.rationDraft?.copy(selectedRecipe = recipe, preview = null, error = null))
    }

    fun previewRation() {
        val draft = _state.value.rationDraft ?: return
        val recipe = draft.selectedRecipe
        if (recipe == null || !draft.grams.isPositiveDecimal()) {
            _state.value = _state.value.copy(rationDraft = draft.copy(error = "Choose a recipe and enter positive grams."))
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(rationDraft = draft.copy(isSubmitting = true, error = null))
            trackingRepository.previewRation(recipe.id, draft.grams).onSuccess { preview ->
                _state.value = _state.value.copy(rationDraft = _state.value.rationDraft?.copy(preview = preview, isSubmitting = false))
            }.onFailure { error ->
                _state.value = _state.value.copy(rationDraft = _state.value.rationDraft?.copy(isSubmitting = false, error = error.userMessage()))
            }
        }
    }

    fun confirmRation() {
        val draft = _state.value.rationDraft ?: return
        val recipe = draft.selectedRecipe ?: return
        if (draft.preview == null || !draft.grams.isPositiveDecimal() || runCatching { Instant.parse(draft.consumedAt) }.isFailure) {
            _state.value = _state.value.copy(rationDraft = draft.copy(error = "Review a valid recipe, grams, and ISO-8601 time before logging."))
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(rationDraft = draft.copy(isSubmitting = true, error = null))
            trackingRepository.logRation(recipe.id, draft.grams, draft.consumedAt, draft.note).onSuccess {
                _state.value = _state.value.copy(destination = TrackingDestination.Dashboard, rationDraft = null)
                refreshDashboard()
            }.onFailure { error ->
                _state.value = _state.value.copy(rationDraft = _state.value.rationDraft?.copy(isSubmitting = false, error = error.userMessage()))
            }
        }
    }

    fun updateGoal(effectiveDate: String? = null, protein: String? = null, carbohydrates: String? = null, fat: String? = null) {
        val editor = _state.value.goalEditor
        _state.value = _state.value.copy(goalEditor = editor.copy(
            effectiveDate = effectiveDate ?: editor.effectiveDate,
            protein = protein ?: editor.protein,
            carbohydrates = carbohydrates ?: editor.carbohydrates,
            fat = fat ?: editor.fat,
            error = null,
        ))
    }

    fun saveGoal() {
        val editor = _state.value.goalEditor
        val nutrients = listOf("protein" to editor.protein, "carbohydrates" to editor.carbohydrates, "fat" to editor.fat)
            .filter { (_, amount) -> amount.isNotBlank() }
            .map { (code, amount) -> NutrientAmount(code, amount) }
        if (runCatching { LocalDate.parse(editor.effectiveDate) }.isFailure || nutrients.isEmpty() || nutrients.any { !it.amount.isPositiveDecimal() }) {
            _state.value = _state.value.copy(goalEditor = editor.copy(error = "Choose a date and enter at least one positive target."))
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(goalEditor = editor.copy(isSubmitting = true, error = null))
            trackingRepository.setGoal(editor.effectiveDate, nutrients).onSuccess { goal ->
                _state.value = _state.value.copy(goalEditor = GoalEditorState(effectiveDate = goal.effectiveLocalDate))
                refreshDashboard()
            }.onFailure { error ->
                _state.value = _state.value.copy(goalEditor = _state.value.goalEditor.copy(isSubmitting = false, error = error.userMessage()))
            }
        }
    }

    fun updateHistory(query: String? = null, from: String? = null, to: String? = null) {
        _state.value = _state.value.copy(historyQuery = query ?: _state.value.historyQuery, historyFrom = from ?: _state.value.historyFrom, historyTo = to ?: _state.value.historyTo)
        loadHistory()
    }

    fun loadMoreHistory() = loadHistory(_state.value.historyCursor, append = true)

    fun openRation(rationId: String) = viewModelScope.launch {
        trackingRepository.ration(rationId).onSuccess { detail -> _state.value = _state.value.copy(detail = detail) }
            .onFailure { error -> _state.value = _state.value.copy(historyError = error.userMessage()) }
    }

    fun closeRationDetail() { _state.value = _state.value.copy(detail = null, pendingDelete = null) }
    fun requestDelete() { _state.value = _state.value.copy(pendingDelete = _state.value.detail) }
    fun dismissDelete() { _state.value = _state.value.copy(pendingDelete = null) }

    fun deleteRation() {
        val ration = _state.value.pendingDelete ?: return
        viewModelScope.launch {
            trackingRepository.deleteRation(ration.id).onSuccess {
                _state.value = _state.value.copy(detail = null, pendingDelete = null)
                refreshDashboard()
                loadHistory()
            }.onFailure { error -> _state.value = _state.value.copy(pendingDelete = null, historyError = error.userMessage()) }
        }
    }

    fun updateTimezone(timezone: String) {
        if (timezone.isBlank()) return
        viewModelScope.launch {
            _state.value = _state.value.copy(isUpdatingTimezone = true, timezoneError = null)
            trackingRepository.updateTimezone(timezone.trim()).onSuccess { saved ->
                _state.value = _state.value.copy(timezone = saved, isUpdatingTimezone = false)
                refreshDashboard()
                loadHistory()
            }.onFailure { error -> _state.value = _state.value.copy(isUpdatingTimezone = false, timezoneError = error.userMessage()) }
        }
    }

    private fun loadDashboard(date: LocalDate) = viewModelScope.launch {
        _state.value = _state.value.copy(date = date, isLoadingDashboard = true, dashboardError = null)
        dashboardRepository.dashboard(date.toString()).onSuccess { dashboard ->
            _state.value = _state.value.copy(dashboard = dashboard, isLoadingDashboard = false)
        }.onFailure { error -> _state.value = _state.value.copy(isLoadingDashboard = false, dashboardError = error.userMessage()) }
        trackingRepository.rations(date = date.toString()).onSuccess { page ->
            _state.value = _state.value.copy(dayRations = page.rations)
        }
    }

    private fun loadHistory(cursor: String? = null, append: Boolean = false) = viewModelScope.launch {
        val state = _state.value
        _state.value = state.copy(isLoadingHistory = true, historyError = null)
        trackingRepository.rations(
            from = state.historyFrom.takeIf { it.isNotBlank() },
            to = state.historyTo.takeIf { it.isNotBlank() },
            query = state.historyQuery.takeIf { it.isNotBlank() },
            cursor = cursor,
        ).onSuccess { page ->
            _state.value = _state.value.copy(history = if (append) _state.value.history + page.rations else page.rations, historyCursor = page.nextCursor, isLoadingHistory = false)
        }.onFailure { error -> _state.value = _state.value.copy(isLoadingHistory = false, historyError = error.userMessage()) }
    }

    private fun searchRecipes(query: String) {
        recipeSearchJob?.cancel()
        recipeSearchJob = viewModelScope.launch {
            delay(250)
            _state.value = _state.value.copy(rationDraft = _state.value.rationDraft?.copy(isLoadingRecipes = true))
            recipeRepository.recipes(query).onSuccess { recipes ->
                _state.value = _state.value.copy(rationDraft = _state.value.rationDraft?.copy(recipes = recipes, isLoadingRecipes = false))
            }.onFailure { error ->
                _state.value = _state.value.copy(rationDraft = _state.value.rationDraft?.copy(isLoadingRecipes = false, error = error.userMessage()))
            }
        }
    }
}

private fun String.isPositiveDecimal() = Regex("\\d+(\\.\\d+)?").matches(this) && toBigDecimalOrNull()?.signum() == 1
private fun Throwable.userMessage() = when (this) {
    is ApiFailure.Http -> if (statusCode == 401) "Your session has expired. Sign in again." else "Unable to complete that request. Try again."
    ApiFailure.Network -> "Unable to reach EasyMacros. Check your connection and try again."
    else -> "Something went wrong. Try again."
}