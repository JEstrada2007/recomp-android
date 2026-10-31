package com.recomp.app.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.math.roundToInt

private val Context.profileStore by preferencesDataStore("perfil")

data class UserProfile(
    val age: Int = 0,
    val heightCm: Int = 0,
    val initialWeightKg: Double = 0.0,
    val goalWeightKg: Double = 0.0,
    val formula: String = "",
    val activity: String = "",
    val objective: String = "",
    val deficitKcal: Int = 350
) {
    val isConfigured: Boolean
        get() = age in 18..100 && heightCm in 100..240 &&
            initialWeightKg in 30.0..400.0 && goalWeightKg in 30.0..400.0 &&
            formula in listOf("masculina", "femenina") &&
            activity in listOf("sedentaria", "ligera", "moderada", "intensa") &&
            objective in listOf("perder_grasa", "recomposicion", "mantener", "ganar_musculo")

    val kcalTarget: Int?
        get() {
            if (!isConfigured) return null
            val offset = if (formula == "masculina") 5 else -161
            val factor = when (activity) {
                "sedentaria" -> 1.2
                "ligera" -> 1.375
                "moderada" -> 1.55
                else -> 1.725
            }
            val change = when (objective) {
                "perder_grasa" -> -deficitKcal
                "recomposicion" -> -200
                "mantener" -> 0
                else -> 150
            }
            val bmr = 10 * initialWeightKg + 6.25 * heightCm - 5 * age + offset
            // Aproximación educativa para adultos, no prescripción clínica.
            return (((bmr * factor + change) / 50).roundToInt() * 50).coerceAtLeast(1500)
        }
}

class ProfileStore(private val context: Context) {
    private object Keys {
        val AGE = intPreferencesKey("age")
        val HEIGHT = intPreferencesKey("height")
        val START = doublePreferencesKey("initial_weight")
        val GOAL = doublePreferencesKey("goal_weight")
        val FORMULA = stringPreferencesKey("formula")
        val ACTIVITY = stringPreferencesKey("activity")
        val OBJECTIVE = stringPreferencesKey("objective")
        val DEFICIT = intPreferencesKey("deficit")
    }
    val profile: Flow<UserProfile> = context.profileStore.data.map { p ->
        UserProfile(
            age = p[Keys.AGE] ?: 0,
            heightCm = p[Keys.HEIGHT] ?: 0,
            initialWeightKg = p[Keys.START] ?: 0.0,
            goalWeightKg = p[Keys.GOAL] ?: 0.0,
            formula = p[Keys.FORMULA] ?: "",
            activity = p[Keys.ACTIVITY] ?: "",
            objective = p[Keys.OBJECTIVE] ?: "",
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
            p[Keys.ACTIVITY] = profile.activity
            p[Keys.OBJECTIVE] = profile.objective
            p[Keys.DEFICIT] = profile.deficitKcal
        }
    }
}
