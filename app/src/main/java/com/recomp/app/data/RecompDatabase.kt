package com.recomp.app.data

import android.content.Context
import androidx.room.*
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

@Dao
interface RecompDao {
    @Query("SELECT * FROM pesos ORDER BY date DESC, id DESC")
    fun observeWeights(): Flow<List<WeightEntry>>

    @Query("SELECT * FROM comidas ORDER BY date DESC, id DESC")
    fun observeMeals(): Flow<List<MealEntry>>

    @Query("SELECT * FROM entrenamientos ORDER BY date DESC, id DESC")
    fun observeWorkouts(): Flow<List<WorkoutEntry>>

    @Insert suspend fun insertWeight(item: WeightEntry)
    @Insert suspend fun insertMeal(item: MealEntry)
    @Insert suspend fun insertWorkout(item: WorkoutEntry)
    @Delete suspend fun deleteMeal(item: MealEntry)
    @Delete suspend fun deleteWorkout(item: WorkoutEntry)
    @Delete suspend fun deleteWeight(item: WeightEntry)
}

@Database(
    entities = [WeightEntry::class, MealEntry::class, WorkoutEntry::class],
    version = 1,
    exportSchema = true
)
abstract class RecompDatabase : RoomDatabase() {
    abstract fun dao(): RecompDao

    companion object {
        @Volatile private var instance: RecompDatabase? = null
        fun get(context: Context): RecompDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                RecompDatabase::class.java,
                "recomp.db"
            ).build().also { instance = it }
        }
    }
}
