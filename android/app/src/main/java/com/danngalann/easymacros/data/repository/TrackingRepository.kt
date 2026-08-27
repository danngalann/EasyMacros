package com.danngalann.easymacros.data.repository

import com.danngalann.easymacros.data.api.GoalRequest
import com.danngalann.easymacros.data.api.RationDetailResponse
import com.danngalann.easymacros.data.api.RationRequest
import com.danngalann.easymacros.data.api.RationPreviewRequest
import com.danngalann.easymacros.data.api.TimezoneRequest
import com.danngalann.easymacros.data.api.TrackingApi
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import retrofit2.HttpException

data class RationSummary(
    val id: String,
    val recipeName: String,
    val consumedGrams: String,
    val consumedAt: String,
    val consumedLocalDate: String,
    val note: String?,
)

data class RationDetail(
    val id: String,
    val recipeName: String,
    val recipeYieldGrams: String,
    val consumedGrams: String,
    val consumedAt: String,
    val consumedLocalDate: String,
    val timezone: String,
    val note: String?,
    val nutrients: List<NutrientAmount>,
    val calories: String,
)

data class RationPage(val rations: List<RationSummary>, val nextCursor: String?)
data class RationPreview(
    val recipeName: String,
    val consumedGrams: String,
    val nutrients: List<NutrientAmount>,
    val calories: String,
)

interface TrackingRepository {
    suspend fun rations(date: String? = null, from: String? = null, to: String? = null, query: String? = null, cursor: String? = null): Result<RationPage>
    suspend fun ration(rationId: String): Result<RationDetail>
    suspend fun previewRation(recipeId: String, grams: String?): Result<RationPreview>
    suspend fun logRation(recipeId: String, grams: String?, consumedAt: String, note: String): Result<Unit>
    suspend fun deleteRation(rationId: String): Result<Unit>
    suspend fun setGoal(effectiveDate: String, nutrients: List<NutrientAmount>): Result<Goal>
    suspend fun updateTimezone(timezone: String): Result<String>
}

@Singleton
class DefaultTrackingRepository @Inject constructor(
    private val api: TrackingApi,
) : TrackingRepository {
    override suspend fun rations(date: String?, from: String?, to: String?, query: String?, cursor: String?) = request {
        api.rations(date, from, to, query, cursor).let { response ->
            RationPage(response.rations.map { RationSummary(it.id, it.recipeName, it.consumedGrams, it.consumedAt, it.consumedLocalDate, it.note) }, response.nextCursor)
        }
    }

    override suspend fun ration(rationId: String) = request { api.ration(rationId).toDomain() }
    override suspend fun previewRation(recipeId: String, grams: String?) = request {
        api.previewRation(RationPreviewRequest(recipeId, grams)).let { response ->
            RationPreview(response.recipeName, response.consumedGrams, response.nutrients.map { NutrientAmount(it.code, it.amount) }, response.calories)
        }
    }
    override suspend fun logRation(recipeId: String, grams: String?, consumedAt: String, note: String) = request {
        api.createRation(RationRequest(recipeId, grams, consumedAt, note.takeIf { it.isNotBlank() }))
        Unit
    }
    override suspend fun deleteRation(rationId: String) = request { api.deleteRation(rationId); Unit }
    override suspend fun setGoal(effectiveDate: String, nutrients: List<NutrientAmount>) = request {
        api.setGoal(GoalRequest(effectiveDate, nutrients.map { com.danngalann.easymacros.data.api.NutrientAmountResponse(it.code, it.amount) })).let { Goal(it.effectiveLocalDate, it.nutrients.map { nutrient -> NutrientAmount(nutrient.code, nutrient.amount) }) }
    }
    override suspend fun updateTimezone(timezone: String) = request { api.updateTimezone(TimezoneRequest(timezone)).timezone }

    private suspend fun <T> request(call: suspend () -> T): Result<T> = try {
        Result.success(call())
    } catch (error: Exception) {
        Result.failure(when (error) {
            is HttpException -> ApiFailure.Http(error.code())
            is IOException -> ApiFailure.Network
            else -> ApiFailure.Unexpected
        })
    }
}

private fun RationDetailResponse.toDomain() = RationDetail(
    id, recipeName, recipeYieldGrams, consumedGrams, consumedAt, consumedLocalDate, timezone, note,
    nutrients.map { NutrientAmount(it.code, it.amount) }, calories,
)