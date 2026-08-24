package com.danngalann.easymacros.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.danngalann.easymacros.data.repository.ApiFailure
import com.danngalann.easymacros.data.repository.IngredientSummary
import com.danngalann.easymacros.data.repository.RecipeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class IngredientsUiState(
    val query: String = "",
    val ingredients: List<IngredientSummary> = emptyList(),
    val editor: IngredientEditorState? = null,
    val isLoading: Boolean = true,
    val error: String? = null,
)

@HiltViewModel
class IngredientsViewModel @Inject constructor(
    private val repository: RecipeRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(IngredientsUiState())
    val state: StateFlow<IngredientsUiState> = _state.asStateFlow()

    init { loadIngredients() }

    fun search(query: String) {
        _state.value = _state.value.copy(query = query)
        loadIngredients()
    }

    fun retry() = loadIngredients()

    fun openEditor() {
        _state.value = _state.value.copy(editor = IngredientEditorState())
    }

    fun closeEditor() {
        _state.value = _state.value.copy(editor = null)
    }

    fun updateEditor(
        name: String? = null,
        brand: String? = null,
        protein: String? = null,
        carbohydrates: String? = null,
        fat: String? = null,
    ) {
        val editor = _state.value.editor ?: return
        _state.value = _state.value.copy(editor = editor.copy(
            name = name ?: editor.name,
            brand = brand ?: editor.brand,
            protein = protein ?: editor.protein,
            carbohydrates = carbohydrates ?: editor.carbohydrates,
            fat = fat ?: editor.fat,
            isReviewing = false,
            error = null,
        ))
    }

    fun review() {
        val editor = _state.value.editor ?: return
        if (!editor.isValidIngredient()) {
            _state.value = _state.value.copy(editor = editor.copy(
                error = "Add a name and non-negative protein, carbohydrate, and fat values per 100 g.",
            ))
            return
        }
        _state.value = _state.value.copy(editor = editor.copy(isReviewing = true, error = null))
    }

    fun confirm() {
        val editor = _state.value.editor ?: return
        if (!editor.isReviewing || editor.isSubmitting) return
        _state.value = _state.value.copy(editor = editor.copy(isSubmitting = true, error = null))
        viewModelScope.launch {
            repository.createIngredient(editor.draft()).onSuccess {
                _state.value = _state.value.copy(editor = null)
                loadIngredients()
            }.onFailure { error ->
                _state.value = _state.value.copy(editor = _state.value.editor?.copy(
                    isSubmitting = false,
                    error = error.ingredientCreateMessage(),
                ))
            }
        }
    }

    private fun loadIngredients() = viewModelScope.launch {
        _state.value = _state.value.copy(isLoading = true, error = null)
        repository.ingredients(_state.value.query, false).onSuccess { ingredients ->
            _state.value = _state.value.copy(ingredients = ingredients, isLoading = false)
        }.onFailure { error ->
            _state.value = _state.value.copy(isLoading = false, error = error.ingredientLoadMessage())
        }
    }
}

private fun IngredientEditorState.isValidIngredient() = name.isNotBlank() &&
    listOf(protein, carbohydrates, fat).all { value ->
        Regex("\\d+(\\.\\d+)?").matches(value) && (value.toBigDecimalOrNull()?.signum() ?: -1) >= 0
    }

private fun Throwable.ingredientLoadMessage() = when (this) {
    ApiFailure.Network -> "Unable to reach EasyMacros. Check your connection and try again."
    is ApiFailure.Http -> if (statusCode == 401) "Your session has expired. Sign in again." else "Unable to load ingredients right now. Try again."
    else -> "Something went wrong while loading ingredients. Try again."
}

private fun Throwable.ingredientCreateMessage() = when (this) {
    ApiFailure.Network -> "Unable to reach EasyMacros. Check your connection and try again."
    is ApiFailure.Http -> when (statusCode) {
        401 -> "Your session has expired. Sign in again."
        409 -> "This ingredient confirmation expired. Review the values and try again."
        else -> "Unable to create this ingredient. Check the values and try again."
    }
    else -> "Something went wrong while creating the ingredient. Try again."
}