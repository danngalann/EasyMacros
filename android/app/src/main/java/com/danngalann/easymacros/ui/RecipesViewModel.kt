package com.danngalann.easymacros.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.danngalann.easymacros.data.repository.ApiFailure
import com.danngalann.easymacros.data.repository.IngredientSummary
import com.danngalann.easymacros.data.repository.IngredientDraft
import com.danngalann.easymacros.data.repository.RecipeComponent
import com.danngalann.easymacros.data.repository.RecipeDetail
import com.danngalann.easymacros.data.repository.RecipeDraft
import com.danngalann.easymacros.data.repository.RecipePreview
import com.danngalann.easymacros.data.repository.RecipeRepository
import com.danngalann.easymacros.data.repository.RecipeSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class RecipeEditorState(
    val recipeId: String? = null,
    val versionNumber: Int? = null,
    val name: String = "",
    val notes: String = "",
    val yieldGrams: String = "",
    val components: List<RecipeComponent> = emptyList(),
    val ingredientQuery: String = "",
    val showHiddenIngredients: Boolean = false,
    val ingredients: List<IngredientSummary> = emptyList(),
    val preview: RecipePreview? = null,
    val error: String? = null,
    val isLoadingIngredients: Boolean = false,
    val isSubmitting: Boolean = false,
    val ingredientEditor: IngredientEditorState? = null,
) {
    val isDirty: Boolean get() = name.isNotBlank() || notes.isNotBlank() || yieldGrams.isNotBlank() || components.isNotEmpty()
    fun draft() = RecipeDraft(name, notes, yieldGrams, components, versionNumber)
}

data class IngredientEditorState(
    val name: String = "",
    val brand: String = "",
    val protein: String = "",
    val carbohydrates: String = "",
    val fat: String = "",
    val isReviewing: Boolean = false,
    val isSubmitting: Boolean = false,
    val error: String? = null,
) {
    fun draft() = IngredientDraft(name, brand, protein, carbohydrates, fat)
}

data class RecipesUiState(
    val query: String = "",
    val recipes: List<RecipeSummary> = emptyList(),
    val detail: RecipeDetail? = null,
    val editor: RecipeEditorState? = null,
    val pendingDelete: RecipeDetail? = null,
    val isLoading: Boolean = true,
    val error: String? = null,
)

@HiltViewModel
class RecipesViewModel @Inject constructor(
    private val repository: RecipeRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(RecipesUiState())
    val state: StateFlow<RecipesUiState> = _state.asStateFlow()

    init { loadRecipes() }

    fun search(query: String) {
        _state.value = _state.value.copy(query = query)
        loadRecipes()
    }

    fun retry() = loadRecipes()

    fun openRecipe(recipeId: String) = viewModelScope.launch {
        _state.value = _state.value.copy(isLoading = true, error = null)
        repository.recipe(recipeId).onSuccess { detail ->
            _state.value = _state.value.copy(detail = detail, isLoading = false)
        }.onFailure { error -> _state.value = _state.value.copy(isLoading = false, error = error.openMessage()) }
    }

    fun closeDetail() { _state.value = _state.value.copy(detail = null, pendingDelete = null) }

    fun createRecipe() {
        _state.value = _state.value.copy(detail = null, editor = RecipeEditorState())
        loadIngredients()
    }

    fun editRecipe() {
        val detail = _state.value.detail ?: return
        _state.value = _state.value.copy(
            editor = RecipeEditorState(
                recipeId = detail.id,
                versionNumber = detail.versionNumber,
                name = detail.name,
                notes = detail.notes.orEmpty(),
                yieldGrams = detail.yieldGrams,
                components = detail.components,
            ),
        )
        loadIngredients()
    }

    fun discardEditor() { _state.value = _state.value.copy(editor = null) }

    fun updateEditor(
        name: String? = null,
        notes: String? = null,
        yieldGrams: String? = null,
        ingredientQuery: String? = null,
        showHiddenIngredients: Boolean? = null,
    ) {
        val editor = _state.value.editor ?: return
        val updated = editor.copy(
            name = name ?: editor.name,
            notes = notes ?: editor.notes,
            yieldGrams = yieldGrams ?: editor.yieldGrams,
            ingredientQuery = ingredientQuery ?: editor.ingredientQuery,
            showHiddenIngredients = showHiddenIngredients ?: editor.showHiddenIngredients,
            preview = null,
            error = null,
        )
        _state.value = _state.value.copy(editor = updated)
        if (ingredientQuery != null || showHiddenIngredients != null) loadIngredients()
    }

    fun addIngredient(ingredient: IngredientSummary) {
        updateEditorComponents((_state.value.editor?.components.orEmpty() + RecipeComponent(ingredient.id, ingredient.name, "")))
    }

    fun updateComponentQuantity(ingredientId: String, quantityGrams: String) {
        updateEditorComponents(_state.value.editor?.components.orEmpty().map {
            if (it.ingredientId == ingredientId) it.copy(quantityGrams = quantityGrams) else it
        })
    }

    fun removeComponent(ingredientId: String) {
        updateEditorComponents(_state.value.editor?.components.orEmpty().filterNot { it.ingredientId == ingredientId })
    }

    fun openIngredientEditor() {
        _state.value = _state.value.copy(editor = _state.value.editor?.copy(ingredientEditor = IngredientEditorState()))
    }

    fun closeIngredientEditor() {
        _state.value = _state.value.copy(editor = _state.value.editor?.copy(ingredientEditor = null))
    }

    fun updateIngredientEditor(
        name: String? = null,
        brand: String? = null,
        protein: String? = null,
        carbohydrates: String? = null,
        fat: String? = null,
    ) {
        val editor = _state.value.editor ?: return
        val ingredient = editor.ingredientEditor ?: return
        _state.value = _state.value.copy(editor = editor.copy(ingredientEditor = ingredient.copy(
            name = name ?: ingredient.name,
            brand = brand ?: ingredient.brand,
            protein = protein ?: ingredient.protein,
            carbohydrates = carbohydrates ?: ingredient.carbohydrates,
            fat = fat ?: ingredient.fat,
            isReviewing = false,
            error = null,
        )))
    }

    fun reviewIngredient() {
        val editor = _state.value.editor ?: return
        val ingredient = editor.ingredientEditor ?: return
        if (!ingredient.isValid()) {
            _state.value = _state.value.copy(editor = editor.copy(ingredientEditor = ingredient.copy(
                error = "Add a name and non-negative protein, carbohydrate, and fat values per 100 g.",
            )))
            return
        }
        _state.value = _state.value.copy(editor = editor.copy(ingredientEditor = ingredient.copy(isReviewing = true, error = null)))
    }

    fun confirmIngredient() {
        val editor = _state.value.editor ?: return
        val ingredient = editor.ingredientEditor ?: return
        if (!ingredient.isReviewing || ingredient.isSubmitting) return
        _state.value = _state.value.copy(editor = editor.copy(ingredientEditor = ingredient.copy(isSubmitting = true, error = null)))
        viewModelScope.launch {
            repository.createIngredient(ingredient.draft()).onSuccess { created ->
                val current = _state.value.editor ?: return@onSuccess
                _state.value = _state.value.copy(editor = current.copy(
                    components = current.components + RecipeComponent(created.id, created.name, ""),
                    ingredients = (current.ingredients + created).distinctBy(IngredientSummary::id),
                    ingredientEditor = null,
                    preview = null,
                ))
            }.onFailure { error ->
                val current = _state.value.editor ?: return@onFailure
                _state.value = _state.value.copy(editor = current.copy(ingredientEditor = current.ingredientEditor?.copy(
                    isSubmitting = false,
                    error = error.ingredientMessage(),
                )))
            }
        }
    }

    fun preview() = submit(
        operation = { draft -> repository.preview(draft) },
        success = { preview ->
            _state.value = _state.value.copy(editor = _state.value.editor?.copy(preview = preview, isSubmitting = false))
        },
    )

    fun save() = submit(
        operation = { draft -> repository.save(_state.value.editor?.recipeId, draft) },
        success = { detail ->
            _state.value = _state.value.copy(detail = detail, editor = null, isLoading = false)
            loadRecipes()
        },
    )

    fun requestDelete() { _state.value = _state.value.copy(pendingDelete = _state.value.detail) }
    fun dismissDelete() { _state.value = _state.value.copy(pendingDelete = null) }

    fun deleteRecipe() {
        val recipe = _state.value.pendingDelete ?: return
        _state.value = _state.value.copy(pendingDelete = null, isLoading = true)
        viewModelScope.launch {
            repository.delete(recipe.id).onSuccess {
                _state.value = _state.value.copy(detail = null, isLoading = false)
                loadRecipes()
            }.onFailure { error -> _state.value = _state.value.copy(isLoading = false, error = error.deleteMessage()) }
        }
    }

    private fun updateEditorComponents(components: List<RecipeComponent>) {
        _state.value = _state.value.copy(editor = _state.value.editor?.copy(components = components, preview = null, error = null))
    }

    private fun loadRecipes() = viewModelScope.launch {
        _state.value = _state.value.copy(isLoading = true, error = null)
        repository.recipes(_state.value.query).onSuccess { recipes ->
            _state.value = _state.value.copy(recipes = recipes, isLoading = false)
        }.onFailure { error -> _state.value = _state.value.copy(isLoading = false, error = error.listMessage()) }
    }

    private fun loadIngredients() {
        val editor = _state.value.editor ?: return
        _state.value = _state.value.copy(editor = editor.copy(isLoadingIngredients = true))
        viewModelScope.launch {
            repository.ingredients(editor.ingredientQuery, editor.showHiddenIngredients).onSuccess { ingredients ->
                _state.value = _state.value.copy(editor = _state.value.editor?.copy(ingredients = ingredients, isLoadingIngredients = false))
            }.onFailure { error ->
                _state.value = _state.value.copy(editor = _state.value.editor?.copy(isLoadingIngredients = false, error = error.ingredientListMessage()))
            }
        }
    }

    private fun <T> submit(operation: suspend (RecipeDraft) -> Result<T>, success: (T) -> Unit) {
        val editor = _state.value.editor ?: return
        if (editor.isSubmitting) return
        if (!editor.isValid()) {
            _state.value = _state.value.copy(editor = editor.copy(error = "Add a name, positive yield, and positive grams for every ingredient."))
            return
        }
        _state.value = _state.value.copy(editor = editor.copy(isSubmitting = true, error = null))
        viewModelScope.launch {
            operation(editor.draft()).onSuccess(success).onFailure { error ->
                _state.value = _state.value.copy(editor = _state.value.editor?.copy(isSubmitting = false, error = error.userMessage()))
            }
        }
    }
}

private fun RecipeEditorState.isValid() = name.isNotBlank() && yieldGrams.isPositiveDecimal() && components.isNotEmpty() && components.all { it.quantityGrams.isPositiveDecimal() }
private fun String.isPositiveDecimal() = Regex("\\d+(\\.\\d+)?").matches(this) && toBigDecimalOrNull()?.signum() == 1
private fun String.isNonNegativeDecimal() = Regex("\\d+(\\.\\d+)?").matches(this) && (toBigDecimalOrNull()?.signum() ?: -1) >= 0
private fun IngredientEditorState.isValid() = name.isNotBlank() && protein.isNonNegativeDecimal() && carbohydrates.isNonNegativeDecimal() && fat.isNonNegativeDecimal()
private fun Throwable.userMessage() = when (this) {
    is ApiFailure.Http -> when (statusCode) {
        401 -> "Your session has expired. Sign in again."
        404 -> "This recipe is no longer available. Refresh the list and try again."
        409 -> "This recipe changed elsewhere. Reload it before saving again."
        else -> "Unable to save this recipe. Check the values and try again."
    }
    ApiFailure.Network -> "Unable to reach EasyMacros. Check your connection and try again."
    else -> "Something went wrong. Try again."
}

private fun Throwable.listMessage() = when (this) {
    ApiFailure.Network -> "Unable to reach EasyMacros. Check your connection and try again."
    is ApiFailure.Http -> if (statusCode == 401) "Your session has expired. Sign in again." else "Unable to load recipes right now. Try again."
    else -> "Something went wrong while loading recipes. Try again."
}

private fun Throwable.openMessage() = when (this) {
    ApiFailure.Network -> "Unable to reach EasyMacros. Check your connection and try again."
    is ApiFailure.Http -> if (statusCode == 404) "This recipe is no longer available." else "Unable to open this recipe. Try again."
    else -> "Something went wrong while opening this recipe. Try again."
}

private fun Throwable.deleteMessage() = when (this) {
    ApiFailure.Network -> "Unable to reach EasyMacros. Check your connection and try again."
    is ApiFailure.Http -> "Unable to delete this recipe. Refresh and try again."
    else -> "Something went wrong while deleting this recipe. Try again."
}

private fun Throwable.ingredientMessage() = when (this) {
    ApiFailure.Network -> "Unable to reach EasyMacros. Check your connection and try again."
    is ApiFailure.Http -> when (statusCode) {
        401 -> "Your session has expired. Sign in again."
        409 -> "This ingredient confirmation expired. Review the values and try again."
        else -> "Unable to create this ingredient. Check the values and try again."
    }
    else -> "Something went wrong while creating the ingredient. Try again."
}

private fun Throwable.ingredientListMessage() = when (this) {
    ApiFailure.Network -> "Unable to reach EasyMacros. Check your connection and try again."
    is ApiFailure.Http -> if (statusCode == 401) "Your session has expired. Sign in again." else "Unable to load ingredients right now. Try again."
    else -> "Something went wrong while loading ingredients. Try again."
}