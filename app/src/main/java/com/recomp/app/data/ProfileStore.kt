package com.recomp.app.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.math.roundToInt

private val Context.profileStore by preferencesDataStore("perfil")

data class UserProfile(
    val age: Int = 25,
    val heightCm: Int = 170,
    val initialWeightKg: Double = 80.0,
    val goalWeightKg: Double = 75.0,
    val formula: String = "", // "masculina" / "femenina"; por confirmar
    val deficitKcal: Int = 350
) {
    val kcalTarget: Int?
        get() {
            val offset = when (formula) {
                "masculina" -> 5
                "femenina" -> -161
                else -> return null
            }
            val bmr = 10 * initialWeightKg + 6.25 * heightCm - 5 * age + offset
            // Estimación sedentaria, no sustituto de evaluación clínica.
            return (((bmr * 1.2 - deficitKcal) / 50).roundToInt() * 50).coerceAtLeast(1500)
        }
}

class ProfileStore(private val context: Context) {
    private object Keys {
        val AGE = intPreferencesKey("age")
        val HEIGHT = intPreferencesKey("height")
        val START = doublePreferencesKey("initial_weight")
        val GOAL = doublePreferencesKey("goal_weight")
        val FORMULA = stringPreferencesKey("formula")
        val DEFICIT = intPreferencesKey("deficit")
    }
    val profile: Flow<UserProfile> = context.profileStore.data.map { p ->
        UserProfile(
            age = p[Keys.AGE] ?: 25,
            heightCm = p[Keys.HEIGHT] ?: 170,
            initialWeightKg = p[Keys.START] ?: 80.0,
            goalWeightKg = p[Keys.GOAL] ?: 75.0,
            formula = p[Keys.FORMULA] ?: "",
            deficitKcal = p[Keys.DEFICIT] ?: 350
        )
    }
    suspend fun update(profile: UserProfile) {
        context.profileStore.edit { p ->
            p[Keys.AGE] = profile.age
            p[Keys.HEIGHT] = profile.heightCm
            p[Keys.START] = profile.initialWeightKg
            p[Keys.GOAL] = profile.goalWeightKg
            p[Keys.FORMULA] = profile.formula
            p[Keys.DEFICIT] = profile.deficitKcal
        }
    }
}
