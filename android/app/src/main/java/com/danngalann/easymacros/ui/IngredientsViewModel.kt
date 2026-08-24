package com.danngalann.easymacros.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.danngalann.easymacros.data.repository.ApiFailure
import com.danngalann.easymacros.data.repository.CandidateReview
import com.danngalann.easymacros.data.repository.IngredientCandidate
import com.danngalann.easymacros.data.repository.IngredientDetailModel
import com.danngalann.easymacros.data.repository.IngredientDraft
import com.danngalann.easymacros.data.repository.IngredientNutrient
import com.danngalann.easymacros.data.repository.IngredientImage
import com.danngalann.easymacros.data.repository.IngredientRepository
import com.danngalann.easymacros.data.repository.IngredientSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class IngredientFlow { List, Method, Manual, Barcode, Image, Processing, Review, Detail }

data class CandidateEditorState(
    val id: String,
    val version: String,
    val source: String,
    val name: String,
    val brand: String,
    val barcode: String,
    val nutrients: List<IngredientNutrient>,
    val confidence: String?,
    val warnings: List<String>,
    val provider: String?,
    val status: String,
    val errorCode: String?,
) {
    fun review() = CandidateReview(version, name, brand, barcode, nutrients)
}

data class IngredientsUiState(
    val query: String = "",
    val includeHidden: Boolean = false,
    val ingredients: List<IngredientSummary> = emptyList(),

    val flow: IngredientFlow = IngredientFlow.List,
    val manual: IngredientEditorState = IngredientEditorState(),
    val barcode: String = "",
    val scannerEnabled: Boolean = false,
    val permissionDenied: Boolean = false,
    val images: List<IngredientImage> = emptyList(),
    val description: String = "",
    val uploadProgress: Int? = null,
    val candidate: CandidateEditorState? = null,
    val detail: IngredientDetailModel? = null,
    val confirmedIngredient: IngredientDetailModel? = null,
    val successMessage: String? = null,
    val isLoading: Boolean = true,
    val isSubmitting: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class IngredientsViewModel @Inject constructor(
    private val repository: IngredientRepository,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val _state = MutableStateFlow(IngredientsUiState())
    val state: StateFlow<IngredientsUiState> = _state.asStateFlow()
    private var pollingJob: Job? = null
    private var submissionJob: Job? = null
    private var lastScan: Pair<String, Long>? = null

    init {
        val candidateId = savedStateHandle.get<String>("candidateId")
        if (candidateId == null) loadIngredients() else resumeCandidate(candidateId)
    }

    fun search(query: String) {
        _state.value = _state.value.copy(query = query)
        loadIngredients()
    }

    fun setIncludeHidden(include: Boolean) {
        _state.value = _state.value.copy(includeHidden = include)
        loadIngredients()
    }

    fun retry() = when (_state.value.flow) {
        IngredientFlow.Processing -> _state.value.candidate?.id?.let(::retryCandidate)
        IngredientFlow.Review -> _state.value.candidate
            ?.takeIf { it.source == "ai" && it.status == "failed" }
            ?.id
            ?.let(::retryCandidate)
            ?: loadIngredients()
        else -> loadIngredients()
    }

    fun chooseMethod() { _state.value = _state.value.copy(flow = IngredientFlow.Method, error = null) }
    fun chooseManual() { _state.value = _state.value.copy(flow = IngredientFlow.Manual, manual = IngredientEditorState(), error = null) }
    fun chooseBarcode() { _state.value = _state.value.copy(flow = IngredientFlow.Barcode, error = null) }
    fun chooseImage() {
        _state.value = _state.value.copy(
            flow = IngredientFlow.Image,
            images = emptyList(),
            description = "",
            uploadProgress = null,
            error = null,
        )
    }

    fun backToList() {
        pollingJob?.cancel()
        _state.value.candidate?.takeIf { it.status in setOf("pending", "processing") }?.id?.let { id ->
            viewModelScope.launch { repository.cancel(id) }
        }
        savedStateHandle["candidateId"] = null
        _state.value = _state.value.copy(flow = IngredientFlow.List, candidate = null, detail = null, error = null)
    }

    fun updateManual(
        name: String? = null,
        brand: String? = null,
        protein: String? = null,
        carbohydrates: String? = null,
        fat: String? = null,
    ) {
        val current = _state.value.manual
        _state.value = _state.value.copy(
            manual = current.copy(
                name = name ?: current.name,
                brand = brand ?: current.brand,
                protein = protein ?: current.protein,
                carbohydrates = carbohydrates ?: current.carbohydrates,
                fat = fat ?: current.fat,
            ),
            error = null,
        )
    }

    fun submitManual() {
        val draft = _state.value.manual.draft()
        if (!draft.isValid()) {
            _state.value = _state.value.copy(error = "Add a name and non-negative macro values per 100 g.")
            return
        }
        submit { repository.manual(draft) }
    }

    fun updateBarcode(value: String) { _state.value = _state.value.copy(barcode = value, error = null) }
    fun setScannerEnabled(enabled: Boolean) { _state.value = _state.value.copy(scannerEnabled = enabled) }
    fun onPermissionDenied() { _state.value = _state.value.copy(permissionDenied = true, scannerEnabled = false) }

    fun onBarcodeScanned(value: String, nowMillis: Long = System.currentTimeMillis()) {
        val previous = lastScan
        if (previous?.first == value && nowMillis - previous.second < 2_000) return
        lastScan = value to nowMillis
        _state.value = _state.value.copy(barcode = value, scannerEnabled = false)
        submitBarcode()
    }

    fun submitBarcode() {
        val barcode = _state.value.barcode
        if (!BarcodeValidator.isValid(barcode)) {
            _state.value = _state.value.copy(error = "Enter a valid UPC-A or EAN-13 barcode.")
            return
        }
        submit { repository.barcode(barcode) }
    }

    fun addImage(bytes: ByteArray, mime: String) {
        if (_state.value.images.size >= 4) return
        _state.value = _state.value.copy(
            images = _state.value.images + IngredientImage(bytes, mime),
            error = null,
        )
    }

    fun removeImage(index: Int) {
        _state.value = _state.value.copy(images = _state.value.images.filterIndexed { current, _ -> current != index })
    }

    fun updateDescription(value: String) {
        _state.value = _state.value.copy(description = value.take(500), error = null)
    }

    fun submitImage() {
        val images = _state.value.images
        if (images.isEmpty()) {
            _state.value = _state.value.copy(error = "Capture or select an image first.")
            return
        }
        _state.value = _state.value.copy(uploadProgress = 0)
        submit {
            _state.value = _state.value.copy(uploadProgress = 50)
            repository.ai(images, _state.value.description, "auto")
        }
    }

    fun cancelUpload() {
        submissionJob?.cancel()
        submissionJob = null
        pollingJob?.cancel()
        _state.value.candidate?.id?.let { id -> viewModelScope.launch { repository.cancel(id) } }
        savedStateHandle["candidateId"] = null
        _state.value = _state.value.copy(flow = IngredientFlow.Image, candidate = null, isSubmitting = false, uploadProgress = null)
    }

    fun updateCandidate(
        name: String? = null,
        brand: String? = null,
        barcode: String? = null,
        nutrientCode: String? = null,
        nutrientAmount: String? = null,
    ) {
        val candidate = _state.value.candidate ?: return
        val nutrients = if (nutrientCode == null) candidate.nutrients else candidate.nutrients.map {
            if (it.code == nutrientCode) it.copy(amount = nutrientAmount.orEmpty()) else it
        }
        _state.value = _state.value.copy(
            candidate = candidate.copy(
                name = name ?: candidate.name,
                brand = brand ?: candidate.brand,
                barcode = barcode ?: candidate.barcode,
                nutrients = nutrients,
            ),
            error = null,
        )
    }

    fun confirmCandidate() {
        val candidate = _state.value.candidate ?: return
        if (candidate.name.isBlank() || !candidate.nutrients.hasRequiredValidMacros() || _state.value.isSubmitting) {
            _state.value = _state.value.copy(error = "Name and valid protein, carbohydrate, and fat values are required.")
            return
        }
        _state.value = _state.value.copy(isSubmitting = true, error = null)
        viewModelScope.launch {
            repository.confirm(candidate.id, candidate.review()).onSuccess {
                savedStateHandle["candidateId"] = null
                _state.value = _state.value.copy(
                    flow = IngredientFlow.List,
                    confirmedIngredient = it,
                    successMessage = "${it.name} was created.",
                    candidate = null,
                    isSubmitting = false,
                )
                loadIngredients(keepFlow = true)
            }.onFailure { showFailure(it) }
        }
    }

    fun consumeConfirmedIngredient() {
        _state.value = _state.value.copy(confirmedIngredient = null)
    }

    fun clearSuccessMessage() {
        _state.value = _state.value.copy(successMessage = null)
    }

    fun useImageInstead() { chooseImage() }

    fun openDetail(id: String) {
        _state.value = _state.value.copy(flow = IngredientFlow.Detail, isLoading = true, error = null)
        viewModelScope.launch {
            repository.ingredient(id).onSuccess {
                _state.value = _state.value.copy(detail = it, isLoading = false)
            }.onFailure { showFailure(it) }
        }
    }

    fun saveDetail(name: String, brand: String) {
        val detail = _state.value.detail ?: return
        if (name.isBlank()) return
        _state.value = _state.value.copy(isSubmitting = true)
        viewModelScope.launch {
            repository.update(detail.id, name, brand).onSuccess {
                _state.value = _state.value.copy(detail = it, isSubmitting = false)
                loadIngredients(keepFlow = true)
            }.onFailure { showFailure(it) }
        }
    }

    fun toggleHidden() {
        val detail = _state.value.detail ?: return
        _state.value = _state.value.copy(isSubmitting = true)
        viewModelScope.launch {
            repository.setHidden(detail.id, !detail.hidden).onSuccess {
                _state.value = _state.value.copy(detail = it, isSubmitting = false)
                loadIngredients(keepFlow = true)
            }.onFailure { showFailure(it) }
        }
    }

    private fun submit(operation: suspend () -> Result<IngredientCandidate>) {
        if (_state.value.isSubmitting) return
        _state.value = _state.value.copy(isSubmitting = true, error = null)
        submissionJob = viewModelScope.launch {
            try {
                operation().onSuccess(::acceptCandidate).onFailure(::showFailure)
            } finally {
                submissionJob = null
            }
        }
    }

    private fun acceptCandidate(candidate: IngredientCandidate) {
        val editor = candidate.toEditor()
        savedStateHandle["candidateId"] = candidate.id
        _state.value = _state.value.copy(
            flow = if (candidate.status in setOf("pending", "processing")) IngredientFlow.Processing else IngredientFlow.Review,
            candidate = editor,
            isSubmitting = false,
            uploadProgress = if (candidate.status in setOf("pending", "processing")) 100 else null,
            error = if (candidate.status == "failed") candidate.message() else null,
        )
        if (candidate.status in setOf("pending", "processing")) poll(candidate.id)
    }

    private fun resumeCandidate(id: String) {
        _state.value = _state.value.copy(flow = IngredientFlow.Processing)
        poll(id)
    }

    private fun poll(id: String) {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            while (true) {
                repository.candidate(id).onSuccess { candidate ->
                    if (candidate.status in setOf("pending", "processing")) {
                        _state.value = _state.value.copy(
                            flow = IngredientFlow.Processing,
                            candidate = candidate.toEditor(),
                            isSubmitting = false,
                            uploadProgress = 100,
                        )
                    } else {
                        acceptCandidate(candidate)
                        return@launch
                    }
                }.onFailure {
                    showFailure(it)
                    return@launch
                }
                delay(1_500)
            }
        }
    }

    private fun retryCandidate(id: String) {
        viewModelScope.launch {
            repository.retry(id).onSuccess(::acceptCandidate).onFailure(::showFailure)
        }
    }

    private fun loadIngredients(keepFlow: Boolean = false) = viewModelScope.launch {
        _state.value = _state.value.copy(isLoading = true, error = null)
        repository.ingredients(_state.value.query, _state.value.includeHidden).onSuccess {
            _state.value = _state.value.copy(
                ingredients = it,
                isLoading = false,
                flow = if (keepFlow) _state.value.flow else IngredientFlow.List,
            )
        }.onFailure(::showFailure)
    }

    private fun showFailure(error: Throwable) {
        _state.value = _state.value.copy(isLoading = false, isSubmitting = false, uploadProgress = null, error = error.userMessage())
    }
}

object BarcodeValidator {
    fun isValid(input: String): Boolean {
        val digits = input.filterNot { it == ' ' || it == '-' }
        if (digits.length !in setOf(12, 13) || digits.any { !it.isDigit() }) return false
        val normalized = if (digits.length == 12) "0$digits" else digits
        val sum = normalized.take(12).mapIndexed { index, char ->
            char.digitToInt() * if (index % 2 == 0) 1 else 3
        }.sum()
        return (10 - sum % 10) % 10 == normalized.last().digitToInt()
    }
}

private fun IngredientDraft.isValid() = name.isNotBlank() &&
    listOf(protein, carbohydrates, fat).all { Regex("\\d+(\\.\\d+)?").matches(it) }

private fun List<IngredientNutrient>.hasRequiredValidMacros() =
    listOf("protein", "carbohydrates", "fat").all { code ->
        firstOrNull { it.code == code }?.amount?.let { Regex("\\d+(\\.\\d+)?").matches(it) } == true
    }

private fun IngredientCandidate.toEditor() = CandidateEditorState(
    id, version, source, name, brand, barcode, nutrients, confidence, warnings, provider, status, errorCode,
)

private fun IngredientCandidate.message() = when (errorCode) {
    "not_found" -> "Barcode not found. Use image + AI or manual entry."
    "rate_limited" -> "Open Food Facts is throttled. Retry later or use image + AI."
    "malformed_response" -> "The provider response was malformed. Use image + AI or manual entry."
    else -> warnings.firstOrNull() ?: "Candidate creation failed. Retry or use another method."
}

private fun Throwable.userMessage() = when (this) {
    ApiFailure.Network -> "Unable to reach EasyMacros. Your form is preserved; check the connection and retry."
    is ApiFailure.Http -> when (statusCode) {
        401 -> "Your session expired. Sign in again."
        404 -> "This ingredient or candidate was not found."
        409 -> "This candidate expired or was already confirmed."
        429 -> "The provider quota is temporarily exhausted. Retry later."
        else -> "The request could not be completed. Check the values and retry."
    }
    else -> "Something went wrong. Your form is preserved; retry."
}
