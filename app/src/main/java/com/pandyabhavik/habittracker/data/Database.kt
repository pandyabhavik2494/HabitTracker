package com.pandyabhavik.habittracker.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Upsert
import com.pandyabhavik.habittracker.core.Day
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * One day of the four habits. Health Connect fills steps and weight; the app fills the rest.
 * The calorie columns are left over from the MyFitnessPal sync and are no longer read or written.
 * [kitchenClosedAt] now holds when meals were marked logged (kept under its old name so no migration is needed).
 */
@Entity(tableName = "days")
data class DayEntity(
    @PrimaryKey val date: String,
    val steps: Int = 0,
    val calories: Int = 0,
    val meals: Int = 0,
    val breakfast: Int = 0,
    val lunch: Int = 0,
    val dinner: Int = 0,
    val snacks: Int = 0,
    val kitchenClosedAt: Long? = null,
    val weightKg: Double? = null,
    val weighedAt: Long? = null,
    val weightManual: Boolean = false,
    val bathAt: Long? = null,
    val perfectAt: Long? = null,
    val savedWithSwaps: Boolean = false,
    val pledgedAt: Long? = null,
    /** 24 comma-separated hourly step counts. */
    val hourly: String = "",
) {
    fun toDay() = Day(
        date = LocalDate.parse(date),
        steps = steps,
        // Days from before the meals check-in that ended perfect still count their meals quarter.
        mealsLoggedAt = kitchenClosedAt ?: perfectAt,
        weightKg = weightKg,
        weighedAt = weighedAt,
        weightManual = weightManual,
        bathAt = bathAt,
        perfectAt = perfectAt,
        savedWithSwaps = savedWithSwaps,
        pledgedAt = pledgedAt,
        hourly = if (hourly.isBlank()) emptyList() else hourly.split(',').map { it.toIntOrNull() ?: 0 },
    )
}

/** A collected cat. [count] above 1 means duplicates, and each duplicate earned a swap. */
@Entity(tableName = "stickers")
data class StickerEntity(
    @PrimaryKey val id: String,
    val count: Int,
    val firstAt: Long,
    val lastAt: Long,
)

/**
 * A sticker pack. Daily packs roll a random cat when ripped; secret and comeback packs hold a fixed cat.
 */
@Entity(tableName = "packs")
data class PackEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    val createdAt: Long,
    /** daily, milestone, secret or comeback. */
    val reason: String,
    val fixedCat: String? = null,
    val minRarity: String? = null,
    val openedCat: String? = null,
    val openedAt: Long? = null,
    val wasNew: Boolean = false,
)

@Dao
interface HabitDao {
    @Query("SELECT * FROM days ORDER BY date")
    fun daysFlow(): Flow<List<DayEntity>>

    @Query("SELECT * FROM days ORDER BY date")
    suspend fun days(): List<DayEntity>

    @Query("SELECT * FROM days WHERE date = :date")
    suspend fun day(date: String): DayEntity?

    @Upsert
    suspend fun upsertDay(day: DayEntity)

    @Upsert
    suspend fun upsertDays(days: List<DayEntity>)

    @Query("SELECT * FROM stickers")
    fun stickersFlow(): Flow<List<StickerEntity>>

    @Query("SELECT * FROM stickers")
    suspend fun stickers(): List<StickerEntity>

    @Query("SELECT * FROM stickers WHERE id = :id")
    suspend fun sticker(id: String): StickerEntity?

    @Upsert
    suspend fun upsertSticker(sticker: StickerEntity)

    @Query("SELECT * FROM packs ORDER BY id")
    fun packsFlow(): Flow<List<PackEntity>>

    @Query("SELECT * FROM packs ORDER BY id")
    suspend fun packs(): List<PackEntity>

    @Query("SELECT * FROM packs WHERE id = :id")
    suspend fun pack(id: Long): PackEntity?

    @Query("SELECT * FROM packs WHERE date = :date AND reason = :reason")
    suspend fun packsFor(date: String, reason: String): List<PackEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPack(pack: PackEntity): Long

    @Upsert
    suspend fun upsertPack(pack: PackEntity)

    @Query("DELETE FROM packs WHERE date = :date AND openedAt IS NULL")
    suspend fun deleteUnopenedPacks(date: String)

    /** Only the packs a Perfect Day itself earns; secret and comeback packs stay once earned. */
    @Query("DELETE FROM packs WHERE date = :date AND openedAt IS NULL AND reason IN ('daily', 'milestone')")
    suspend fun deleteUnopenedDayPacks(date: String)

    @Query("DELETE FROM days")
    suspend fun clearDays()

    @Query("DELETE FROM stickers")
    suspend fun clearStickers()

    @Query("DELETE FROM packs")
    suspend fun clearPacks()

    @Transaction
    suspend fun clearAll() {
        clearDays()
        clearStickers()
        clearPacks()
    }
}

@Database(entities = [DayEntity::class, StickerEntity::class, PackEntity::class], version = 1, exportSchema = false)
abstract class HabitDatabase : RoomDatabase() {
    abstract fun dao(): HabitDao

    companion object {
        fun create(context: Context, name: String = "habits.db"): HabitDatabase =
            Room.databaseBuilder(context, HabitDatabase::class.java, name).build()
    }
}

fun Day.toEntity() = DayEntity(
    date = date.toString(),
    steps = steps,
    kitchenClosedAt = mealsLoggedAt,
    weightKg = weightKg,
    weighedAt = weighedAt,
    weightManual = weightManual,
    bathAt = bathAt,
    perfectAt = perfectAt,
    savedWithSwaps = savedWithSwaps,
    pledgedAt = pledgedAt,
    hourly = hourly.joinToString(","),
)
