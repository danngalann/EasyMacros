package com.danngalann.easymacros.data.repository

import com.danngalann.easymacros.data.api.BarcodeCandidateRequest
import com.danngalann.easymacros.data.api.ConfirmIngredientCandidateRequest
import com.danngalann.easymacros.data.api.IngredientApi
import com.danngalann.easymacros.data.api.IngredientDetailResponse
import com.danngalann.easymacros.data.api.IngredientVisibilityRequest
import com.danngalann.easymacros.data.api.ManualIngredientRequest
import com.danngalann.easymacros.data.api.NutrientAmountResponse
import com.danngalann.easymacros.data.api.UpdateIngredientRequest
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.HttpException

data class IngredientNutrient(
    val code: String,
    val displayName: String,
    val amount: String,
    val unit: String,
    val derived: Boolean = false,
)

data class IngredientCandidate(
    val id: String,
    val version: String,
    val status: String,
    val source: String,
    val name: String,
    val brand: String,
    val barcode: String,
    val nutrients: List<IngredientNutrient>,
    val confidence: String?,
    val warnings: List<String>,
    val provider: String?,
    val errorCode: String?,
)

data class IngredientDetailModel(
    val id: String,
    val name: String,
    val brand: String,
    val barcode: String,
    val hidden: Boolean,
    val source: String,
    val confidence: String?,
    val warnings: List<String>,
    val nutrients: List<NutrientAmount>,
)

data class CandidateReview(
    val version: String,
    val name: String,
    val brand: String,
    val barcode: String,
    val nutrients: List<IngredientNutrient>,
)

data class IngredientImage(val bytes: ByteArray, val mime: String)

interface IngredientRepository {
    suspend fun ingredients(query: String, hidden: Boolean): Result<List<IngredientSummary>>
    suspend fun ingredient(id: String): Result<IngredientDetailModel>
    suspend fun update(id: String, name: String, brand: String): Result<IngredientDetailModel>
    suspend fun setHidden(id: String, hidden: Boolean): Result<IngredientDetailModel>
    suspend fun manual(draft: IngredientDraft): Result<IngredientCandidate>
    suspend fun barcode(barcode: String): Result<IngredientCandidate>
    suspend fun ai(images: List<IngredientImage>, description: String, mode: String): Result<IngredientCandidate>
    suspend fun candidate(id: String): Result<IngredientCandidate>
    suspend fun retry(id: String): Result<IngredientCandidate>
    suspend fun cancel(id: String): Result<Unit>
    suspend fun confirm(id: String, review: CandidateReview): Result<IngredientDetailModel>
}

@Singleton
class DefaultIngredientRepository @Inject constructor(
    private val api: IngredientApi,
) : IngredientRepository {
    override suspend fun ingredients(query: String, hidden: Boolean) = request {
        api.ingredients(query, hidden).ingredients.map { IngredientSummary(it.id, it.name, it.hidden) }
    }

    override suspend fun ingredient(id: String) = request { api.ingredient(id).toDomain() }

    override suspend fun update(id: String, name: String, brand: String) = request {
        api.updateIngredient(id, UpdateIngredientRequest(name.trim(), brand.trim().ifBlank { null })).toDomain()
    }

    override suspend fun setHidden(id: String, hidden: Boolean) = request {
        api.setIngredientVisibility(id, IngredientVisibilityRequest(hidden)).toDomain()
    }

    override suspend fun manual(draft: IngredientDraft) = request {
        api.createManualCandidate(
            ManualIngredientRequest(
                draft.name.trim(),
                draft.brand.trim().ifBlank { null },
                listOf(
                    NutrientAmountResponse("protein", draft.protein),
                    NutrientAmountResponse("carbohydrates", draft.carbohydrates),
                    NutrientAmountResponse("fat", draft.fat),
                ),
            ),
        ).toDomain()
    }

    override suspend fun barcode(barcode: String) = request {
        api.createBarcodeCandidate(BarcodeCandidateRequest(barcode)).toDomain()
    }

    override suspend fun ai(images: List<IngredientImage>, description: String, mode: String) = request {
        api.createAiCandidate(
            images.mapIndexed { index, image ->
                MultipartBody.Part.createFormData(
                    "images",
                    "ingredient-upload-${index + 1}",
                    image.bytes.toRequestBody(image.mime.toMediaType()),
                )
            },
            description.takeIf(String::isNotBlank)?.toRequestBody("text/plain".toMediaType()),
            mode.toRequestBody("text/plain".toMediaType()),
            UUID.randomUUID().toString().toRequestBody("text/plain".toMediaType()),
        ).toDomain()
    }

    override suspend fun candidate(id: String) = request { api.candidate(id).toDomain() }
    override suspend fun retry(id: String) = request { api.retryCandidate(id).toDomain() }
    override suspend fun cancel(id: String) = request { api.cancelCandidate(id) }

    override suspend fun confirm(id: String, review: CandidateReview) = request {
        api.confirmCandidate(
            id,
            ConfirmIngredientCandidateRequest(
                review.version,
                review.name.trim(),
                review.brand.trim().ifBlank { null },
                review.barcode.trim().ifBlank { null },
                review.nutrients.map { NutrientAmountResponse(it.code, it.amount) },
            ),
        ).toDomain()
    }

    private suspend fun <T> request(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        Result.failure(
            when (error) {
                is HttpException -> ApiFailure.Http(error.code())
                is IOException -> ApiFailure.Network
                else -> ApiFailure.Unexpected
            },
        )
    }
}

private fun com.danngalann.easymacros.data.api.IngredientCandidateResponse.toDomain() =
    IngredientCandidate(
        id,
        version,
        status,
        source,
        suggestedName,
        brand.orEmpty(),
        barcode.orEmpty(),
        nutrients.map {
            IngredientNutrient(
                it.code,
                it.displayName ?: it.code.replace('_', ' ').replaceFirstChar(Char::uppercase),
                it.amount,
                it.unit ?: if (it.code == "vitamin_a") "ug" else if (it.code in setOf("sodium", "vitamin_c", "calcium", "iron")) "mg" else "g",
                it.derived,
            )
        }.let { mapped ->
            val required = listOf(
                IngredientNutrient("protein", "Protein", "", "g"),
                IngredientNutrient("carbohydrates", "Carbohydrates", "", "g"),
                IngredientNutrient("fat", "Fat", "", "g"),
            )
            (mapped + required).distinctBy(IngredientNutrient::code)
        },
        confidence,
        warnings,
        provenance["provider"] as? String,
        errorCode,
    )

private fun IngredientDetailResponse.toDomain() = IngredientDetailModel(
    id,
    name,
    brand.orEmpty(),
    barcode.orEmpty(),
    hidden,
    source,
    confidence,
    warnings,
    nutrients.map { NutrientAmount(it.code, it.amount) },
)
