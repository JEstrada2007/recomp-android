package com.recomp.app.data

import android.content.Context
import androidx.room.*
import androidx.sqlite.execSQL
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "pesos")
data class WeightEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    val weightKg: Double,
    val waistCm: Double? = null
)

@Entity(tableName = "comidas")
data class MealEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    val name: String,
    val calories: Int,
    val proteinGrams: Double
)

@Entity(tableName = "entrenamientos")
data class WorkoutEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    val name: String,
    val minutes: Int,
    val notes: String = ""
)

/**
 * Los pasos medidos y los introducidos manualmente se guardan por separado:
 * registrar una caminata no suma pasos por segunda vez.
 */
@Entity(tableName = "step_days")
data class StepDay(
    @PrimaryKey val date: String,
    val sensorSteps: Int = 0,
    val manualSteps: Int = 0
)

@Entity(tableName = "step_checkpoint")
data class StepCheckpoint(
    @PrimaryKey val id: Int = 1,
    val date: String,
    val rawCount: Long,
    val elapsedRealtimeMs: Long
)

@Entity(tableName = "cardio_sessions")
data class CardioSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    val activity: String, // "caminar" o "correr"
    val durationSeconds: Long,
    val distanceKm: Double? = null,
    val notes: String = ""
)

@Dao
interface RecompDao {
    @Query("SELECT * FROM pesos ORDER BY date DESC, id DESC")
    fun observeWeights(): Flow<List<WeightEntry>>

    @Query("SELECT * FROM comidas ORDER BY date DESC, id DESC")
    fun observeMeals(): Flow<List<MealEntry>>

    @Query("SELECT * FROM entrenamientos ORDER BY date DESC, id DESC")
    fun observeWorkouts(): Flow<List<WorkoutEntry>>

    @Query("SELECT * FROM step_days ORDER BY date DESC")
    fun observeStepDays(): Flow<List<StepDay>>

    @Query("SELECT * FROM cardio_sessions ORDER BY date DESC, id DESC")
    fun observeCardioSessions(): Flow<List<CardioSession>>

    @Query("SELECT * FROM step_days WHERE date = :date LIMIT 1")
    suspend fun getStepDay(date: String): StepDay?

    @Query("SELECT * FROM step_checkpoint WHERE id = 1 LIMIT 1")
    suspend fun getStepCheckpoint(): StepCheckpoint?

    @Upsert suspend fun upsertStepDay(day: StepDay)
    @Upsert suspend fun upsertStepCheckpoint(checkpoint: StepCheckpoint)
    @Insert suspend fun insertCardioSession(session: CardioSession)
    @Delete suspend fun deleteCardioSession(session: CardioSession)

    @Transaction
    suspend fun setManualSteps(date: String, amount: Int) {
        require(amount in 0..200000)
        val old = getStepDay(date) ?: StepDay(date)
        upsertStepDay(old.copy(manualSteps = amount))
    }

    /**
     * TYPE_STEP_COUNTER reports a cumulative device value (since reboot).
     * Only attribute positive differences to the same day and same boot.
     * The first measurement establishes a baseline, not a day's total.
     */
    @Transaction
    suspend fun recordSensorReading(date: String, raw: Long, elapsedRealtimeMs: Long) {
        if (raw < 0) return
        val previous = getStepCheckpoint()
        val valid = previous != null &&
            previous.date == date &&
            elapsedRealtimeMs >= previous.elapsedRealtimeMs &&
            raw >= previous.rawCount
        val delta = if (valid) (raw - previous!!.rawCount).coerceIn(0L, 100000L).toInt() else 0
        if (delta > 0) {
            val old = getStepDay(date) ?: StepDay(date)
            upsertStepDay(old.copy(sensorSteps = (old.sensorSteps.toLong() + delta).coerceAtMost(200000L).toInt()))
        }
        upsertStepCheckpoint(StepCheckpoint(date = date, rawCount = raw, elapsedRealtimeMs = elapsedRealtimeMs))
    }

    @Insert suspend fun insertWeight(item: WeightEntry)
    @Insert suspend fun insertMeal(item: MealEntry)
    @Insert suspend fun insertWorkout(item: WorkoutEntry)
    @Delete suspend fun deleteMeal(item: MealEntry)
    @Delete suspend fun deleteWorkout(item: WorkoutEntry)
    @Delete suspend fun deleteWeight(item: WeightEntry)
}

@Database(
    entities = [WeightEntry::class, MealEntry::class, WorkoutEntry::class,
        StepDay::class, StepCheckpoint::class, CardioSession::class],
    version = 2,
    exportSchema = true
)
abstract class RecompDatabase : RoomDatabase() {
    abstract fun dao(): RecompDao

    companion object {
        private val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(connection: androidx.sqlite.SQLiteConnection) {
                connection.execSQL("CREATE TABLE IF NOT EXISTS `step_days` (`date` TEXT NOT NULL, `sensorSteps` INTEGER NOT NULL, `manualSteps` INTEGER NOT NULL, PRIMARY KEY(`date`))")
                connection.execSQL("CREATE TABLE IF NOT EXISTS `step_checkpoint` (`id` INTEGER NOT NULL, `date` TEXT NOT NULL, `rawCount` INTEGER NOT NULL, `elapsedRealtimeMs` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                connection.execSQL("CREATE TABLE IF NOT EXISTS `cardio_sessions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `date` TEXT NOT NULL, `activity` TEXT NOT NULL, `durationSeconds` INTEGER NOT NULL, `distanceKm` REAL, `notes` TEXT NOT NULL)")
            }
        }

        @Volatile private var instance: RecompDatabase? = null
        fun get(context: Context): RecompDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                RecompDatabase::class.java,
                "recomp.db"
            ).addMigrations(MIGRATION_1_2).build().also { instance = it }
        }
    }
}
