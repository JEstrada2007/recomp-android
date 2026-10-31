package com.recomp.app.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.recomp.app.data.CardioSession
import com.recomp.app.data.RecompDao
import com.recomp.app.data.StepDay
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.util.Locale

data class StepSensorState(
    val available: Boolean,
    val permitted: Boolean,
    val requestPermission: () -> Unit
)

/**
 * Listener active only while the Activity is started. Android's cumulative counter
 * is not a historical "steps since midnight" API: first sample creates a baseline.
 * The Room checkpoint avoids counting the same steps after recomposition/reopening.
 */
@Composable
fun rememberStepSensor(dao: RecompDao): StepSensorState {
    val context = LocalContext.current
    val manager = remember(context) {
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    }
    val sensor = remember(manager) { manager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) }
    var permitted by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < 29 ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        permitted = it
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val mutex = remember { Mutex() }

    DisposableEffect(dao, sensor, permitted, lifecycleOwner) {
        if (sensor == null || !permitted) {
            onDispose { }
        } else {
            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    val value = event.values.firstOrNull()?.toLong() ?: return
                    val date = LocalDate.now().toString()
                    val elapsed = SystemClock.elapsedRealtime()
                    scope.launch {
                        mutex.withLock { dao.recordSensorReading(date, value, elapsed) }
                    }
                }
                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
            }
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_START ->
                        manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
                    Lifecycle.Event.ON_STOP -> manager.unregisterListener(listener)
                    else -> Unit
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
            }
            onDispose {
                manager.unregisterListener(listener)
                lifecycleOwner.lifecycle.removeObserver(observer)
            }
        }
    }
    return StepSensorState(sensor != null, permitted) {
        if (Build.VERSION.SDK_INT >= 29) {
            request.launch(Manifest.permission.ACTIVITY_RECOGNITION)
        } else {
            permitted = true
        }
    }
}

@Composable
fun ActivityTrackingScreen(
    todaySteps: StepDay?,
    sessions: List<CardioSession>,
    sensorState: StepSensorState,
    activeMode: String,
    startedAt: Long,
    onStart: (String) -> Unit,
    onCancel: () -> Unit,
    onSave: (String, Long, Double?, String) -> Unit,
    onDelete: (CardioSession) -> Unit,
    onSetManualSteps: (Int) -> Unit
) {
    val auto = todaySteps?.sensorSteps ?: 0
    val manual = todaySteps?.manualSteps ?: 0
    var stepsDialog by remember { mutableStateOf(false) }
    var sessionDialog by remember { mutableStateOf(false) }
    var finishDialog by remember { mutableStateOf(false) }
    var nowElapsed by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(startedAt) {
        while (startedAt > 0L) {
            nowElapsed = SystemClock.elapsedRealtime()
            delay(1000)
        }
    }

    Text("Actividad y pasos", style = MaterialTheme.typography.headlineSmall)
    Card(shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("PASOS DE HOY", style = MaterialTheme.typography.labelLarge)
            Text("%,d".format(Locale.getDefault(), auto + manual), style = MaterialTheme.typography.headlineLarge)
            Text("Sensor: $auto  ·  Manuales: $manual", style = MaterialTheme.typography.bodySmall)
            Text("Los registros manuales se suman a los detectados; evita registrar dos veces los mismos pasos.", style = MaterialTheme.typography.bodySmall)
            if (!sensorState.available) {
                Text("Este dispositivo no tiene un contador de pasos compatible. Puedes registrarlos manualmente.", style = MaterialTheme.typography.bodySmall)
            } else if (!sensorState.permitted) {
                Button(onClick = sensorState.requestPermission) { Text("Permitir conteo de pasos") }
            } else {
                Text("Conteo automático activo mientras usas la app. El primer dato establece el punto de partida; no recupera pasos anteriores.", style = MaterialTheme.typography.bodySmall)
            }
            OutlinedButton(onClick = { stepsDialog = true }) { Text("Editar pasos manuales") }
        }
    }

    Text("Caminata y running", style = MaterialTheme.typography.titleLarge)
    Card(shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (startedAt == 0L) {
                Text("Inicia una sesión o registra una actividad pasada.")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onStart("caminar") }, modifier = Modifier.weight(1f)) { Text("Caminar") }
                    Button(onClick = { onStart("correr") }, modifier = Modifier.weight(1f)) { Text("Running") }
                }
                OutlinedButton(onClick = { sessionDialog = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Registrar sesión manualmente")
                }
            } else {
                val seconds = ((nowElapsed - startedAt) / 1000L).coerceAtLeast(0L)
                Text(if (activeMode == "correr") "Running en curso" else "Caminata en curso", style = MaterialTheme.typography.titleMedium)
                Text("%02d:%02d:%02d".format(Locale.US, seconds / 3600, (seconds / 60) % 60, seconds % 60), style = MaterialTheme.typography.headlineMedium)
                Text("El cronómetro funciona con la app abierta; no registra GPS ni rutas.", style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { finishDialog = true }, modifier = Modifier.weight(1f)) { Text("Finalizar") }
                    OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("Descartar") }
                }
            }
        }
    }

    val weekStart = LocalDate.now().minusDays(LocalDate.now().dayOfWeek.value.toLong() - 1)
    val weekSessions = sessions.filter { runCatching { !LocalDate.parse(it.date).isBefore(weekStart) }.getOrDefault(false) }
    Text("Esta semana: ${weekSessions.size} sesiones · ${weekSessions.sumOf { it.durationSeconds } / 60} min", style = MaterialTheme.typography.bodyMedium)
    Text("Historial de caminatas y running", style = MaterialTheme.typography.titleMedium)
    if (sessions.isEmpty()) {
        Text("Aún no hay sesiones registradas.", style = MaterialTheme.typography.bodySmall)
    }
    sessions.take(25).forEach { item ->
        Card(shape = RoundedCornerShape(14.dp)) {
            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(if (item.activity == "correr") "Running" else "Caminata", style = MaterialTheme.typography.titleMedium)
                    val distanceText = item.distanceKm?.let { " · %.2f km".format(Locale.getDefault(), it) } ?: ""
                    Text("${item.date} · ${item.durationSeconds / 60} min$distanceText", style = MaterialTheme.typography.bodySmall)
                    if (item.notes.isNotBlank()) Text(item.notes, style = MaterialTheme.typography.bodySmall)
                }
                TextButton(onClick = { onDelete(item) }) { Text("Eliminar") }
            }
        }
    }

    if (stepsDialog) {
        ManualStepsDialog(initial = manual, onDismiss = { stepsDialog = false }) { count ->
            onSetManualSteps(count)
            stepsDialog = false
        }
    }
    if (sessionDialog) {
        CardioEntryDialog(
            title = "Registrar actividad",
            initialMode = "caminar",
            fixedDurationSeconds = null,
            onDismiss = { sessionDialog = false }
        ) { mode, seconds, km, notes ->
            onSave(mode, seconds, km, notes)
            sessionDialog = false
        }
    }
    if (finishDialog) {
        val seconds = ((SystemClock.elapsedRealtime() - startedAt) / 1000L).coerceAtLeast(1L)
        CardioEntryDialog(
            title = "Guardar sesión",
            initialMode = activeMode,
            fixedDurationSeconds = seconds,
            onDismiss = { finishDialog = false }
        ) { mode, duration, km, notes ->
            onSave(mode, duration, km, notes)
            finishDialog = false
        }
    }
}

@Composable
private fun ManualStepsDialog(initial: Int, onDismiss: () -> Unit, onSave: (Int) -> Unit) {
    var text by remember(initial) { mutableStateOf(initial.toString()) }
    val value = text.toIntOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pasos manuales del día") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = text, onValueChange = { text = it.filter(Char::isDigit).take(6) },
                    label = { Text("Total manual (se puede corregir)") },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                    )
                )
                Text("No modifica los pasos detectados por el sensor.", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(enabled = value != null && value in 0..200000, onClick = { onSave(value!!) }) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
private fun CardioEntryDialog(
    title: String,
    initialMode: String,
    fixedDurationSeconds: Long?,
    onDismiss: () -> Unit,
    onSave: (String, Long, Double?, String) -> Unit
) {
    var mode by remember { mutableStateOf(initialMode) }
    var minutes by remember { mutableStateOf("") }
    var distance by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    val km = if (distance.isBlank()) null else distance.replace(',', '.').toDoubleOrNull()
    val seconds = fixedDurationSeconds ?: ((minutes.toIntOrNull() ?: 0) * 60L)
    val valid = seconds in 1L..86400L && (distance.isBlank() || (km != null && km > 0.0 && km <= 200.0))
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = mode == "caminar", onClick = { mode = "caminar" }, label = { Text("Caminata") })
                    FilterChip(selected = mode == "correr", onClick = { mode = "correr" }, label = { Text("Running") })
                }
                if (fixedDurationSeconds == null) {
                    OutlinedTextField(
                        value = minutes,
                        onValueChange = { minutes = it.filter(Char::isDigit).take(4) },
                        label = { Text("Duración (min)") },
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                        )
                    )
                } else {
                    Text("Duración registrada: ${seconds / 60} min ${seconds % 60} s")
                }
                OutlinedTextField(
                    value = distance,
                    onValueChange = { distance = it.take(12) },
                    label = { Text("Distancia (km) · opcional") },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal
                    )
                )
                OutlinedTextField(
                    value = notes, onValueChange = { notes = it.take(150) },
                    label = { Text("Notas · opcional") }, maxLines = 2
                )
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = { onSave(mode, seconds, km, notes.trim()) }) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}
