package com.pandyabhavik.habittracker.health

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.MealType
import androidx.health.connect.client.records.NutritionRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.AggregateGroupByDurationRequest
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import kotlin.reflect.KClass

enum class HealthStatus { AVAILABLE, NEEDS_UPDATE, NOT_INSTALLED }

/** What Health Connect holds for one day. */
data class HealthDay(
    val date: LocalDate,
    val steps: Int?,
    val hourly: List<Int>?,
    val calories: Int?,
    val meals: Int?,
    val breakfast: Int = 0,
    val lunch: Int = 0,
    val dinner: Int = 0,
    val snacks: Int = 0,
    val weightKg: Double?,
    val weighedAt: Long?,
)

/**
 * Reads the three synced habits from Health Connect: steps (Google Health / Fit and any other step
 * source, de-duplicated by Health Connect's own priority list), food from MyFitnessPal, and weight
 * from the scale's app (VeSync).
 */
class HealthGateway(private val context: Context) {
    fun status(): HealthStatus = when (HealthConnectClient.getSdkStatus(context, PROVIDER)) {
        HealthConnectClient.SDK_AVAILABLE -> HealthStatus.AVAILABLE
        HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> HealthStatus.NEEDS_UPDATE
        else -> HealthStatus.NOT_INSTALLED
    }

    private val client: HealthConnectClient by lazy { HealthConnectClient.getOrCreate(context) }

    suspend fun granted(): Set<String> =
        if (status() == HealthStatus.AVAILABLE) client.permissionController.getGrantedPermissions() else emptySet()

    fun installIntent(): Intent =
        Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$PROVIDER&url=healthconnect%3A%2F%2Fonboarding"))
            .setPackage("com.android.vending")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun settingsIntent(): Intent =
        Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    suspend fun readDay(date: LocalDate, zone: ZoneId, granted: Set<String>): HealthDay {
        val start = date.atStartOfDay(zone).toInstant()
        val end = date.plusDays(1).atStartOfDay(zone).toInstant()
        val range = TimeRangeFilter.between(start, end)

        var steps: Int? = null
        var hourly: List<Int>? = null
        if (READ_STEPS in granted) {
            val total = client.aggregate(AggregateRequest(setOf(StepsRecord.COUNT_TOTAL), range))
            steps = (total[StepsRecord.COUNT_TOTAL] ?: 0L).toInt()
            val groups = client.aggregateGroupByDuration(
                AggregateGroupByDurationRequest(setOf(StepsRecord.COUNT_TOTAL), range, Duration.ofHours(1)),
            )
            val h = IntArray(24)
            groups.forEach { g ->
                val hour = g.startTime.atZone(zone).hour
                h[hour] += (g.result[StepsRecord.COUNT_TOTAL] ?: 0L).toInt()
            }
            hourly = h.toList()
        }

        var calories: Int? = null
        var meals: Int? = null
        val perMeal = IntArray(5)
        if (READ_NUTRITION in granted) {
            val food = readAll(NutritionRecord::class, range).filter { it.metadata.dataOrigin.packageName != context.packageName }
            meals = food.size
            var kcal = 0.0
            food.forEach { r ->
                val e = r.energy?.inKilocalories ?: 0.0
                kcal += e
                val slot = when (r.mealType) {
                    MealType.MEAL_TYPE_BREAKFAST -> 0
                    MealType.MEAL_TYPE_LUNCH -> 1
                    MealType.MEAL_TYPE_DINNER -> 2
                    MealType.MEAL_TYPE_SNACK -> 3
                    else -> 4
                }
                perMeal[slot] += e.toInt()
            }
            calories = kotlin.math.round(kcal).toInt()
        }

        var weight: Double? = null
        var weighedAt: Long? = null
        if (READ_WEIGHT in granted) {
            readAll(WeightRecord::class, range).maxByOrNull { it.time }?.let {
                weight = it.weight.inKilograms
                weighedAt = it.time.toEpochMilli()
            }
        }

        return HealthDay(
            date = date,
            steps = steps,
            hourly = hourly,
            calories = calories,
            meals = meals,
            breakfast = perMeal[0],
            lunch = perMeal[1],
            dinner = perMeal[2],
            // Food without a meal type counts as a snack in the breakdown.
            snacks = perMeal[3] + perMeal[4],
            weightKg = weight,
            weighedAt = weighedAt,
        )
    }

    private suspend fun <T : Record> readAll(type: KClass<T>, range: TimeRangeFilter): List<T> {
        val out = ArrayList<T>()
        var token: String? = null
        do {
            val response = client.readRecords(ReadRecordsRequest(type, range, pageSize = 1000, pageToken = token))
            out += response.records
            token = response.pageToken
        } while (!token.isNullOrEmpty())
        return out
    }

    companion object {
        const val PROVIDER = "com.google.android.apps.healthdata"

        val READ_STEPS = HealthPermission.getReadPermission(StepsRecord::class)
        val READ_NUTRITION = HealthPermission.getReadPermission(NutritionRecord::class)
        val READ_WEIGHT = HealthPermission.getReadPermission(WeightRecord::class)
        const val READ_IN_BACKGROUND = "android.permission.health.READ_HEALTH_DATA_IN_BACKGROUND"

        val ALL = setOf(READ_STEPS, READ_NUTRITION, READ_WEIGHT, READ_IN_BACKGROUND)
        val CORE = setOf(READ_STEPS, READ_NUTRITION, READ_WEIGHT)
    }
}
