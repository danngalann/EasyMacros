package com.danngalann.easymacros.data.repository

import com.danngalann.easymacros.data.api.DashboardApi
import com.danngalann.easymacros.data.api.GoalResponse
import com.danngalann.easymacros.data.api.NutrientAmountResponse
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import retrofit2.HttpException

data class NutrientAmount(val code: String, val amount: String)

data class Goal(
    val effectiveLocalDate: String,
    val nutrients: List<NutrientAmount>,
)

data class DailyDashboard(
    val date: String,
    val nutrients: List<NutrientAmount>,
    val calories: String,
    val goal: Goal?,
)

interface DashboardRepository {
    suspend fun dashboard(date: String): Result<DailyDashboard>
}

@Singleton
class DefaultDashboardRepository @Inject constructor(
    private val api: DashboardApi,
) : DashboardRepository {
    override suspend fun dashboard(date: String): Result<DailyDashboard> = try {
        val response = api.dashboard(date)
        Result.success(
            DailyDashboard(
                date = response.date,
                nutrients = response.nutrients.toDomain(),
                calories = response.calories,
                goal = response.goal?.toDomain(),
            ),
        )
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

sealed class ApiFailure : Exception() {
    data class Http(val statusCode: Int) : ApiFailure()
    data object Network : ApiFailure()
    data object Unexpected : ApiFailure()
}

private fun List<NutrientAmountResponse>.toDomain() = map { NutrientAmount(it.code, it.amount) }

private fun GoalResponse.toDomain() = Goal(effectiveLocalDate, nutrients.toDomain())