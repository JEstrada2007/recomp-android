package com.recomp.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.recomp.app.data.*
import com.recomp.app.ui.*
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

private val NavLabels = listOf("Inicio", "Comidas", "Entrenar", "Progreso", "Ajustes")
private val Routine = listOf(
    "Sentadillas a una silla" to "2 × 8–12",
    "Flexiones en pared o mesa" to "2 × 8–12",
    "Remo con mochila" to "2 × 10–12",
    "Puente de glúteos" to "2 × 12–15",
    "Press de hombros" to "2 × 8–12",
    "Plancha apoyada" to "2 × 15–30 s"
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dao = RecompDatabase.get(applicationContext).dao()
        val store = ProfileStore(applicationContext)
        setContent {
            RecompTheme {
                val weights by dao.observeWeights().collectAsState(initial = emptyList())
                val meals by dao.observeMeals().collectAsState(initial = emptyList())
                val workouts by dao.observeWorkouts().collectAsState(initial = emptyList())
                val profile by store.profile.collectAsState(initial = UserProfile())
                val scope = rememberCoroutineScope()
                var tab by remember { mutableStateOf(0) }
                val today = LocalDate.now().toString()
                val todayMeals = remember(meals, today) { meals.filter { it.date == today } }
                val todaysCalories = todayMeals.sumOf { it.calories }
                val todaysProtein = todayMeals.sumOf { it.proteinGrams }
                val currentWeight = weights.firstOrNull()?.weightKg ?: profile.initialWeightKg
                val latestWaist = weights.firstOrNull { it.waistCm != null }?.waistCm
                var weightDialog by remember { mutableStateOf(false) }
                var mealDialog by remember { mutableStateOf(false) }
                var workoutDialog by remember { mutableStateOf(false) }

                var exportSnapshot by remember { mutableStateOf("") }
                val exporter = rememberLauncherForActivityResult(
                    ActivityResultContracts.CreateDocument("application/json")
                ) { uri ->
                    if (uri != null) {
                        runCatching {
                            contentResolver.openOutputStream(uri)?.use { output ->
                                output.write(exportSnapshot.toByteArray(Charsets.UTF_8))
                            } ?: error("No se pudo abrir el archivo")
                        }
                    }
                }

                Scaffold(
                    containerColor = Background,
                    bottomBar = {
                        if (profile.isConfigured) NavigationBar(containerColor = Color(0xFF1C142E)) {
                            NavLabels.forEachIndexed { index, label ->
                                NavigationBarItem(
                                    selected = tab == index,
                                    onClick = { tab = index },
                                    icon = {
                                        Icon(
                                            imageVector = when (index) {
                                                0 -> Icons.Default.Home
                                                1 -> Icons.Default.Restaurant
                                                2 -> Icons.Default.FitnessCenter
                                                3 -> Icons.Default.ShowChart
                                                else -> Icons.Default.Settings
                                            },
                                            contentDescription = label
                                        )
                                    },
                                    label = { Text(label, fontSize = 9.sp, maxLines = 1) },
                                    alwaysShowLabel = true
                                )
                            }
                        }
                    }
                ) { padding ->
                    Column(
                        Modifier.fillMaxSize().padding(padding)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 18.dp, vertical = 18.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("recomp.", color = Purple, fontWeight = FontWeight.Black, fontSize = 26.sp)
                                Text("Tu progreso, a tu ritmo", color = TextMuted, fontSize = 12.sp)
                            }
                            Surface(color = Secondary, shape = RoundedCornerShape(16.dp)) {
                                Text("v0.1", modifier = Modifier.padding(10.dp), color = Color.White, fontSize = 12.sp)
                            }
                        }
                        if (!profile.isConfigured) {
                            SettingsScreen(
                                profile,
                                onSave = { updated -> scope.launch { store.update(updated) } },
                                onExport = {},
                                isFirstLaunch = true
                            )
                        } else when (tab) {
                            0 -> HomeScreen(
                                profile, currentWeight, latestWaist,
                                todaysCalories, todaysProtein, workouts,
                                onWeight = { weightDialog = true },
                                onMeal = { mealDialog = true },
                                onWorkout = { workoutDialog = true }
                            )
                            1 -> FoodScreen(todayMeals, profile.kcalTarget, todaysCalories, todaysProtein,
                                onAdd = { mealDialog = true },
                                onDelete = { item -> scope.launch { dao.deleteMeal(item) } })
                            2 -> TrainingScreen(workouts, today,
                                onAdd = { workoutDialog = true },
                                onDelete = { item -> scope.launch { dao.deleteWorkout(item) } })
                            3 -> ProgressScreen(weights, profile.initialWeightKg, profile.goalWeightKg,
                                onAdd = { weightDialog = true },
                                onDelete = { item -> scope.launch { dao.deleteWeight(item) } })
                            else -> SettingsScreen(profile,
                                onSave = { updated -> scope.launch { store.update(updated) } },
                                onExport = {
                                    exportSnapshot = makeJson(profile, weights, meals, workouts)
                                    exporter.launch("recomp-respaldo-$today.json")
                                })
                        }
                        Spacer(Modifier.height(10.dp))
                    }
                }
                if (weightDialog) WeightDialog(onDismiss = { weightDialog = false }) { kg, waist ->
                    scope.launch { dao.insertWeight(WeightEntry(date = today, weightKg = kg, waistCm = waist)) }
                    weightDialog = false
                }
                if (mealDialog) MealDialog(onDismiss = { mealDialog = false }) { name, kcal, protein ->
                    scope.launch { dao.insertMeal(MealEntry(date = today, name = name, calories = kcal, proteinGrams = protein)) }
                    mealDialog = false
                }
                if (workoutDialog) WorkoutDialog(onDismiss = { workoutDialog = false }) { title, duration, notes ->
                    scope.launch { dao.insertWorkout(WorkoutEntry(date = today, name = title, minutes = duration, notes = notes)) }
                    workoutDialog = false
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String, subtitle: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(title, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        if (subtitle != null) Text(subtitle, fontSize = 12.sp, color = TextMuted)
    }
}

@Composable
private fun Panel(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(modifier, shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Surface)) {
        Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(11.dp), content = content)
    }
}

@Composable
private fun Metric(label: String, value: String, subtitle: String = "", modifier: Modifier = Modifier) {
    Panel(modifier) {
        Text(label, color = TextMuted, fontSize = 12.sp)
        Text(value, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        if (subtitle.isNotEmpty()) Text(subtitle, color = TextMuted, fontSize = 11.sp)
    }
}

@Composable
private fun HomeScreen(
    profile: UserProfile,
    currentWeight: Double,
    waist: Double?,
    calories: Int,
    protein: Double,
    workouts: List<WorkoutEntry>,
    onWeight: () -> Unit,
    onMeal: () -> Unit,
    onWorkout: () -> Unit
) {
    SectionTitle("Tu resumen", "Una meta sostenible, un día a la vez")
    Panel {
        Text("PESO ACTUAL", fontSize = 12.sp, color = TextMuted)
        Text("${oneDecimal(currentWeight)} kg", fontSize = 40.sp, fontWeight = FontWeight.ExtraBold)
        val delta = profile.initialWeightKg - currentWeight
        Text(
            "Cambio desde el inicio: ${oneDecimal(currentWeight - profile.initialWeightKg)} kg  •  Meta ${oneDecimal(profile.goalWeightKg)} kg",
            color = TextMuted, fontSize = 12.sp
        )
        val total = abs(profile.goalWeightKg - profile.initialWeightKg)
        val fraction = if (total > 0) (abs(currentWeight - profile.initialWeightKg) / total).toFloat().coerceIn(0f, 1f) else 0f
        LinearProgressIndicator(
            progress = { fraction }, modifier = Modifier.fillMaxWidth().height(9.dp),
            color = Purple, trackColor = Secondary,
            strokeCap = StrokeCap.Round
        )
        if (waist != null) Text("Última cintura registrada: ${oneDecimal(waist)} cm", color = TextMuted, fontSize = 12.sp)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Metric("CALORÍAS HOY", "$calories", "Meta: ${profile.kcalTarget?.toString() ?: "sin configurar"}", Modifier.weight(1f))
        Metric("PROTEÍNA HOY", "${protein.toInt()} g", "Seguimiento diario", Modifier.weight(1f))
    }
    Panel {
        Text("Esta semana", fontWeight = FontWeight.SemiBold)
        val monday = LocalDate.now().minusDays((LocalDate.now().dayOfWeek.value - 1).toLong()).toString()
        Text("${workouts.count { it.date >= monday }} entrenamientos registrados", fontSize = 16.sp)
        Text("Empieza con 2 días de fuerza; aumenta a 3 si te recuperas bien.", fontSize = 12.sp, color = TextMuted)
    }
    Text("Acciones rápidas", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
    Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        Button(onClick = onWeight, modifier = Modifier.weight(1f), contentPadding = PaddingValues(8.dp)) {
            Icon(Icons.Default.Add, null, Modifier.size(17.dp)); Text("Peso", fontSize = 12.sp)
        }
        OutlinedButton(onClick = onMeal, modifier = Modifier.weight(1f), contentPadding = PaddingValues(8.dp)) {
            Text("+ Comida", fontSize = 12.sp)
        }
        OutlinedButton(onClick = onWorkout, modifier = Modifier.weight(1f), contentPadding = PaddingValues(8.dp)) {
            Text("+ Entreno", fontSize = 12.sp)
        }
    }
    Text("Las calorías son estimaciones orientativas; la app no reemplaza una valoración de salud.", fontSize = 11.sp, color = TextMuted)
}

@Composable
private fun FoodScreen(
    meals: List<MealEntry>, target: Int?, total: Int, protein: Double,
    onAdd: () -> Unit, onDelete: (MealEntry) -> Unit
) {
    SectionTitle("Alimentación", "Registra lo que comes, sin alimentos prohibidos")
    Panel {
        Text("HOY", fontSize = 12.sp, color = TextMuted)
        Text("$total kcal", fontSize = 34.sp, fontWeight = FontWeight.Bold)
        if (target != null) {
            LinearProgressIndicator(
                progress = { (total.toFloat() / target).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(9.dp),
                color = Purple, trackColor = Secondary, strokeCap = StrokeCap.Round
            )
            Text("Objetivo orientativo: $target kcal", color = TextMuted, fontSize = 12.sp)
        } else Text("Selecciona la fórmula metabólica en Ajustes para calcular una meta orientativa.", color = TextMuted, fontSize = 12.sp)
        Text("Proteína: ${oneDecimal(protein)} g", fontWeight = FontWeight.SemiBold)
        Text("La necesidad de proteína cambia según tu peso, actividad y objetivos. No hay una meta universal.", color = TextMuted, fontSize = 11.sp)
    }
    Button(onClick = onAdd, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Default.Add, null); Spacer(Modifier.width(7.dp)); Text("Registrar comida")
    }
    Text("Comidas de hoy", fontWeight = FontWeight.Bold, fontSize = 18.sp)
    if (meals.isEmpty()) {
        Panel { Text("Aún no hay comidas registradas. Puedes empezar con tu próxima comida.", color = TextMuted) }
    } else meals.forEach { meal ->
        Panel {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(meal.name, fontWeight = FontWeight.SemiBold)
                    Text("${meal.calories} kcal  ·  ${oneDecimal(meal.proteinGrams)} g proteína", fontSize = 12.sp, color = TextMuted)
                }
                IconButton(onClick = { onDelete(meal) }) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = "Eliminar ${meal.name}", tint = TextMuted)
                }
            }
        }
    }
    Text("Las calorías deben introducirse a partir de etiquetas o estimaciones de porciones; no se calculan automáticamente.", color = TextMuted, fontSize = 11.sp)
}

@Composable
private fun TrainingScreen(
    workouts: List<WorkoutEntry>, today: String,
    onAdd: () -> Unit, onDelete: (WorkoutEntry) -> Unit
) {
    SectionTitle("Entrenamiento", "Mejora poco a poco; la constancia importa más que la intensidad")
    Panel {
        Text("Ejemplo de rutina para principiantes", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text("Semanas 1–2: 2 sesiones de fuerza. Desde la semana 3: hasta 3 sesiones si te recuperas bien.", color = TextMuted, fontSize = 12.sp)
        Routine.forEach { (exercise, reps) ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(exercise, Modifier.weight(1f), fontSize = 13.sp)
                Text(reps, fontSize = 12.sp, color = Purple, fontWeight = FontWeight.Medium)
            }
            HorizontalDivider(color = Secondary)
        }
        Text("Calienta 5 min. Descansa 60–120 s entre series. Detén el ejercicio ante dolor o mareo.", color = TextMuted, fontSize = 11.sp)
    }
    Panel {
        Text("Actividad ligera", fontWeight = FontWeight.SemiBold)
        Text("Camina 15–20 minutos al inicio. Aumenta progresivamente hacia 150 min/semana según tolerancia.", fontSize = 12.sp, color = TextMuted)
    }
    Button(onClick = onAdd, Modifier.fillMaxWidth()) {
        Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text("Registrar entrenamiento")
    }
    Text("Historial de entrenamientos", fontWeight = FontWeight.Bold, fontSize = 18.sp)
    if (workouts.isEmpty()) Panel { Text("Aún no registras entrenamientos.", color = TextMuted) }
    else workouts.take(25).forEach { item ->
        Panel {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(item.name, fontWeight = FontWeight.Medium)
                    Text("${displayDate(item.date)} · ${item.minutes} min", fontSize = 12.sp, color = TextMuted)
                    if (item.notes.isNotBlank()) Text(item.notes, fontSize = 12.sp, color = TextMuted)
                }
                if (item.date == today) Icon(Icons.Default.CheckCircle, null, tint = Success, modifier = Modifier.size(18.dp))
                IconButton(onClick = { onDelete(item) }) { Icon(Icons.Default.DeleteOutline, "Eliminar", tint = TextMuted) }
            }
        }
    }
}

@Composable
private fun ProgressScreen(
    weights: List<WeightEntry>, initialWeight: Double, goal: Double,
    onAdd: () -> Unit, onDelete: (WeightEntry) -> Unit
) {
    SectionTitle("Progreso", "Mide tendencias, no días aislados")
    Panel {
        Text("Historial de peso", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        if (weights.size >= 2) WeightChart(weights.take(20).reversed())
        else Text("Registra al menos dos mediciones para ver la gráfica.", color = TextMuted, fontSize = 12.sp)
        val current = weights.firstOrNull()?.weightKg ?: initialWeight
        Text("Inicio: ${oneDecimal(initialWeight)} kg  ·  Actual: ${oneDecimal(current)} kg  ·  Meta: ${oneDecimal(goal)} kg", color = TextMuted, fontSize = 12.sp)
    }
    Button(onClick = onAdd, Modifier.fillMaxWidth()) {
        Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text("Registrar peso y cintura")
    }
    Text("Mediciones", fontWeight = FontWeight.Bold, fontSize = 18.sp)
    if (weights.isEmpty()) Panel { Text("Todavía no has añadido ninguna medición.", color = TextMuted) }
    else weights.take(35).forEach { item ->
        Panel {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("${oneDecimal(item.weightKg)} kg", fontWeight = FontWeight.Bold)
                    Text("${displayDate(item.date)}${item.waistCm?.let { " · cintura ${oneDecimal(it)} cm" } ?: ""}", fontSize = 12.sp, color = TextMuted)
                }
                IconButton(onClick = { onDelete(item) }) { Icon(Icons.Default.DeleteOutline, "Eliminar", tint = TextMuted) }
            }
        }
    }
    Text("Para un seguimiento útil: pésate en condiciones parecidas varias mañanas por semana y compara promedios.", color = TextMuted, fontSize = 11.sp)
}

@Composable
private fun WeightChart(entries: List<WeightEntry>) {
    val values = entries.map { it.weightKg }
    val min = (values.minOrNull() ?: 0.0) - 0.7
    val max = (values.maxOrNull() ?: 1.0) + 0.7
    val range = (max - min).coerceAtLeast(1.0)
    Canvas(Modifier.fillMaxWidth().height(165.dp).background(Secondary, RoundedCornerShape(12.dp)).padding(14.dp)) {
        val w = size.width
        val h = size.height
        val positions = values.mapIndexed { index, value ->
            Offset(w * index / (values.size - 1).toFloat(), ((max - value) / range * h).toFloat())
        }
        for (i in 0 until positions.lastIndex) {
            drawLine(Purple, positions[i], positions[i + 1], strokeWidth = 4.dp.toPx(), cap = StrokeCap.Round)
        }
        positions.forEach { drawCircle(Success, radius = 3.5.dp.toPx(), center = it) }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(displayDate(entries.first().date), color = TextMuted, fontSize = 11.sp)
        Text(displayDate(entries.last().date), color = TextMuted, fontSize = 11.sp)
    }
}

@Composable
private fun SettingsScreen(
    profile: UserProfile,
    onSave: (UserProfile) -> Unit,
    onExport: () -> Unit,
    isFirstLaunch: Boolean = false
) {
    var age by remember(profile) { mutableStateOf(profile.age.takeIf { it > 0 }?.toString() ?: "") }
    var height by remember(profile) { mutableStateOf(profile.heightCm.takeIf { it > 0 }?.toString() ?: "") }
    var startWeight by remember(profile) { mutableStateOf(profile.initialWeightKg.takeIf { it > 0 }?.toString() ?: "") }
    var goalWeight by remember(profile) { mutableStateOf(profile.goalWeightKg.takeIf { it > 0 }?.toString() ?: "") }
    var sex by remember(profile) { mutableStateOf(profile.formula) }
    var activity by remember(profile) { mutableStateOf(profile.activity) }
    var objective by remember(profile) { mutableStateOf(profile.objective) }
    val updated = profile.copy(
        age = age.toIntOrNull() ?: profile.age,
        heightCm = height.toIntOrNull() ?: profile.heightCm,
        initialWeightKg = startWeight.parseDecimal() ?: profile.initialWeightKg,
        goalWeightKg = goalWeight.parseDecimal() ?: profile.goalWeightKg,
        formula = sex,
        activity = activity,
        objective = objective
    )
    SectionTitle(
        if (isFirstLaunch) "Crea tu perfil" else "Ajustes",
        if (isFirstLaunch) "Personaliza Recomp antes de empezar. No usamos datos de ejemplo." else "Configura los datos de tu plan"
    )
    Panel {
        Text("Perfil y objetivo", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        NumberInput("Edad (años)", age, { age = it }, false)
        NumberInput("Estatura (cm)", height, { height = it }, false)
        NumberInput("Peso inicial (kg)", startWeight, { startWeight = it })
        NumberInput("Meta de peso inicial (kg)", goalWeight, { goalWeight = it })
        Text("Fórmula metabólica para estimar las calorías", fontSize = 13.sp, fontWeight = FontWeight.Medium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = sex == "masculina", onClick = { sex = "masculina" }, label = { Text("Masculina", fontSize = 12.sp) })
            FilterChip(selected = sex == "femenina", onClick = { sex = "femenina" }, label = { Text("Femenina", fontSize = 12.sp) })
        }
        Text("Selecciona la variante de la ecuación de gasto energético; no describe tu identidad de género.", color = TextMuted, fontSize = 11.sp)
        Text("Nivel de actividad habitual", fontSize = 13.sp, fontWeight = FontWeight.Medium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = activity == "sedentaria", onClick = { activity = "sedentaria" }, label = { Text("Sedentaria") })
            FilterChip(selected = activity == "ligera", onClick = { activity = "ligera" }, label = { Text("Ligera") })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = activity == "moderada", onClick = { activity = "moderada" }, label = { Text("Moderada") })
            FilterChip(selected = activity == "intensa", onClick = { activity = "intensa" }, label = { Text("Intensa") })
        }
        Text("Objetivo personal", fontSize = 13.sp, fontWeight = FontWeight.Medium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = objective == "perder_grasa", onClick = { objective = "perder_grasa" }, label = { Text("Perder grasa") })
            FilterChip(selected = objective == "recomposicion", onClick = { objective = "recomposicion" }, label = { Text("Recomposición") })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = objective == "mantener", onClick = { objective = "mantener" }, label = { Text("Mantener") })
            FilterChip(selected = objective == "ganar_musculo", onClick = { objective = "ganar_musculo" }, label = { Text("Ganar músculo") })
        }
        val parsedAge = age.toIntOrNull()
        val parsedHeight = height.toIntOrNull()
        val valid = parsedAge != null && parsedAge in 18..100 &&
            parsedHeight != null && parsedHeight in 100..240 &&
            (startWeight.parseDecimal() ?: 0.0) in 30.0..400.0 &&
            (goalWeight.parseDecimal() ?: 0.0) in 30.0..400.0 &&
            sex in listOf("masculina", "femenina") && activity.isNotBlank() && objective.isNotBlank()
        Button(onClick = { onSave(updated) }, enabled = valid, modifier = Modifier.fillMaxWidth()) {
            Text(if (isFirstLaunch) "Comenzar" else "Guardar ajustes")
        }
    }
    Panel {
        Text("Gasto energético estimado", fontWeight = FontWeight.Bold)
        val target = updated.kcalTarget
        if (target == null) Text("Escoge una fórmula para ver un cálculo aproximado.", color = TextMuted)
        else {
            Text("$target kcal/día", color = Purple, fontWeight = FontWeight.Bold, fontSize = 30.sp)
            Text("Estimación según actividad y objetivo elegido; no es una prescripción. No se ajusta automáticamente.", fontSize = 12.sp, color = TextMuted)
        }
    }
    if (!isFirstLaunch) Panel {
        Text("Privacidad y datos", fontWeight = FontWeight.Bold)
        Text("Los registros se guardan únicamente en este teléfono. La app no solicita internet ni crea una cuenta.", color = TextMuted, fontSize = 12.sp)
        OutlinedButton(onClick = onExport, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.FileDownload, null); Spacer(Modifier.width(8.dp)); Text("Exportar respaldo JSON")
        }
        Text("El archivo exportado contiene datos personales de salud: consérvalo en un lugar privado. La importación estará disponible en una futura versión.", fontSize = 11.sp, color = TextMuted)
    }
}

@Composable
private fun NumberInput(label: String, value: String, onChange: (String) -> Unit, decimal: Boolean = true) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) },
        singleLine = true, modifier = Modifier.fillMaxWidth(),
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
            keyboardType = if (decimal) androidx.compose.ui.text.input.KeyboardType.Decimal
                else androidx.compose.ui.text.input.KeyboardType.Number
        )
    )
}

@Composable
private fun WeightDialog(onDismiss: () -> Unit, onSave: (Double, Double?) -> Unit) {
    var kg by remember { mutableStateOf("") }
    var waist by remember { mutableStateOf("") }
    val weight = kg.parseDecimal()
    val waistNum = waist.parseDecimal()
    val valid = weight != null && weight in 30.0..400.0 && (waist.isBlank() || (waistNum != null && waistNum in 30.0..250.0))
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Registrar medición") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Fecha: ${displayDate(LocalDate.now().toString())}", color = TextMuted, fontSize = 12.sp)
                NumberInput("Peso (kg)", kg, { kg = it })
                NumberInput("Cintura (cm) · opcional", waist, { waist = it })
            }
        },
        confirmButton = { TextButton(enabled = valid, onClick = { onSave(weight!!, waistNum) }) { Text("Guardar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
private fun MealDialog(onDismiss: () -> Unit, onSave: (String, Int, Double) -> Unit) {
    var name by remember { mutableStateOf("") }
    var kcal by remember { mutableStateOf("") }
    var protein by remember { mutableStateOf("") }
    val c = kcal.toIntOrNull()
    val p = protein.parseDecimal()
    val valid = name.isNotBlank() && c != null && c in 0..5000 && p != null && p in 0.0..300.0
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Registrar comida") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it },
                    label = { Text("Nombre o descripción") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                NumberInput("Calorías (kcal)", kcal, { kcal = it }, false)
                NumberInput("Proteína (g)", protein, { protein = it })
                Text("Introduce estimaciones a partir de etiquetas o porciones.", color = TextMuted, fontSize = 11.sp)
            }
        },
        confirmButton = { TextButton(enabled = valid, onClick = { onSave(name.trim(), c!!, p!!) }) { Text("Guardar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
private fun WorkoutDialog(onDismiss: () -> Unit, onSave: (String, Int, String) -> Unit) {
    var title by remember { mutableStateOf("Fuerza cuerpo completo") }
    var minutes by remember { mutableStateOf("35") }
    var notes by remember { mutableStateOf("") }
    val mins = minutes.toIntOrNull()
    val valid = title.isNotBlank() && mins != null && mins in 1..360
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Registrar entrenamiento") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                OutlinedTextField(value = title, onValueChange = { title = it },
                    label = { Text("Tipo de entrenamiento") }, modifier = Modifier.fillMaxWidth())
                NumberInput("Duración (minutos)", minutes, { minutes = it }, false)
                OutlinedTextField(value = notes, onValueChange = { notes = it },
                    label = { Text("Notas · opcional") }, modifier = Modifier.fillMaxWidth(), maxLines = 3)
            }
        },
        confirmButton = { TextButton(enabled = valid, onClick = { onSave(title.trim(), mins!!, notes.trim()) }) { Text("Guardar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

private fun oneDecimal(number: Double): String = String.format(Locale.US, "%.1f", number)
private fun String.parseDecimal(): Double? = replace(',', '.').toDoubleOrNull()
private fun displayDate(raw: String): String = runCatching {
    LocalDate.parse(raw).format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
}.getOrDefault(raw)

private fun makeJson(
    p: UserProfile,
    weights: List<WeightEntry>,
    meals: List<MealEntry>,
    workouts: List<WorkoutEntry>
): String = JSONObject().apply {
    put("format", "recomp-backup-v1")
    put("exported_at", LocalDate.now().toString())
    put("profile", JSONObject().apply {
        put("age", p.age); put("height_cm", p.heightCm)
        put("starting_weight_kg", p.initialWeightKg); put("goal_weight_kg", p.goalWeightKg)
        put("formula", p.formula); put("activity", p.activity); put("objective", p.objective); put("deficit_kcal", p.deficitKcal)
    })
    put("weights", JSONArray().also { array -> weights.forEach { w ->
        array.put(JSONObject().apply {
            put("date", w.date); put("weight_kg", w.weightKg)
            put("waist_cm", w.waistCm ?: JSONObject.NULL)
        })
    } })
    put("meals", JSONArray().also { array -> meals.forEach { m ->
        array.put(JSONObject().apply {
            put("date", m.date); put("name", m.name)
            put("calories", m.calories); put("protein_g", m.proteinGrams)
        })
    } })
    put("workouts", JSONArray().also { array -> workouts.forEach { w ->
        array.put(JSONObject().apply {
            put("date", w.date); put("name", w.name)
            put("minutes", w.minutes); put("notes", w.notes)
        })
    } })
}.toString(2)
